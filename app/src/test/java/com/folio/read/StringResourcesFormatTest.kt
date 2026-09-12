package com.folio.read

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 字符串资源格式符闸门。
 *
 * 为什么需要：`strings.xml` 里的格式符写坏了，**编译期 aapt2 不一定拦得住**——
 * 曾经出现过 `确定删除规则「%1」吗？`（`$s` 被脚本吃掉）这种串，构建照过，
 * 直到运行期 `String.format` 抛 `UnknownFormatConversionException` 才闪退。
 * 这个测试在单测阶段把「每个 % 要么是 %% 要么是合法格式符」钉死。
 */
class StringResourcesFormatTest {

    private val stringsXml: File by lazy {
        listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).firstOrNull { it.exists() }
            ?: error("找不到 strings.xml,当前工作目录: ${File("").absolutePath}")
    }

    @Test
    fun `每个百分号要么是转义要么是合法格式符`() {
        val content = stringsXml.readText()
        val entry = Regex("""<string name="([^"]+)">([\s\S]*?)</string>""")
        val problems = mutableListOf<String>()
        var checked = 0

        for (m in entry.findAll(content)) {
            val name = m.groupValues[1]
            val value = m.groupValues[2]
            checked++
            var i = 0
            while (i < value.length) {
                if (value[i] != '%') { i++; continue }
                val rest = value.substring(i)
                when {
                    // %% 转义的字面百分号
                    rest.startsWith("%%") -> i += 2
                    // %n$s / %n$d 这类带位置参数的格式符
                    Regex("""^%\d+\$[sd]""").find(rest) != null -> i += rest.indexOfFirst { it == '$' } + 2
                    // %s / %d 这类不带位置的格式符
                    Regex("""^%[sd]""").find(rest) != null -> i += 2
                    else -> {
                        problems += "$name: 位置 $i 处的 % 不是合法格式符 → ${rest.take(12)}"
                        i++
                    }
                }
            }
        }

        assertTrue("strings.xml 里没解析到任何字符串,路径可能不对: ${stringsXml.absolutePath}", checked > 0)
        assertTrue(
            "发现 ${problems.size} 处损坏的格式符(运行期会抛 String.format 异常):\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
        assertEquals("已核对字符串条数", checked, entry.findAll(content).count())
    }
}
