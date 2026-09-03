package com.folio.read

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.folio.read.data.AppDatabase
import com.folio.read.ui.components.CoverCache
import com.folio.read.ui.components.prewarmBookCovers
import com.folio.read.util.FrameJankLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用级入口:debug 构建下按前台/后台启停帧掉帧日志(供暴力测试流畅度) */
class FolioApp : Application() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        prewarmBookCovers()
        // buildConfig 未开启,用可调试标志位判断 debug 构建
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (isDebuggable && startedActivities == 1) FrameJankLog.start()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (isDebuggable && startedActivities == 0) FrameJankLog.stop()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * 封面预热:冷启动时书架组合之前,后台先把首屏书的封面位图画进缓存——
     * CoverArtwork 组合时缓存命中同步取位图,书名首帧直接显示(组合后才异步渲染
     * 曾致冷启动书名延迟闪现,用户反馈)。只预热排序最前的 12 本(首屏可见量+缓冲),
     * 渲染尺寸与 CoverArtwork 同为规范值(COVER_RENDER_WIDTH_DP×密度),key 一致才命中。
     * 横排书名走 Text 组件无位图,跳过。预热失败静默(组合侧同步渲染兜底)。
     */
    private fun prewarmBookCovers() {
        val appContext = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                AppDatabase.getInstance(appContext).bookDao()
                    .observeAll().first()
                    .take(12)
                    .let { prewarmBookCovers(appContext, it) }
            }
        }
    }
}
