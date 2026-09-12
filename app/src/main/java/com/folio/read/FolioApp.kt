package com.folio.read

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.folio.read.data.AppDatabase
import com.folio.read.ui.components.CoverCache
import com.folio.read.ui.components.prewarmBookCovers
import com.folio.read.ui.toc.TocRules
import com.folio.read.util.AppLog
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
        // 移植自 legado 的代码以全局 appDb/DefaultData 形式访问宿主,Folio 无 splitties 的 appCtx,
        // 由此处注入 Application Context(须早于任何目录解析)
        io.legado.app.PortingContext.init(this)
        seedTocRulesIfEmpty()
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
     * 启动时播种 TXT 目录规则表（仅当表为空）。
     * 移植的引擎只在解析书籍时才做懒加载，若等到那时才播种，用户首次进「目录规则」页
     * 会看到空列表；legado 自身是在启动的 upVersion 里完成导入的，这里与之对齐。
     * 失败不阻塞启动（规则页会显示空态提示，用户可手动「恢复内置」）。
     */
    private fun seedTocRulesIfEmpty() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { TocRules().ensureDefaults() }.getOrElse { e ->
                AppLog.w("FolioApp", "内置目录规则播种失败: $e", e)
            }
        }
    }

    /**
     * 封面预热:冷启动时书架组合之前,后台先把首屏书的封面位图画进缓存——
     * CoverArtwork 组合时缓存命中同步取位图,书名首帧直接显示(组合后才异步渲染
     * 曾致冷启动书名延迟闪现,用户反馈)。只预热排序最前的 12 本(首屏可见量+缓冲),
     * 渲染尺寸与 CoverArtwork 同为规范值(COVER_RENDER_WIDTH_DP×密度),key 一致才命中。
     * 横排书名走 Text 组件无位图,跳过。预热失败记日志(组合侧同步渲染兜底)。
     */
    private fun prewarmBookCovers() {
        val appContext = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                AppDatabase.getInstance(appContext).bookDao()
                    .observeAll().first()
                    .take(12)
                    .let { prewarmBookCovers(appContext, it) }
            }.getOrElse { e ->
                AppLog.w("FolioApp", "封面预热失败: $e", e)
            }
        }
    }
}
