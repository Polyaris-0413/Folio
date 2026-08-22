package com.folio.read

import com.folio.read.ui.reader.ChapterDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 章节识别回归测试:装饰符前缀/无章节兜底/既有规则不回归 */
class ChapterDetectorTest {

    private fun sample(titles: List<String>): String = buildString {
        titles.forEachIndexed { i, t ->
            append(t).append('\n')
            repeat(10) { append("这是第${i + 1}章的正文内容,用于填充章节之间的间距。") }
            append('\n')
        }
    }

    @Test
    fun `标准第X章仍识别`() {
        val s = sample(listOf("第1章 真实", "第2章 窥见"))
        assertEquals(2, ChapterDetector.detectChapterStarts(s).size)
    }

    @Test
    fun `装饰符前缀章节识别`() {
        val s = sample(listOf("☆、第一章 ", "☆、第二章 "))
        val starts = ChapterDetector.detectChapterStarts(s)
        assertEquals(2, starts.size)
        // 块首必须是章节标题行(识别到「☆、第一章」而非其后的正文)
        assertTrue(s.substring(starts[0]).startsWith("☆、第一章"))
    }

    @Test
    fun `无章节标题整本一章`() {
        val s = buildString {
            repeat(30) { append("这是没有章节标题的纯段落正文。") }
        }
        assertEquals(listOf(0), ChapterDetector.detectChapterStarts(s))
    }

    @Test
    fun `前言标题仍识别`() {
        val s = sample(listOf("前言", "第1章 正文"))
        assertEquals(2, ChapterDetector.detectChapterStarts(s).size)
    }
}
