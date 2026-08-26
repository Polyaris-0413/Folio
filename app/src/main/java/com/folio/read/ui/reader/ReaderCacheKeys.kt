package com.folio.read.ui.reader

/** 章节识别规则版本:规则变化时章节块首缓存与分页缓存整体失效 */
const val ChapterRuleVersion = "ch3"

/** 文本处理版本:段落缩进/前言处理规则变化时,正文/分页/章节缓存整体失效 */
const val TextProcessVersion = "p11"

/** 章节块首缓存键中的版本签名:章节规则/文本处理变化时缓存失效(阅读页与目录页共用) */
val ChapterCacheKey = "$ChapterRuleVersion|$TextProcessVersion"
