package io.legado.app.help

import io.legado.app.PortingContext
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.TxtTocRule
import org.json.JSONArray

/**
 * legado `io.legado.app.help.DefaultData` 的宿主替身（只取 TXT 目录规则一支）。
 *
 * legado 原类是全应用默认数据的总装载器（书源/订阅源/字典规则/主题/朗读配置/键盘辅助等），
 * 逐字搬运会把 `DictRule → AnalyzeRule → BackstageWebView → 各类 UI 对话框` 整条链带进来
 * （实测 import 闭包 357 文件 / 6.5 万行），与目录系统无关，故只保留 [txtTocRules]。
 *
 * 与 legado 的差异：原类用 GSON 解析，Folio 无 GSON 依赖，改用平台自带的 org.json；
 * 字段名与 JSON 结构不变，asset 文件逐字节相同。
 */
object DefaultData {

    /** 内置 TXT 目录规则，对应 legado `DefaultData.txtTocRules` */
    val txtTocRules: List<TxtTocRule> by lazy {
        runCatching {
            val json = PortingContext.required.assets
                .open("defaultData/txtTocRule.json")
                .use { it.readBytes().decodeToString() }
            parseTxtTocRules(json)
        }.getOrElse {
            AppLog.put("内置 TXT 目录规则解析失败", it)
            emptyList()
        }
    }

    /** 与 assets 版同源的解析入口：抽出纯函数以便 JVM 单测直接注入 JSON 字符串 */
    fun parseTxtTocRules(json: String): List<TxtTocRule> {
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            TxtTocRule(
                id = item.getLong("id"),
                name = item.getString("name"),
                rule = item.getString("rule"),
                example = if (item.isNull("example")) null else item.getString("example"),
                serialNumber = item.getInt("serialNumber"),
                enable = item.getBoolean("enable"),
            )
        }
    }
}
