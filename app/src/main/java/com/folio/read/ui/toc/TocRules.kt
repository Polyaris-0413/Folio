package com.folio.read.ui.toc

import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.help.DefaultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 规则页的展示模型。
 *
 * 为什么需要这一层：移植来的 [TxtTocRule] 重写了 `equals`/`hashCode` **只比较 `id`**
 * （legado 原样如此，用于其列表去重），而 Compose 的 `collectAsState` 按结构相等判断
 * 状态是否变化。若直接收集 `List<TxtTocRule>`，把某条规则的开关由 true 改成 false 后
 * 新列表与旧列表「元素两两相等」（每个 id 都没变）→ 状态更新被判为无变化而丢弃 →
 * 界面不重组 → 表现为开关点了没反应（实际已写入数据库，重启后才看得到）。
 * 本模型用全字段相等，绕开该判定。
 */
data class TocRuleUi(
    val id: Long,
    val name: String,
    val rule: String,
    val example: String?,
    val serialNumber: Int,
    val enable: Boolean,
) {
    /** 预置规则 id 为负；[NEW_ID] 表示尚未落库的新建规则 */
    val isBuiltIn: Boolean get() = id < 0

    companion object {
        const val NEW_ID = 0L
    }
}

private fun TxtTocRule.toUi() = TocRuleUi(id, name, rule, example, serialNumber, enable)

private fun TocRuleUi.toEntity(id: Long) = TxtTocRule(
    id = id,
    name = name,
    rule = rule,
    example = example,
    serialNumber = serialNumber,
    enable = enable,
)

/**
 * 规则库读写入口。
 *
 * 移植来的 [TxtTocRuleRepository] 是逐字节搬运的（由 `scripts/check-port-drift.sh` 把关），
 * 因此 Folio 侧的动作写在这里，而不是去改搬运文件。用到的都是它已有的方法
 * （`enableByIds`/`deleteByIds`/`insert`），没有另造一套 DAO 调用。
 */
class TocRules {

    private val repo = TxtTocRuleRepository()

    /** 对外只暴露展示模型，避免上游踩到 `TxtTocRule` 只比 id 的相等语义（见 [TocRuleUi]） */
    fun flowAll(): Flow<List<TocRuleUi>> = repo.flowAll().map { list -> list.map { it.toUi() } }

    suspend fun setEnabled(id: Long, enabled: Boolean) = repo.enableByIds(listOf(id), enabled)

    /** 新增（id 为 [TocRuleUi.NEW_ID] 时分配时间戳作主键，与 legado 同款）或更新 */
    suspend fun save(rule: TocRuleUi) {
        val id = if (rule.id == TocRuleUi.NEW_ID) System.currentTimeMillis() else rule.id
        repo.insert(rule.toEntity(id))
    }

    suspend fun delete(id: Long) = repo.deleteByIds(listOf(id))

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
