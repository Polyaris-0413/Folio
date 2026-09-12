package com.folio.read.ui.reader

/** 章节识别规则版本:章节切分规则变化时页表缓存整体失效。
 * ch5:目录识别由「单条硬编码正则」换成移植自 legado 的规则库+择优引擎,章节边界整体改变 */
const val ChapterRuleVersion = "ch5"

/** 文本处理版本:段落缩进/前言处理规则变化时,页表缓存整体失效。
 * p14:分章在原文(引擎按字节偏移),段落加工改为逐章进行,与 p13 的整本加工不同 */
const val TextProcessVersion = "p14"
