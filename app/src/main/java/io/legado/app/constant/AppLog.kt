package io.legado.app.constant

import com.folio.read.util.AppLog as FolioAppLog

/**
 * legado `io.legado.app.constant.AppLog` 的宿主替身。
 *
 * 移植过来的 legado 文件（如 `TextFile.kt`）会调用 `AppLog.put(msg, e)`，这类调用点
 * 一律保持原样，日志出口统一转发到 Folio 已有的 [FolioAppLog]（release 剥离 d 级）。
 *
 * 只实现移植集实际用到的 [put]；legado 原类还有 putNotSave/putDebug 等，
 * 属其自身日志体系（落盘到文件、调试开关），Folio 无对应需求。
 */
object AppLog {

    private const val TAG = "FolioToc"

    /**
     * legado 的 [toast] 参数用于在界面上弹提示。移植集里没有任何调用点传 true
     * （`TextFile.getTocRule` 只用它记录正则语法错误），且解析在后台线程执行，
     * 因此这里不实现弹提示，仅记录日志。
     */
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        if (message == null) return
        if (throwable == null) FolioAppLog.w(TAG, message) else FolioAppLog.w(TAG, message, throwable)
    }
}
