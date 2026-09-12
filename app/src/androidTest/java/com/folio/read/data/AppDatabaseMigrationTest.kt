package com.folio.read.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库迁移测试:v8(上一个已发布版本的结构)→ 当前版本。
 *
 * 为什么必须有：这次改动让 `books` 多了一列、并新增了 TXT 目录规则表，用户升级时
 * 若迁移写错，最坏结果是书架数据丢失。编译期只能验证「实体与最新 schema 一致」，
 * 验证不了「旧库能被正确升级」，所以这里按 v8 的真实 schema 手工造一个库，
 * 用与线上完全相同的 [AppDatabase.ALL_MIGRATIONS] 升级，再断言数据仍在。
 *
 * 旧版 schema 从 androidTest 资产里的 Room 导出文件读取（见 app/build.gradle.kts 的
 * androidTest assets.srcDir），而不是手抄建表语句——手抄会随 schema 演进而失真。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private companion object {
        const val DB_NAME = "migration_v8_test.db"
        const val SCHEMA_ASSET = "com.folio.read.data.AppDatabase/8.json"

        /** v8 库里预置的一本书,迁移后必须原样还在 */
        const val BOOK_TITLE = "迁移测试书"
        const val BOOK_PATH = "content://test/document/migrate"
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteDb()
    }

    @After
    fun tearDown() {
        deleteDb()
    }

    private fun deleteDb() {
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(context.getDatabasePath(DB_NAME).path + suffix).delete()
        }
    }

    /** 按 Room 导出的 v8 schema 造一个 v8 数据库,并塞入一本书 */
    private fun createV8Database() {
        val schema = JSONObject(
            // 注意:ApplicationProvider 给的是「被测应用」的 Context,它看不到测试 APK 自身的 assets。
            // schema 被打进测试 APK,因此必须用 Instrumentation 的测试 Context 读取。
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(SCHEMA_ASSET).use { it.readBytes().decodeToString() },
        ).getJSONObject("database")

        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.use {
            schema.getJSONArray("entities").let { entities ->
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    it.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        it.execSQL(
                            indices.getJSONObject(j).getString("createSql")
                                .replace("\${TABLE_NAME}", entity.getString("tableName")),
                        )
                    }
                }
            }
            // room_master_table 必须带上 v8 的 identityHash,否则 Room 会认为这不是自己的库
            it.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            it.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
                arrayOf<Any>(schema.getString("identityHash")),
            )
            it.execSQL(
                "INSERT INTO books (id, title, filePath, dedupKey, addedAt, lastReadAt, progress, " +
                    "currentChapterIndex, chapterPosition) VALUES (1, ?, ?, ?, 100, 200, 0.0, 3, 7)",
                arrayOf<Any>(BOOK_TITLE, BOOK_PATH, "migrate"),
            )
            it.version = 8
        }
    }

    @Test
    fun `v8升级到当前版本后书籍数据保留且新增目录规则表`() {
        createV8Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val book = runBlocking(Dispatchers.IO) {
                // 触发打开(迁移在此发生);拿到书说明迁移链跑通且数据未丢
                db.openHelper.writableDatabase
                db.bookDao().getBook(1L)
            }
            assertTrue("迁移后书籍应仍存在", book != null)
            assertEquals(BOOK_TITLE, book!!.title)
            assertEquals(BOOK_PATH, book.filePath)
            assertEquals("阅读位置应保留", 3, book.currentChapterIndex)
            assertEquals("章内偏移应保留", 7, book.chapterPosition)
            assertEquals("新增的 tocRule 应回填空串(自动择优)", "", book.tocRule)

            val ruleCount = runBlocking(Dispatchers.IO) { db.txtTocRuleDao.count }
            assertEquals("新增规则表应为空表", 0, ruleCount)

            assertEquals("数据库版本应升到 10", 10, db.openHelper.writableDatabase.version)
        } finally {
            db.close()
        }
    }
}
