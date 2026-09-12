package com.folio.read.port

import com.folio.read.ui.reader.ChapterDetector
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.DefaultData
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.Utf8BomUtils
import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 移植自 legado 的 TXT 目录系统的回归测试（纯 JVM 部分）。
 *
 * 引擎 `TextFile` 本身需要 Room 取规则表，属设备侧验证（instrumented / 真机），
 * 这里覆盖不依赖 Android 运行时的那一层：内置规则资产、编码探测、MD5、字数格式、
 * 以及载体模型的宿主适配语义。
 */
class LegadoTocPortTest {

    // ---------- 内置目录规则资产 ----------

    private val rulesAsset: File by lazy {
        listOf(
            File("src/main/assets/defaultData/txtTocRule.json"),
            File("app/src/main/assets/defaultData/txtTocRule.json"),
        ).firstOrNull { it.exists() }
            ?: error("找不到内置目录规则资产,当前工作目录: ${File("").absolutePath}")
    }

    private fun loadRules() = DefaultData.parseTxtTocRules(rulesAsset.readText())

    @Test
    fun `内置规则资产解析出全部条目且结构符合 legado 约定`() {
        val rules = loadRules()
        assertEquals("内置规则条数应等于资产条目数", 26, rules.size)
        assertTrue("预置规则 id 全部为负(用户自定义规则用正数 id)", rules.all { it.id < 0 })
        assertEquals("id 范围应为 -100..-1", -100L to -1L, rules.minOf { it.id } to rules.maxOf { it.id })
        // serialNumber 决定择优时的尝试顺序,0..24 连续 + 99 兜底
        assertEquals((0..24).toList() + 99, rules.map { it.serialNumber }.sorted())
        assertEquals("启用条目数", 12, rules.count { it.enable })
    }

    @Test
    fun `兜底规则是空正则且默认关闭`() {
        val fallback = loadRules().single { it.rule.isEmpty() }
        assertEquals(-100L, fallback.id)
        assertEquals("默认分章规则", fallback.name)
        assertFalse("空正则作为兜底不可默认启用", fallback.enable)
    }

    @Test
    fun `serialNumber 为 1 的目录规则与 Folio 既有识别结果一致`() {
        val rule = loadRules().single { it.serialNumber == 1 }
        assertEquals(-2L, rule.id)
        assertEquals("目录", rule.name)
        assertTrue("该规则应默认启用", rule.enable)
        // 该条目即 legado 默认「目录」规则,ChapterDetector 的正则以它为底(另加装饰符前缀等 Folio 扩展);
        // 因此凡它自己的示例行,两者都必须判为章节标题
        val example = rule.example ?: error("serialNumber=1 的规则应带 example")
        assertTrue(
            "asset 规则应命中自身示例: $example",
            Regex(rule.rule, RegexOption.MULTILINE).matches(example),
        )
        assertTrue("ChapterDetector 也应命中该示例", ChapterDetector.isTitleLine(example))
    }

    // ---------- 编码探测(移植的 icu4j) ----------

    @Test
    fun `UTF-8 中文被探测为 UTF-8 且可还原`() {
        val text = "第一章 目录识别的中文测试"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val name = EncodingDetect.getEncode(bytes)
        assertEquals("UTF-8", name)
        assertEquals(text, String(bytes, Charset.forName(name)))
    }

    @Test
    fun `GBK 中文被探测为非 UTF-8 且解码可还原`() {
        val text = "第一章 目录识别的中文测试"
        val gbk = Charset.forName("GBK")
        val bytes = text.toByteArray(gbk)
        val name = EncodingDetect.getEncode(bytes)
        assertNotEquals("GBK 字节不应被判为 UTF-8", "UTF-8", name)
        // 关键性质:探测出的字符集必须能把这批字节还原成原文(GB18030 等超集同样满足)
        assertEquals(text, String(bytes, Charset.forName(name)))
    }

    @Test
    fun `BOM 判定与 legado 语义一致`() {
        // legado 的实现要求 bytes.size > 3,故「恰好 3 字节的纯 BOM」返回 false(原样保留该行为)
        assertFalse(Utf8BomUtils.hasBom(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())))
        assertTrue(Utf8BomUtils.hasBom(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x41)))
        assertFalse(Utf8BomUtils.hasBom("第一章".toByteArray(Charsets.UTF_8)))
        // 去掉 BOM 后应可正常解码
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "第一章".toByteArray(Charsets.UTF_8)
        assertEquals("第一章", String(Utf8BomUtils.removeUTF8BOM(withBom), Charsets.UTF_8))
    }

    // ---------- MD5(替代 hutool 的原生实现) ----------

    @Test
    fun `md5Encode16 与标准 MD5 的 8 到 24 位子串一致`() {
        // md5("abc") = 900150983cd24fb0d6963f7d28e17f72
        assertEquals("900150983cd24fb0d6963f7d28e17f72", MD5Utils.md5Encode("abc"))
        assertEquals("3cd24fb0d6963f7d", MD5Utils.md5Encode16("abc"))
        // md5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", MD5Utils.md5Encode(""))
        assertEquals(32, MD5Utils.md5Encode("任意中文").length)
        assertEquals(16, MD5Utils.md5Encode16("任意中文").length)
    }

    // ---------- 字数格式 ----------

    @Test
    fun `wordCountFormat 阈值与单位符合 legado 语义`() {
        assertEquals("", StringUtils.wordCountFormat(0))
        assertEquals("1234字", StringUtils.wordCountFormat(1234))
        // 严格大于 10000 才换成万字,10000 本身仍是「10000字」
        assertEquals("10000字", StringUtils.wordCountFormat(10000))
        assertEquals("1万字", StringUtils.wordCountFormat(10001))
        assertEquals("12万字", StringUtils.wordCountFormat(120000))
        // 字符串重载:纯数字按数字格式化,非数字原样返回
        assertEquals("1234字", StringUtils.wordCountFormat("1234"))
        assertEquals("约三千字", StringUtils.wordCountFormat("约三千字"))
        assertEquals("", StringUtils.wordCountFormat(null))
    }

    // ---------- 载体模型(宿主适配语义) ----------

    @Test
    fun `fileCharset 把 legado 的 Unicode 别名映射为 UTF-16 并回落 UTF-8`() {
        assertEquals(Charsets.UTF_8, Book(charset = "UTF-8").fileCharset())
        assertEquals(Charset.forName("GBK"), Book(charset = "GBK").fileCharset())
        // legado AppConst.charsets 里列了 "Unicode",而 Charset.forName("Unicode") 在 JDK 上不可用
        assertEquals(Charset.forName("UTF-16"), Book(charset = "Unicode").fileCharset())
        // 非法名称不抛异常,回落 UTF-8
        assertEquals(Charsets.UTF_8, Book(charset = "不存在的编码").fileCharset())
        assertEquals(Charsets.UTF_8, Book(charset = null).fileCharset())
    }

    @Test
    fun `BookChapter 按 url 判定相等且文件名格式与 legado 一致`() {
        val a = BookChapter(url = "u1", title = "第一章")
        val b = BookChapter(url = "u1", title = "标题不同但 url 相同")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, BookChapter(url = "u2", title = "第一章"))

        val named = BookChapter(url = "u", title = "第一章", index = 7)
        // legado 语义:%05d 序号 + titleMD5(16 位) + 后缀
        assertTrue(named.getFileName().matches(Regex("^00007-[0-9a-f]{16}\\.nb$")))
        assertTrue(named.getFontName().matches(Regex("^00007-[0-9a-f]{16}\\.ttf$")))
    }
}
