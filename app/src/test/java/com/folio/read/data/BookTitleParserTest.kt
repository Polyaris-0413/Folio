package com.folio.read.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 书名清洗回归测试:样本与预期来自 2026-08-20 实机校准(含误伤对照) */
class BookTitleParserTest {

    @Test
    fun `主样本全链路净化`() {
        assertEquals(
            "哈利波特",
            BookTitleParser.parse("1_哈利波特完整系列（全七册）作者：jkroing(1).txt"),
        )
    }

    @Test
    fun `营销裸词尾部剥除`() {
        assertEquals("三体", BookTitleParser.parse("三体全集.txt"))
        assertEquals("凡人修仙传", BookTitleParser.parse("凡人修仙传 完结版 免费阅读.txt"))
        assertEquals("雪中悍刀行", BookTitleParser.parse("雪中悍刀行（全文精校版）.txt"))
    }

    @Test
    fun `营销词不误伤书名正文`() {
        // 「系列」不在词表(合法书名部分),只删「完整系列」组合词
        assertEquals("哈利波特系列", BookTitleParser.parse("哈利波特系列.txt"))
        // 「完整」裸词不在词表
        assertEquals("完整的救赎", BookTitleParser.parse("完整的救赎.txt"))
    }

    @Test
    fun `书名号路径同样过噪声清洗`() {
        // 《》提取结果也过营销词表(曾绕过第二层清洗直接返回)
        assertEquals("刘慈欣中短篇科幻小说", BookTitleParser.parse("《刘慈欣中短篇科幻小说合集》作者：刘慈欣.txt"))
        // 《》内版本括号同样剥除
        assertEquals("三体", BookTitleParser.parse("《三体（全集）》.txt"))
    }

    @Test
    fun `前缀序号不误伤 2_5 小数书名`() {
        assertEquals("2.5次元的诱惑", BookTitleParser.parse("2.5次元的诱惑.txt"))
    }

    @Test
    fun `版本括号白名单不误伤正文括号`() {
        // 2026-08-26 用户拍板「直接滤掉所有 ()」:正文括号也剥掉
        assertEquals("诗经", BookTitleParser.parse("诗经（小雅集）.txt"))
        assertEquals("韩娱之国民妖精", BookTitleParser.parse("（综）韩娱之国民妖精.txt"))
    }

    @Test
    fun `尾部编号不误伤年份`() {
        // 全滤 () 后,年份括号也剥掉(用户拍板「直接滤掉所有 ()」)
        assertEquals("源代码", BookTitleParser.parse("源代码（1999）.txt"))
    }

    @Test
    fun `版本括号正常剥除`() {
        assertEquals("雪中悍刀行", BookTitleParser.parse("雪中悍刀行（全文精校版）.txt"))
        assertEquals("三国演义", BookTitleParser.parse("三国演义（120回本）.txt"))
    }

    @Test
    fun `点号序号前缀正常剥除`() {
        assertEquals("围城", BookTitleParser.parse("01. 围城.txt"))
    }

    @Test
    fun `尾部半角编号剥除`() {
        assertEquals("哈利波特与密室", BookTitleParser.parse("哈利波特与密室(2).txt"))
    }

    @Test
    fun `书名内作者括号不误伤`() {
        // 直接滤所有 () 后,作者括号剥掉
        assertEquals("活着", BookTitleParser.parse("活着（余华著）.txt"))
    }

    @Test
    fun `资源站括号剥除`() {
        assertEquals("活着", BookTitleParser.parse("活着 (余华) (z-library.sk, 1lib.sk, z-lib.sk).azw3"))
    }

    @Test
    fun `无扩展名含内部点书名全滤括号`() {
        // 书名已无真扩展名、含 z-lib.sk 内部点:只去已知扩展名,不误截内部点,再全滤括号
        assertEquals("魔鬼积木", BookTitleParser.parse("魔鬼积木 (刘慈欣) (z-library.sk, 1lib.sk, z-lib.sk)"))
    }
}
