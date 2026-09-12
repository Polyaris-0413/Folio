package com.folio.read.port

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.folio.read.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DefaultData
import io.legado.app.model.localBook.TextFile
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 目录引擎的设备侧行为测试。
 *
 * 为什么用 instrumented 而不是 JVM 单测：切章引擎 `TextFile` 取目录规则的入口是
 * `appDb.txtTocRuleDao`（Room），JVM 单测没有 Android 运行时。这里用真实 Room 库把
 * 规则表铺成受控状态，再以内存流注入正文（[Book.openStream]），因此覆盖的正是
 * 「规则评分择优 → 正则切章 → 字节偏移取正文」这条真实链路。
 *
 * 注意：测试直接读写应用自身的 `folio.db` 规则表，并在 [tearDown] 恢复内置规则；
 * 请只在开发机上运行 connectedAndroidTest，不要在有阅读数据的日常机上跑。
 */
@RunWith(AndroidJUnit4::class)
class TextFileEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
    }

    /** 把规则表铺成「只有给定规则」的受控状态（传空数组即清空） */
    private fun seedRules(vararg rules: TxtTocRule) {
        runBlocking(Dispatchers.IO) {
            val dao = db.txtTocRuleDao
            dao.all.forEach { dao.delete(it) }
            if (rules.isNotEmpty()) dao.insert(*rules)
        }
    }

    /** 构造以内存流为正文的载体；charset 固定 UTF-8 以保证探测结果确定 */
    private fun textBook(
        text: String,
        tocRule: String = "",
        splitLongChapter: Boolean = true,
    ) = Book(
        bookUrl = "test://book",
        originName = "test.txt",
        charset = "UTF-8",
        tocUrl = tocRule,
        splitLongChapterEnabled = splitLongChapter,
        openStream = { ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) },
    )

    /** 引擎内部要走 Room（同步 DAO 不允许主线程），统一包一层 IO */
    private fun <T> offMainThread(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun chapterList(book: Book): List<BookChapter> =
        offMainThread { TextFile.getChapterList(book) }

    // ------------------------------------------------------------ 规则打分择优

    @Test
    fun `打分择优选中目录规则并按规则切章`() {
        seedRules(*DefaultData.txtTocRules.toTypedArray())
        val book = textBook(
            buildString {
                // 标题间隔须 >1000 字节:引擎的计分规则是「相邻命中相距 >1000 才 +1」
                for (i in 1..4) {
                    append("第${i}章 第${i}个标题\n")
                    append("正".repeat(1100))
                    append("\n")
                }
            },
        )

        val chapters = chapterList(book)
        assertEquals("四个远间隔标题应切出四章", 4, chapters.size)
        assertTrue("首章标题应含第1章,实际=${chapters[0].title}", chapters[0].title.contains("第1章"))
        // 择优结果写回载体,供调用方落库(books.tocRule)
        assertTrue("自动择优应产出非空规则", book.tocUrl.isNotBlank())
    }

    // ------------------------------------------------------------ 正则切章 + 前言章

    @Test
    fun `固定规则切章并为首个标题前的内容生成前言章`() {
        val pinned = "^第[一二三四五六七八九十]+章.{0,30}$"
        seedRules(TxtTocRule(id = 1L, name = "测试规则", rule = pinned, serialNumber = 1))
        val book = textBook(
            text = "开头的一段简介\n\n第一章 开始\n正文甲内容\n\n第二章 继续\n正文乙内容\n",
            tocRule = pinned,
        )

        val chapters = chapterList(book)
        assertEquals(listOf("前言", "第一章 开始", "第二章 继续"), chapters.map { it.title })

        val contents = offMainThread { chapters.map { TextFile.getContent(book, it) } }
        assertTrue("前言章应含简介文字,实际=${contents[0]}", contents[0].contains("开头的一段简介"))
        assertTrue("第一章应含正文甲,实际=${contents[1]}", contents[1].contains("正文甲内容"))
        assertTrue("第二章应含正文乙,实际=${contents[2]}", contents[2].contains("正文乙内容"))
    }

    // ------------------------------------------------------------ 字数分章兜底

    @Test
    fun `无规则命中时按字数兜底分章并生成合成标题`() {
        // 唯一启用的规则不可能命中 → 择优 0 分 → 走 analyze() 的 10KB 字数分章
        seedRules(TxtTocRule(id = 1L, name = "不命中", rule = "^@@NEVER@@$", serialNumber = 1))
        val book = textBook(
            text = buildString {
                repeat(30) {
                    append("正".repeat(180))
                    append("\n")
                }
            },
        )

        val chapters = chapterList(book)
        assertTrue(">10KB 正文应切出多章,实际=${chapters.size}", chapters.size >= 2)
        chapters.forEach {
            assertTrue(
                "兜底标题形如 第N章(M),实际=${it.title}",
                Regex("""^第\d+章\(\d+\)$""").matches(it.title),
            )
        }
    }

    // ------------------------------------------------------------ 超长章节拆分

    @Test
    fun `单章超过100KB时按开关拆分并加序号后缀`() {
        val pinned = "^@@CH@@.*$"
        val title = "@@CH@@超长章节"
        seedRules(TxtTocRule(id = 1L, name = "测试规则", rule = pinned, serialNumber = 1))
        // UTF-8 下「字」占 3 字节,150K 字符 ≈ 450KB,远超 maxLengthWithToc(102400)
        val longText = buildString {
            append("$title\n")
            repeat(150) {
                append("字".repeat(1024))
                append("\n")
            }
        }

        val split = chapterList(textBook(text = longText, tocRule = pinned))
        assertTrue("超长章应被拆成多章,实际=${split.size}", split.size >= 2)

        // 引擎被原样搬运,这里固定它的真实拆分语义(而非我们期望的样子):
        // 被拆的「原章」被置为 start==end 的空章、标题不变,并**留在列表里**;
        // 拆分出的子章紧随其后、标题加 (N) 后缀。legado 侧也不过滤这种空章
        // (LocalBook.getChapterList 只做 LinkedHashSet 去重与空标题补名),
        // 所以这是它的用户可见行为,不是移植缺陷。
        val placeholder = split.first()
        assertEquals("原章标题不变", title, placeholder.title)
        assertEquals("原章被置空(start==end)", placeholder.start, placeholder.end)

        val subChapters = split.drop(1)
        assertTrue("应至少拆出两个子章,实际=${subChapters.size}", subChapters.size >= 2)
        subChapters.forEach {
            assertTrue(
                "拆分后子章标题形如 原标题(N),实际=${it.title}",
                Regex("""^${Regex.escape(title)}\(\d+\)$""").matches(it.title),
            )
        }

        val noSplit = chapterList(textBook(text = longText, tocRule = pinned, splitLongChapter = false))
        assertEquals("关闭拆分后应为单章", 1, noSplit.size)
        assertTrue("关闭拆分后该章应有实际跨度", noSplit[0].start != noSplit[0].end)
    }

    // ------------------------------------------------------------ 收尾

    /** 恢复内置规则,不把开发机上应用的规则表留在测试状态 */
    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            val dao = db.txtTocRuleDao
            dao.all.forEach { dao.delete(it) }
            dao.insert(*DefaultData.txtTocRules.toTypedArray())
            TextFile.clear()
        }
    }
}
