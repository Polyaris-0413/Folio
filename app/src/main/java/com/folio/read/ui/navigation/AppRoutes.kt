package com.folio.read.ui.navigation

/**
 * 单 Activity 导航路由表(2.0.0)。
 * 命名统一 camelCase;带参数路由由 fun 生成。
 */
object AppRoutes {
    const val MAIN = "main"
    const val READER = "reader/{bookId}"
    const val LIBRARY_ADD = "libraryAdd"
    const val LICENSES = "licenses"

    const val ARG_BOOK_ID = "bookId"

    fun reader(bookId: Long) = "reader/$bookId"
}
