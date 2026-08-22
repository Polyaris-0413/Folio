package com.folio.read.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 → v2:清理按 filePath 重复的历史数据(保留最新一条),并加唯一索引防重复添加。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM books WHERE id NOT IN (SELECT MAX(id) FROM books GROUP BY filePath)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_books_filePath ON books(filePath)",
                )
            }
        }

        /**
         * v2 → v3:把 tree 前缀的 filePath 归一化为规范 document 形式,
         * 使"手动添加"与"书库添加"的同一文件能正确去重;清理归一后产生的重复。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_books_filePath")
                db.execSQL(
                    "UPDATE books SET filePath = 'content://' || " +
                        "substr(filePath, 10, instr(substr(filePath, 10), '/') - 1) || " +
                        "'/document/' || substr(filePath, instr(filePath, '/document/') + 10) " +
                        "WHERE filePath LIKE '%/document/%'",
                )
                db.execSQL(
                    "DELETE FROM books WHERE id NOT IN (SELECT MAX(id) FROM books GROUP BY filePath)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_books_filePath ON books(filePath)",
                )
            }
        }

        /**
         * v3 → v4:去重键与可读 URI 分离。filePath 恢复为原始(可读)URI,
         * 新增 dedupKey 承担归一化去重;存量 dedupKey 由现有 filePath 的文档 ID 回填。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_books_filePath")
                db.execSQL("ALTER TABLE books ADD COLUMN dedupKey TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE books SET dedupKey = CASE " +
                        "WHEN instr(filePath, '/document/') > 0 " +
                        "THEN substr(filePath, instr(filePath, '/document/') + 10) " +
                        "ELSE filePath END",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_books_dedupKey ON books(dedupKey)",
                )
            }
        }

        /**
         * v4 → v5:曾创建章节表;字节偏移章节模型未实施,表从未写入,
         * 新装不再创建,存量表由 v6→v7 清除。迁移保留为空以满足版本链。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /**
         * v5 → v6:阅读位置从「全书偏移占比 Float」改为「章节号 + 章内字符偏移」。
         * 新增两列(默认 0 = 第 0 章开头);旧 progress 列保留在表中但实体不再使用。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN currentChapterIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN chapterPosition INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v6 → v7:删除从未写入的章节表(字节偏移章节模型未实施)。
         * 老版本创建的 book_chapters 一并清除;实体不再声明该表。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS book_chapters")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "folio.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
