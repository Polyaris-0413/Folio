package io.legado.app

import android.content.Context

/**
 * 移植层的 Application Context 持有者。
 *
 * legado 源码中通过第三方库 splitties 的全局 `appCtx` 取 Application；Folio 未引入该库，
 * 因此改为显式注入：`FolioApp.onCreate()` 调用 [init]。
 *
 * 之所以用持有者而不是在每个替身里各存一份 Context，是为了让「普通对象/顶层属性」
 * 形式的 legado 代码（如 `io.legado.app.data.appDb`、`io.legado.app.help.DefaultData`）
 * 可以原样保留其访问方式。
 */
object PortingContext {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 未初始化即访问属编程错误：所有入口都经由 Application 启动，不存在合法的不初始化路径 */
    val required: Context
        get() = appContext ?: error("PortingContext 未初始化：需在 FolioApp.onCreate() 中调用 init()")
}
