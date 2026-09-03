package com.folio.read.util

import android.util.Log

/**
 * 统一日志出口:tag 保持 Folio 前缀;调试级 d 在 release 构建由 R8 整体剥离
 * (proguard-rules.pro 的 -assumenosideeffects,调用点与消息字符串拼接一并消除),
 * w/e 保留供线上排障。
 * 注意:部分 ROM(ColorOS)会丢弃 debug 级日志,需在那类设备上定位时临时改用 w。
 */
object AppLog {

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) Log.w(tag, msg) else Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) Log.e(tag, msg) else Log.e(tag, msg, tr)
    }
}
