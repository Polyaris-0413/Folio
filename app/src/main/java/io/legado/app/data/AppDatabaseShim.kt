package io.legado.app.data

import com.folio.read.data.AppDatabase
import io.legado.app.PortingContext

/**
 * legado 顶层单例 `appDb` 的宿主替身。
 *
 * legado 在 `io.legado.app.data.AppDatabase.kt` 里以顶层属性 `val appDb` 暴露全局库实例，
 * 其 DAO 通过 `appDb.xxxDao` 访问。移植过来的文件（`TextFile.kt`、`TxtTocRuleRepository.kt`）
 * 原样保留 `appDb` 的写法，故这里提供同名顶层属性，指向 Folio 的 Room 库。
 */
val appDb: AppDatabase
    get() = AppDatabase.getInstance(PortingContext.required)
