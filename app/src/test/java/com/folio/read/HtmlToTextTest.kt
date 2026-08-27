package com.folio.read

import com.folio.read.ui.reader.HtmlToText
import com.folio.read.ui.reader.indentContent
import com.folio.read.ui.reader.stripLeadingTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * epub/azw3 HTML→纯文本回归:对齐 Legado HtmlFormatter 后,含块级标签的书不再被压成零换行。
 * 旧版 HtmlToText 用 [ \t\r\f\v]+ 压空白,其中 \v 是垂直空白类、匹配了换行 \n,
 * 把块级标签生成的换行全压成空格 → 章节整章一坨(朝闻道实测 753 换行 → 0)。
 */
class HtmlToTextTest {

    @Test
    fun `含p_div块级标签_不压成零换行`() {
        val html = """
            <html><head><title>朝闻道</title></head>
            <body>
              <div class="part">
                <p>第一段正文内容,写点中文句号。</p>
                <p>第二段正文内容,也写点中文句号。</p>
                <p>第三段正文内容,终究要有结尾。</p>
              </div>
            </body></html>
        """.trimIndent()
        val out = HtmlToText.convert(html)
        assertTrue("含块级标签的 epub 不应被压成零换行(旧版 \\v 将换行压成空格)", out.contains("\n"))
    }

    @Test
    fun `正文链路_每段独立缩进`() {
        val html = """
            <html><head><title>朝闻道</title></head>
            <body>
              <div class="header0"><h1><span>朝闻道</span></h1></div>
              <div class="part">
                <p>第一段正文内容,写点中文句号。</p>
                <p>第二段正文内容,也写点中文句号。</p>
                <p>第三段正文内容,终究要有结尾。</p>
              </div>
            </body></html>
        """.trimIndent()
        val text = HtmlToText.convert(html).trim()
        val body = indentContent(stripLeadingTitle(text, "朝闻道"))
        val paras = body.split("\n").map { it.trim { c -> c.code <= 0x20 || c == '　' } }
            .filter { it.isNotEmpty() }
        assertEquals("正文应有 3 个非空段落", 3, paras.size)
        body.split("\n").filter { it.isNotBlank() }.forEach {
            assertTrue("每段应以两个全角空格缩进:\"$it\"", it.startsWith("　　"))
        }
    }

    @Test
    fun `无块级标签_整行_走零换行兜底分段`() {
        // 罗杰疑案类:正文无 <p>/<div>/<br>,段落以「句末标点+空格」分隔,HtmlToText 产一整行;
        // indentContent 的零换行兜底应把它切回多段并缩进。
        val html = "<html><body>第一段。 第二段。 第三段。</body></html>"
        val text = HtmlToText.convert(html).trim()
        val body = indentContent(text)
        assertTrue(body.contains("\n"))
        assertEquals(3, body.split("\n").size)
    }

    @Test
    fun `章节标题被正文拆成两行_仍能剥掉`() {
        // 罗杰疑案:标题「第一章 谢泼德医生在早餐桌上」在正文是两行(中间换行),旧 startsWith 剥不掉
        val title = "第一章 谢泼德医生在早餐桌上"
        val text = "第一章\n　　谢泼德医生在早餐桌上\n　　弗拉尔斯太太于16日晚离世而去。"
        assertEquals("弗拉尔斯太太于16日晚离世而去。", stripLeadingTitle(text, title))
    }

    @Test
    fun `章节标题独立成行_剥掉标题`() {
        assertEquals("刘慈欣", stripLeadingTitle("朝闻道\n　　刘慈欣", "朝闻道"))
    }

    @Test
    fun `标题与正文同行_剥掉标题与分隔空格`() {
        assertEquals("我比现在年轻多了", stripLeadingTitle("活着　我比现在年轻多了", "活着"))
    }

    @Test
    fun `标题与正文不符_原样返回`() {
        val title = "第一章"
        val text = "弗拉尔斯太太于16日晚离世而去。"
        assertEquals(text, stripLeadingTitle(text, title))
    }
}
