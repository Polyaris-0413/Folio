package com.folio.read

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.folio.read.util.FrameJankLog

/** 应用级入口:debug 构建下按前台/后台启停帧掉帧日志(供暴力测试流畅度) */
class FolioApp : Application() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
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
}
