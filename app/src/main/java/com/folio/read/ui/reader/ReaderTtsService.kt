package com.folio.read.ui.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
// androidx.media:media 的会话类保留 android.support.v4.media 历史包名,MediaStyle 走 androidx.media.app
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.folio.read.R
import com.folio.read.data.Book
import com.folio.read.data.BookRepository
import com.folio.read.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 朗读前台服务:朗读不依赖阅读页存活,锁屏/切后台/退出阅读页都继续。
 * 前台服务类型 mediaPlayback,Doze 下不被暂停;TTS 用服务自身 context 初始化
 * (部分 ROM 对 applicationContext 绑定有限制——见 ReaderTts 注释)。
 * 通知栏常驻:暂停/继续、停止,点通知回阅读页。
 */
class ReaderTtsService : Service() {

    companion object {
        private const val TAG = "FolioTts"
        const val ACTION_START = "com.folio.read.tts.START"
        const val ACTION_PAUSE_RESUME = "com.folio.read.tts.PAUSE_RESUME"
        const val ACTION_STOP = "com.folio.read.tts.STOP"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_CHAPTER = "chapter"
        const val EXTRA_OFFSET = "offset"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "tts_reading"

        /** 系统当前深浅(按 uiMode) */
        private fun isSystemDark(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        /** 从指定位置开始朗读;chapter/offset 传 -1 时按书内保存的进度读 */
        fun start(context: Context, bookId: Long, chapter: Int, offset: Int) {
            val intent = Intent(context, ReaderTtsService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_CHAPTER, chapter)
                .putExtra(EXTRA_OFFSET, offset)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pauseResume(context: Context) {
            context.startService(
                Intent(context, ReaderTtsService::class.java).setAction(ACTION_PAUSE_RESUME),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ReaderTtsService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: BookRepository
    /** 朗读引擎与媒体会话:首次点「朗读」时才创建(打开阅读页不创建,避免未朗读就接管系统媒体播放器) */
    private var tts: ReaderTts? = null
    private var mediaSession: MediaSessionCompat? = null
    private var ttsReady = false
    private var loading = false

    private var book: Book? = null
    private var text: String? = null
    private var chapters: List<Chapter> = emptyList()
    private var curChapter = 0
    /** 通知点开阅读页时传入的深浅主题;onCreate 按系统与用户设置同步 */
    private var notifDarkTheme = false
    /** 上次停止时的朗读位置(整本正文绝对偏移);停止后媒体栏/通知栏点播放从此处继续 */
    private var lastStopPosition: Int? = null
    /** 当前朗读章节标题(通知文案);文本加载完、朗读开始前为空 */
    private var chapterTitle = ""
    /** 文本加载完成但 TTS 尚未就绪时的暂存切片 */
    private var pendingSlices: List<ReaderTts.Slice>? = null

    // 阅读页可观察状态(服务自有,tts 未创建时为默认值;朗读状态经协程转发到这些字段)
    val active = MutableStateFlow(false)
    val highlightRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val pausedState = MutableStateFlow(false)
    /** 服务当前朗读章:跨章时阅读页 UI 跟随切章(-1 = 未朗读) */
    val readingChapter = MutableStateFlow(-1)
    /** 朗读错误(初始化失败等);阅读页弹出提示后清空 */
    val errorMsg = MutableStateFlow<String?>(null)

    /** 阅读页绑定用的 Binder;unbind 时不解绑(朗读由服务独立运行) */
    inner class TtsBinder : Binder() {
        fun service(): ReaderTtsService = this@ReaderTtsService
    }

    override fun onBind(intent: Intent?): IBinder = TtsBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        // Service 构造阶段 context 未附加,取系统深浅须在 onCreate 后
        notifDarkTheme = isSystemDark(this)
        repo = BookRepository(applicationContext)
        createChannel()
        // 通知点开阅读页要带正确深浅:跟随用户主题设置(随系统或手动)
        serviceScope.launch {
            val themeRepo = SettingsRepository(this@ReaderTtsService)
            themeRepo.followSystemTheme.collect { follow ->
                val manual = themeRepo.manualDark.first()
                notifDarkTheme = if (follow) isSystemDark(this@ReaderTtsService) else manual
            }
        }
        // 朗读引擎与媒体会话不在 onCreate 初始化:打开阅读页(绑定服务)不应接管系统媒体播放器,
        // 首次点「朗读」时由 ensureTtsAndSession() 创建
    }

    /** 首次朗读时创建朗读引擎与媒体会话;之后复用 */
    private fun ensureTtsAndSession() {
        val engine = tts
        if (engine == null) {
            val created = ReaderTts(this) { onChapterFinished() }
            tts = created
            created.init(
                onReady = {
                    Log.d(TAG, "tts init ready")
                    ttsReady = true
                    tryPlay()
                },
                onError = {
                    Log.d(TAG, "tts init error")
                    errorMsg.value = getString(R.string.tts_unavailable)
                    stopSelf()
                },
            )
            // 引擎状态转发到服务自有字段(阅读页经绑定观察)
            serviceScope.launch {
                combine(created.active, created.highlightRange, created.pausedState) { a, h, p -> Triple(a, h, p) }
                    .collect { (a, h, p) ->
                        active.value = a
                        highlightRange.value = h
                        pausedState.value = p
                    }
            }
        }
        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(this, "FolioTts").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        val e = tts ?: return
                        if (e.pausedState.value) {
                            e.togglePause()
                        } else if (!e.active.value) {
                            // 停止后点播放:从上次停止位置继续(滑页/跳章停了朗读后,媒体栏可恢复)
                            resumeFromLastStop()
                        }
                    }

                    override fun onPause() {
                        val e = tts ?: return
                        if (!e.pausedState.value && e.active.value) e.togglePause()
                    }

                    override fun onStop() = stopSelf()

                    override fun onSkipToNext() = playFrom(curChapter + 1, 0)

                    override fun onSkipToPrevious() = playFrom((curChapter - 1).coerceAtLeast(0), 0)
                })
            }
            updateMediaSession()
            // 朗读状态(播放/暂停)驱动媒体栏与通知同步
            serviceScope.launch {
                combine(active, pausedState) { a, p -> a to p }.collect {
                    updateMediaSession()
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action} book=${intent?.getLongExtra(EXTRA_BOOK_ID, -1L)}")
        when (intent?.action) {
            ACTION_START -> {
                // 首次朗读:创建朗读引擎与媒体会话(打开阅读页不创建,避免未朗读就接管媒体播放器)
                ensureTtsAndSession()
                startForegroundCompat()
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
                if (bookId > 0) {
                    startReading(bookId, intent.getIntExtra(EXTRA_CHAPTER, -1), intent.getIntExtra(EXTRA_OFFSET, 0))
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE_RESUME -> {
                val engine = tts
                if (engine != null && engine.active.value) {
                    engine.togglePause()
                } else {
                    // 停止后点按钮:从上次位置重播(与媒体栏播放一致)
                    resumeFromLastStop()
                }
                updateNotification()
            }
            ACTION_STOP -> {
                savePosition()
                // 带位置(滑页停止)时记「当前看到的页」作为下次播放起点;通知栏停止按钮不带位置,用朗读高亮位置
                val ch = intent.getIntExtra(EXTRA_CHAPTER, -1)
                val off = intent.getIntExtra(EXTRA_OFFSET, -1)
                lastStopPosition = if (ch >= 0 && off >= 0) {
                    chapters.getOrNull(ch)?.let { it.start + off }
                } else {
                    tts?.highlightRange?.value?.first
                }
                // 先立即停朗读:stopSelf 在有绑定客户端(阅读页)时不会马上销毁服务,
                // 不显式 stop 的话朗读会继续
                tts?.stop()
                // 朗读章归 -1:用户切章后组合重建时,阅读页跟随逻辑读到 -1 不会误拉回朗读章
                readingChapter.value = -1
                // 停止=不再听:释放媒体会话、移除前台通知,媒体栏/通知栏都不再显示 Folio。
                // (服务仍被阅读页绑定,stopSelf 不会立即销毁,退场必须显式做;下次朗读时重建)
                mediaSession?.release()
                mediaSession = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            // 前台服务被系统恢复时兜底:先挂起前台,避免 ANR/崩溃
            else -> startForegroundCompat()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mediaSession?.release()
        tts?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 异步加载书籍正文与章节,再从头播放指定位置 */
    private fun startReading(bookId: Long, chapter: Int, offset: Int) {
        if (loading) return
        loading = true
        serviceScope.launch {
            val loaded = repo.getBook(bookId)
            if (loaded == null) {
                Log.d(TAG, "startReading: book null")
                loading = false
                errorMsg.value = getString(R.string.tts_load_failed)
                stopSelf()
                return@launch
            }
            val fp = querySourceFingerprint(this@ReaderTtsService, loaded.filePath)
            val content = loadText(loaded, fp)
            if (content == null) {
                Log.d(TAG, "startReading: text null")
                loading = false
                errorMsg.value = getString(R.string.tts_load_failed)
                stopSelf()
                return@launch
            }
            val starts = loadChapterStarts(loaded, fp, content)
            val loadedChapters = buildChapters(content, starts)
            book = loaded
            text = content
            chapters = loadedChapters
            loading = false
            // 未指定位置时按书内保存的进度读(后台续听场景)
            val startChapter = if (chapter >= 0) chapter else loaded.currentChapterIndex
            val startOffset = if (chapter >= 0) offset else loaded.chapterPosition
            playFrom(startChapter, startOffset)
        }
    }

    /** 正文:内存缓存 → 磁盘缓存 → 整本读取+处理并回写(与阅读页同一套缓存,保证文本一致) */
    private suspend fun loadText(book: Book, fp: String?): String? {
        ReaderCache.memoryLoadText(book.id, fp)?.let { return it }
        val cached = withContext(Dispatchers.IO) {
            fp?.let { ReaderCache.loadText(this@ReaderTtsService, book.id, it) }
        }
        if (cached != null) {
            ReaderCache.memoryStoreText(book.id, fp, cached)
            return cached
        }
        val content = withContext(Dispatchers.IO) {
            runCatching { readText(this@ReaderTtsService, book.filePath) }.getOrNull()
        } ?: return null
        val processed = withContext(Dispatchers.Default) { processParagraphs(content) }
        ReaderCache.memoryStoreText(book.id, fp, processed)
        if (fp != null) withContext(Dispatchers.IO) {
            ReaderCache.saveText(this@ReaderTtsService, book.id, fp, processed)
        }
        return processed
    }

    /** 章节块首:缓存 → 整本扫描识别并回写(键含章节规则/文本处理版本) */
    private suspend fun loadChapterStarts(book: Book, fp: String?, content: String): List<Int> {
        if (fp != null) {
            ReaderCache.memoryLoadChapterStarts(book.id, fp, ChapterCacheKey)?.let { return it }
            val cached = withContext(Dispatchers.IO) {
                ReaderCache.loadChapterStarts(this@ReaderTtsService, book.id, fp, ChapterCacheKey)
            }
            if (cached != null) {
                ReaderCache.memoryStoreChapterStarts(book.id, fp, ChapterCacheKey, cached)
                return cached
            }
        }
        val starts = withContext(Dispatchers.Default) { ChapterDetector.detectChapterStarts(content) }
        if (fp != null) {
            ReaderCache.memoryStoreChapterStarts(book.id, fp, ChapterCacheKey, starts)
            withContext(Dispatchers.IO) {
                ReaderCache.saveChapterStarts(this@ReaderTtsService, book.id, fp, ChapterCacheKey, starts)
            }
        }
        return starts
    }

    /** 从某章、章内偏移开始朗读(跨页段落整段读,与阅读页朗读同一套切片) */
    private fun playFrom(chapterIdx: Int, offset: Int) {
        val content = text ?: return
        val chapter = chapters.getOrNull(chapterIdx) ?: return
        curChapter = chapterIdx
        // 通知阅读页当前朗读章:跨章时 UI 跟随切章(否则读完一章 UI 还停在旧章末页)
        readingChapter.value = chapterIdx
        // 媒体会话点击目标=正在朗读的书:流体云/媒体卡点击从这里进入阅读页
        // (不设 sessionActivity 时,ColorOS 流体云点击会落到 launcher 主页——对照 Legado 修复)
        mediaSession?.setSessionActivity(openReaderIntent())
        val start = (chapter.start + offset).coerceIn(chapter.start, chapter.end)
        val slices = buildList {
            // 按句切分(而非自然段):朗读单元/高亮粒度=一句,暂停恢复重读也只是一句
            val segs = splitSentences(content.substring(start, chapter.end))
            var rel = 0
            segs.forEach { seg ->
                add(ReaderTts.Slice(seg, start + rel, start + rel + seg.length))
                rel += seg.length
            }
        }
        Log.d(TAG, "playFrom chapter=$chapterIdx offset=$offset slices=${slices.size}")
        pendingSlices = slices
        chapterTitle = chapter.title
        updateNotification()
        // 切章时同步刷新媒体会话元数据:updateMediaSession 平时由播放/暂停状态变化驱动,
        // 切章状态不变不会触发,不主动刷的话媒体栏/流体云标题停留在旧章节
        updateMediaSession()
        tryPlay()
    }

    /** 停止后恢复播放:从上次停止位置(整本正文绝对偏移)重播 */
    private fun resumeFromLastStop() {
        val abs = lastStopPosition ?: return
        val idx = chapters.indexOfLast { it.start <= abs }.coerceAtLeast(0)
        val chapter = chapters.getOrNull(idx) ?: return
        playFrom(idx, (abs - chapter.start).coerceAtLeast(0))
    }

    /**
     * 阅读页绑定后同步调用:用户翻页/跳章离开朗读页时立即停止。
     * 与 ACTION_STOP 逻辑相同但不走 intent——intent 异步有延迟,期间朗读推进的
     * 自动翻页会把页面拉回朗读页(用户翻页被吞)。
     */
    fun stopReadingAt(chapter: Int, offset: Int) {
        savePosition()
        lastStopPosition = chapters.getOrNull(chapter)?.let { it.start + offset }
        // 朗读章归 -1:用户切章后组合重建时,阅读页跟随逻辑读到 -1 不会误拉回朗读章
        readingChapter.value = -1
        tts?.stop()
        mediaSession?.release()
        mediaSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** TTS 就绪后真正开读;文本未就绪(加载中)时由加载完成侧再调 */
    private fun tryPlay() {
        val engine = tts ?: return
        if (!ttsReady) return
        val slices = pendingSlices ?: return
        pendingSlices = null
        engine.readSlices(slices)
        updateNotification()
    }

    /** 本章读完:保存进度,有下一章则继续,否则全书读完停止 */
    private fun onChapterFinished() {
        Log.d(TAG, "onChapterFinished curChapter=$curChapter total=${chapters.size}")
        savePosition()
        if (curChapter + 1 < chapters.size) {
            playFrom(curChapter + 1, 0)
        } else {
            stopSelf()
        }
    }

    /** 保存当前朗读位置(按当前高亮段换算章节与章内偏移),朗读结束/切章时调用 */
    private fun savePosition() {
        val currentBook = book ?: return
        val abs = tts?.highlightRange?.value?.first ?: return
        val idx = chapters.indexOfLast { it.start <= abs }.coerceAtLeast(0)
        val chapter = chapters[idx]
        serviceScope.launch {
            repo.updatePosition(currentBook.id, idx, (abs - chapter.start).coerceAtLeast(0))
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tts_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 同步媒体会话:播放/暂停状态、书名/章节元数据、会话激活 */
    private fun updateMediaSession() {
        val session = mediaSession ?: return
        val playing = active.value && !pausedState.value
        val state = when {
            !active.value -> PlaybackStateCompat.STATE_STOPPED
            pausedState.value -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
                        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    chapterTitle.ifBlank { getString(R.string.tts_preparing) },
                )
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book?.title ?: getString(R.string.app_name))
                .build(),
        )
        session.isActive = active.value
    }

    private fun startForegroundCompat() {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    /** 打开正在朗读的书(通知/媒体会话/流体云点击共用);book 未加载时为 -1,靠 UPDATE_CURRENT 后续刷新 */
    private fun openReaderIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, ReaderActivity::class.java)
            .putExtra(EXTRA_BOOK_ID, book?.id ?: -1L)
            // 深浅主题随通知同步,否则从媒体栏打开阅读页会落到默认浅色
            .putExtra(ReaderActivity.EXTRA_DARK_THEME, notifDarkTheme)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        // UPDATE_CURRENT:服务启动瞬间 book 未加载时 extra 是 -1,不更新的话
        // 系统复用旧 PendingIntent,点通知会拿 -1 打开阅读页(「无法打开此文件」)
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val title = book?.title ?: getString(R.string.tts_preparing)
        val content = chapterTitle.takeIf { it.isNotBlank() } ?: getString(R.string.tts_preparing)
        // 主按钮文案:朗读中=暂停/继续,停止后=播放(点了从上次位置重播)
        val pauseLabel = getString(
            when {
                !active.value -> R.string.tts_play
                pausedState.value -> R.string.tts_resume
                else -> R.string.tts_pause
            },
        )
        val openReader = openReaderIntent()
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ReaderTtsService::class.java).setAction(ACTION_PAUSE_RESUME),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ReaderTtsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openReader)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // MediaStyle:媒体栏显示 Folio 的播放控制(媒体控制中心/锁屏媒体卡片)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .addAction(0, pauseLabel, pauseIntent)
            .addAction(0, getString(R.string.tts_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
