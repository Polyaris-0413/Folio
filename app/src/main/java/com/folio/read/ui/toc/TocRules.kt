package com.folio.read.ui.toc

import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.help.DefaultData
import kotlinx.coroutines.flow.Flow

/**
 * 规则库读写入口。
 *
 * 移植来的 [TxtTocRuleRepository] 是逐字节搬运的（由 `scripts/check-port-drift.sh` 把关），
 * 因此它缺失的 Folio 侧动作写在这里，而不是去改搬运文件。缺的两个动作是：
 *  - 「恢复内置规则」：legado 放在 `DefaultData.importDefaultTocRules()`，
 *    而移植时只搬了 `DefaultData.txtTocRules`（默认数据总装载器的其余分支与目录无关）；
 *  - `save` 的统一语义：DAO 是 `@Insert(onConflict = REPLACE)`，天然兼作新增与更新。
 */
class TocRules {

    private val repo = TxtTocRuleRepository()

    fun flowAll(): Flow<List<TxtTocRule>> = repo.flowAll()

    /** 新增或更新一条规则：`@Insert(REPLACE)` 按主键覆盖，故新增/编辑同一个入口 */
    suspend fun save(rule: TxtTocRule) = repo.insert(rule)

    suspend fun setEnabled(rule: TxtTocRule, enabled: Boolean) =
        repo.update(rule.copy(enable = enabled))

    suspend fun delete(rule: TxtTocRule) = repo.delete(rule)

    /**
     * 规则表为空时写入内置规则。
     *
     * 语义对应 legado 启动时的 `DefaultData.upVersion()` → `importDefaultTocRules()`
     * （那边用版本戳门控）。移植时只搬了引擎内部的懒加载分支——它仅在解析书籍时触发，
     * 于是全新安装、尚未打开任何书时规则页会是空的，故这里在启动时补一次播种。
     */
    suspend fun ensureDefaults() {
        if (repo.count() == 0) restoreBuiltIn()
    }

    /**
     * 恢复内置规则：删掉全部预置规则（id < 0）再整批写回，用户自建规则（id > 0）保留。
     * 语义照 legado `DefaultData.importDefaultTocRules()`（先 `deleteDefault()` 再 insert）。
     */
    suspend fun restoreBuiltIn() {
        val builtIn = DefaultData.txtTocRules
        repo.deleteByIds(builtIn.map { it.id })
        repo.insert(*builtIn.toTypedArray())
    }
}
