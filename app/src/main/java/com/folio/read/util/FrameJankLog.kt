package com.folio.read.util

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer

/**
 * 帧掉帧日志:Choreographer 逐帧计时,帧耗时超过阈值即写一行日志(tag = FolioFrame)。
 * 仅 debug 构建生效,用于暴力测试滑动/翻页流畅度:`adb logcat -s FolioFrame`。
 * 由 FolioApp 通过 Activity 生命周期自动启停(前台运行,后台停止)。
 */
object FrameJankLog {

    private const val TAG = "FolioFrame"
    private const val THRESHOLD_MS = 20L // 超过 1.2 帧(16.6ms/帧)即判定掉帧,捕捉轻微卡顿

    private var running = false
    private var lastFrameNanos = 0L

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = System.nanoTime()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val elapsedMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L
            if (elapsedMs > THRESHOLD_MS) {
                Log.w(TAG, "jank ${elapsedMs}ms @ uptime=${SystemClock.uptimeMillis()}")
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
