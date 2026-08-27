package com.folio.read

import com.folio.read.ui.reader.buildTxtChapters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** txt 逐章产出回归测试:标题独立、正文缩进、无标题整本一章、前言章 */
class BuildTxtChaptersTest {

    @Test
    fun `标准第X章切分_标题独立`() {
        val raw = buildString {
            append("第1章 开端\n")
            append("第一章的正文内容。\n")
            append("继续正文。\n\n")
            append("第2章 发展\n")
            append("第二章的正文内容。\n")
        }
        val chapters = buildTxtChapters(raw)
        assertEquals(2, chapters.size)
        assertEquals("第1章 开端", chapters[0].title)
        // 标题独立:标题不重复进 content;正文仍在 content 里
        assertTrue(!chapters[0].content.contains("第1章"))
        assertTrue(chapters[0].content.contains("第一章的正文内容。"))
        assertEquals("第2章 发展", chapters[1].title)
    }

    @Test
    fun `无章节标题_整本一章_标题置空`() {
        val raw = buildString {
            repeat(30) { append("这是没有章节标题的纯段落正文。\n") }
        }
        val chapters = buildTxtChapters(raw)
        assertEquals(1, chapters.size)
        assertEquals("", chapters[0].title)
        assertTrue(chapters[0].content.contains("纯段落正文"))
    }

    @Test
    fun `正文前置内容_产出前言章`() {
        val raw = buildString {
            append("版权声明一句话。\n")
            append("第1章 开始\n")
            append("正文。\n")
        }
        val chapters = buildTxtChapters(raw)
        // 前置内容 + 首标题 → processParagraphs 插入「前言」标题行,成为第一章
        assertEquals(2, chapters.size)
        assertEquals("前言", chapters[0].title)
        assertTrue(chapters[0].content.contains("版权声明"))
        assertEquals("第1章 开始", chapters[1].title)
    }
}
