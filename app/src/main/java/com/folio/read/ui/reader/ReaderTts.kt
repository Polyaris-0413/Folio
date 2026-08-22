package com.folio.read.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 基础版朗读:系统 TextToSpeech 逐段朗读,从 (章号, 章内偏移) 开始。
 * 文本按段落(换行)切分(照搬 Legado 的段落朗读思路),逐段喂 TTS,
 * 靠 UtteranceProgressListener 完成回调推进下一段。
 * 每段朗读时通过 [highlightRange] 暴露其在整本正文中的绝对字符范围,
 * 供阅读页做当前句高亮(渲染层加 span,不影响分页测量)。
 * 仅 Activity 存活期间朗读(基础版,退出即停;后台服务后续再做)。
 */
class ReaderTts(
    private val context: Context,
    /** 本章读完后回调(由阅读页决定切下一章继续读,或就此停止) */
    private val onChapterFinished: (() -> Unit)? = null,
) {

    private val appContext = context.applicationContext
    // 用传入的 Activity context 初始化(部分 ROM 的 TTS 引擎对 applicationContext 绑定有限制,
    // 会导致 init 失败 status=-1;Legado 在 Service 中用组件 context 初始化可正常使用)
    private var tts: TextToSpeech? = null
    private var ready = false
    /** 待读页切片(文本 + 在整本正文中的绝对起止);当前页索引;页内当前子段索引(onDone 推进);是否暂停中 */
    private var paragraphs: List<Slice> = emptyList()
    private var index = 0
    private var subIndex = 0
    private var subSlices: List<Slice> = emptyList()
    private var playing = false
    private var paused = false
    /** 因音频焦点被抢而暂停(区别于用户手动暂停);焦点恢复时自动继续 */
    private var autoPaused = false
    /** 每次 speak 的唯一序号;回调只认「当前正在播的段」的序号,忽略延迟到达的旧回调
     * (tts.stop() 会触发被停止段的 onDone/onError,若延迟到暂停恢复/新朗读之后到达,
     * 会把 subIndex 误推进=跳字/跳段) */
    private var speakSeq = 0
    private var lastSpokenSeq = -1
    /** 引擎对当前段报错后的重试次数:引擎报错往往是瞬时的,重读可恢复,
     * 避免进入「后续段全部跳过」的异常连锁(日志实锤:onError 后 9/10/11 段 40-80ms 秒完成) */
    private var errorRetries = 0
    /** 连续「秒完成跳过」检测次数;speak 时间戳与段长(用于判定引擎是否真的发声) */
    private var skipRetries = 0
    private var speakStartedAt = 0L
    private var currentTextLen = 0

    /** 引擎报错重试上限(超过则跳过该段,避免卡死) */
    private val MAX_ERROR_RETRIES = 2
    /** 段朗读时长低于此值判定「引擎秒完成=没发声跳过」(正常短句也 >300ms) */
    private val FAST_SKIP_MS = 300L
    /** 秒完成判定只对超过此长度的段生效(防短句误判) */
    private val FAST_SKIP_MIN_LEN = 20
    /** 引擎报错/跳过后的重读等待:引擎报错后有约半秒「免疫期」,期间喂任何段都被跳过,
     * 立即重读无效(日志实锤:重试后仍连跳 8 段),须等恢复窗口过去再重读 */
    private val RETRY_DELAY_MS = 800L
    /** 跳过检测重试上限(连续秒完成超过此数则放弃该段,避免死循环) */
    private val MAX_SKIP_RETRIES = 3

    /** 主线程 handler:延迟重读/回调状态操作统一回主线程 */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 语音朗读音频属性:系统按语音处理音频焦点与混音策略 */
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // 永久丢失(其他 App 长期占用):暂停,不再自动恢复
            AudioManager.AUDIOFOCUS_LOSS -> pauseForFocus(resumeAfter = false)
            // 暂时丢失(来电/闹钟等):暂停,焦点回来自动继续
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseForFocus(resumeAfter = true)
            // 可压低:保持朗读,由系统自动压低音量
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
            // 焦点回来:若因焦点暂停则恢复
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (autoPaused) {
                    autoPaused = false
                    paused = false
                    pausedState.value = false
                    speakNext()
                }
            }
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    /** 当前朗读段在整本正文中的绝对字符范围;停止后保留最后一段(供阅读页淡出),新朗读/释放时清空 */
    val highlightRange = kotlinx.coroutines.flow.MutableStateFlow<Pair<Int, Int>?>(null)

    /** 是否正在朗读(含暂停);可观察,供阅读页驱动淡出 */
    val active = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 是否暂停中;供通知按钮文案与阅读页图标 */
    val pausedState = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 初始化 TTS;成功后回调 onReady,失败(无可用引擎等)回调 onError */
    fun init(onReady: () -> Unit, onError: (() -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            // 语音朗读属性:让系统知道这是语音,音频焦点/混音策略按语音处理
            tts?.setAudioAttributes(audioAttributes)
            // 中文优先,否则系统默认
            val zh = tts?.setLanguage(Locale.CHINESE)
            if (zh == TextToSpeech.LANG_MISSING_DATA || zh == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    // 段读完,推进下一段(暂停时不推进;旧回调不认,防跳段)
                    android.util.Log.d(
                        "FolioTts",
                        "onDone id=$utteranceId last=$lastSpokenSeq sub=$subIndex paused=$paused",
                    )
                    if (!paused && utteranceId?.toIntOrNull() == lastSpokenSeq) {
                        // 秒完成检测:段还没真正朗读就回调完成=引擎跳过(不发声);
                        // 等引擎恢复窗口过去再重读当前段,防止内容被跳过(跳字)
                        val elapsed = SystemClock.uptimeMillis() - speakStartedAt
                        if (elapsed < FAST_SKIP_MS && currentTextLen > FAST_SKIP_MIN_LEN) {
                            if (skipRetries < MAX_SKIP_RETRIES) {
                                skipRetries++
                                android.util.Log.d(
                                    "FolioTts",
                                    "skipDetected sub=$subIndex ${elapsed}ms len=$currentTextLen retry=${skipRetries}/$MAX_SKIP_RETRIES",
                                )
                                mainHandler.postDelayed({ speakNext() }, RETRY_DELAY_MS)
                                return
                            }
                            android.util.Log.d("FolioTts", "skipGiveUp sub=$subIndex")
                            skipRetries = 0
                        }
                        subIndex++
                        speakNext()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    android.util.Log.d(
                        "FolioTts",
                        "onError id=$utteranceId last=$lastSpokenSeq sub=$subIndex paused=$paused",
                    )
                    if (!paused && utteranceId?.toIntOrNull() == lastSpokenSeq) {
                        // 引擎报错:等恢复窗口过去再重读当前段(立即重读无效——引擎报错后
                        // 有约半秒免疫期,期间喂任何段都被跳过,日志实锤重试后仍连跳 8 段)
                        if (errorRetries < MAX_ERROR_RETRIES) {
                            errorRetries++
                            android.util.Log.d("FolioTts", "retry sub=$subIndex (${errorRetries}/${MAX_ERROR_RETRIES})")
                            mainHandler.postDelayed({ speakNext() }, RETRY_DELAY_MS)
                        } else {
                            errorRetries = 0
                            subIndex++
                            speakNext()
                        }
                    }
                }
            })
            if (ready) onReady() else onError?.invoke()
        }
    }

    /**
     * 从指定位置开始朗读一组「页切片」(每项=一页文本 + 绝对范围)。
     * 页内按段落拆成子段逐个 speak(高亮=当前子段);整页子段读完才到下一页,
     * 读完一页自动推进翻页——跨页段落被页边界自然切分,不等待。
     */
    fun readSlices(slices: List<Slice>) {
        if (!ready) return
        requestFocus()
        paragraphs = slices
        index = 0
        subIndex = 0
        subSlices = emptyList()
        errorRetries = 0
        skipRetries = 0
        playing = true
        paused = false
        active.value = true
        speakNext()
    }

    /** 一个朗读切片:文本 + 在整本正文的绝对起止 */
    data class Slice(val text: String, val start: Int, val end: Int)

    /** 播放/暂停切换 */
    fun togglePause() {
        if (!ready || !playing) return
        if (paused) {
            // 恢复:继续读当前段
            paused = false
            pausedState.value = false
            speakNext()
        } else {
            // 暂停:停住当前段,onDone 因 paused 不推进
            paused = true
            pausedState.value = true
            tts?.stop()
        }
    }

    /** 停止朗读并复位;高亮一并清除(高亮=正在读这里,停了不该残留) */
    fun stop() {
        playing = false
        paused = false
        autoPaused = false
        pausedState.value = false
        paragraphs = emptyList()
        index = 0
        subIndex = 0
        subSlices = emptyList()
        highlightRange.value = null
        // 停止后所有旧回调失效(引擎可能延迟回调被停止的段,防误推进)
        lastSpokenSeq = -1
        active.value = false
        tts?.stop()
        abandonFocus()
    }

    /** 释放资源(Activity 销毁时调用) */
    fun shutdown() {
        playing = false
        paused = false
        autoPaused = false
        pausedState.value = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        highlightRange.value = null
        active.value = false
        abandonFocus()
    }

    /** 申请音频焦点(短暂性 + 允许压低);返回是否拿到 */
    private fun requestFocus() {
        audioManager.requestAudioFocus(focusRequest)
    }

    /** 释放音频焦点 */
    private fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /** 焦点被抢:正在朗读时暂停当前段;resumeAfter 决定焦点回来自动继续 */
    private fun pauseForFocus(resumeAfter: Boolean) {
        if (!playing || paused) return
        paused = true
        pausedState.value = true
        autoPaused = resumeAfter
        tts?.stop()
    }

    private fun speakNext() {
        if (!playing || paused) return
        // 页内子段还有剩余:读当前段(subIndex 指向「正在读的段」,onDone 才推进;
        // 暂停恢复时仍指向本段,从本段重新读,不会跳掉后半句)
        if (subIndex < subSlices.size) {
            val sub = subSlices[subIndex]
            highlightRange.value = sub.start to sub.end
            lastSpokenSeq = speakSeq
            speakStartedAt = SystemClock.uptimeMillis()
            currentTextLen = sub.text.length
            android.util.Log.d(
                "FolioTts",
                "speak id=$speakSeq sub=$subIndex text=${sub.text.take(16)}",
            )
            tts?.speak(sub.text, TextToSpeech.QUEUE_FLUSH, null, (speakSeq++).toString())
            return
        }
        // 当前页读完,进下一页
        if (index >= paragraphs.size) {
            // 本章读完:停住(保留最后一段高亮供淡出),再由回调决定续章或停止
            playing = false
            active.value = false
            onChapterFinished?.invoke()
            return
        }
        val page = paragraphs[index]
        index++
        // 页内按段落拆子段(高亮粒度);整页作为一个朗读单元推进
        subIndex = 0
        subSlices = buildList {
            var rel = 0
            val segs = page.text.split("\n")
            segs.forEachIndexed { i, seg ->
                if (seg.isNotBlank()) {
                    add(Slice(seg, page.start + rel, page.start + rel + seg.length))
                }
                rel += seg.length + if (i < segs.lastIndex) 1 else 0
            }
        }
        speakNext()
    }
}
