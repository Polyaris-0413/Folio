package com.folio.read.ui.library

import com.folio.read.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** 书库空态 contextual bar 文案的场景映射 */
class LibraryEmptyStateTest {

    @Test
    fun `未选目录时提示尚未选择书架目录`() {
        assertEquals(R.string.library_dir_empty_title, libraryEmptyBarTextRes(hasLibraryDir = false))
    }

    @Test
    fun `已选目录但无候选书时提示当前目录暂无书籍`() {
        assertEquals(R.string.library_dir_no_books, libraryEmptyBarTextRes(hasLibraryDir = true))
    }
}
