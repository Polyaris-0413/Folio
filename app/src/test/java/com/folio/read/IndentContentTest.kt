package com.folio.read

import com.folio.read.ui.reader.indentContent
import org.junit.Assert.assertEquals
import org.junit.Test

/** epub/azw3 段落缩进回归:无块级标签的整行文本按「句末标点+空格」分段缩进;已有换行的文本不再按空格误切 */
class IndentContentTest {

    @Test
    fun `零换行_句末标点后空格_分段缩进`() {
        // 罗杰疑案类 epub:无 <p> 标签,段落以「。 」分隔,HtmlToText 输出一整行
        val input = "弗拉尔斯太太于16日晚离世而去。17日早晨八点就有人来请我去。她也死了好几个小时了。 九点过几分我就回了家。 我取出钥匙打开前门。"
        val out = indentContent(input)
        assertEquals(
            "　　弗拉尔斯太太于16日晚离世而去。17日早晨八点就有人来请我去。她也死了好几个小时了。\n" +
                "　　九点过几分我就回了家。\n" +
                "　　我取出钥匙打开前门。",
            out,
        )
    }

    @Test
    fun `已有换行_不按空格重复分段`() {
        // 有块级标签的书,HtmlToText 已产出 \n,不应把段内正常空格误切成段
        val input = "para one。para two。\npara three。para four。"
        val out = indentContent(input)
        assertEquals("　　para one。para two。\n　　para three。para four。", out)
    }

    @Test
    fun `引号内句末后紧跟引号_不误切对话`() {
        // 「你好。」后是右引号,空格前不是句末标点,不切分;仅在下一段「然后。 」处切
        val input = "他说：“你好。”然后她回答。 第二段开始。"
        val out = indentContent(input)
        assertEquals(2, out.split("\n").size)
    }
}
