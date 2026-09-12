package io.legado.app.help.book

import io.legado.app.data.entities.Book

/**
 * legado `io.legado.app.help.book.BookExtensions` 的宿主替身：只提供 `TextFile.kt` 用到的两个扩展。
 */

/**
 * legado 语义：本地书文件比「上次解析时间」更新时返回 true，用于让
 * `TextFile.getChapterList` 重新探测编码并重新择优目录规则。
 *
 * Folio 的失效判定不在这里：章节列表以源文件指纹（size|lastModified）为键缓存在
 * `ReaderCache`，指纹变了就整体重解析；重解析时构造的是**全新的本类实例**
 * （`charset == null`、`tocUrl == ""`），因此 `TextFile.getChapterList` 中
 * `book.charset == null || book.tocUrl.isBlank() || modified` 这一条件依然为真，
 * 编码探测与规则择优照常执行——行为与 legado 的 `modified = true` 等价。
 * 故此处恒返回 false，避免引入 legado 的 `latestChapterTime` 持久化字段。
 */
fun Book.isLocalModified(): Boolean = false

/**
 * legado 用它把文件格式/大小/字数写回 `book.kind`，供书架分类展示；返回 `Unit`，
 * 且 `TextFile` 不读取其结果。Folio 书架不展示这类富化信息，故为空实现。
 */
@Suppress("UNUSED_PARAMETER")
fun Book.upKind() = Unit
