package com.folio.read.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** dedupKey 唯一索引冲突时忽略,返回 -1 表示重复 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: Book): Long

    /** 最近阅读时间倒序:点开过的书提到最前,新加入(lastReadAt=addedAt)也在前 */
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): Book?

    @Query("UPDATE books SET currentChapterIndex = :chapterIndex, chapterPosition = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, chapterIndex: Int, position: Int)

    /** 点开书时刷新最近阅读时间(书架置顶) */
    @Query("UPDATE books SET lastReadAt = :timestamp WHERE id = :id")
    suspend fun updateLastReadAt(id: Long, timestamp: Long)

    @Query("UPDATE books SET filePath = :filePath WHERE id = :id")
    suspend fun updateFilePath(id: Long, filePath: String)

    @Query("UPDATE books SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}
