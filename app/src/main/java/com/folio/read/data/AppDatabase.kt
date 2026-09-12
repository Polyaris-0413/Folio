package com.folio.read.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.data.dao.TxtTocRuleDao
import io.legado.app.data.entities.TxtTocRule

@Database(
    entities = [Book::class, TxtTocRule::class],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    /**
     * TXT 目录规则表(自 legado 移植,见 io.legado.app 包下的说明)。
     * 声明为 val 而非函数:搬过来的 legado 代码以 `appDb.txtTocRuleDao` 属性形式访问。
     */
    abstract val txtTocRuleDao: TxtTocRuleDao

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

        /**
         * v7 → v8:新增 lastReadAt(最近阅读时间,书架置顶排序依据)。
         * 存量书回填为 addedAt(未读过的书按加入时间排)。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE books SET lastReadAt = addedAt")
            }
        }

        /**
         * v8 → v9:新增 TXT 目录规则表(自 legado 移植,规则库+择优引擎的存储)。
         * 建表语句取自 legado 的 Room schema(io.legado.app.data.AppDatabase/94.json 的
         * txtTocRules 定义),字段与约束保持一致,便于两侧规则相互导入。
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `txtTocRules` (`id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `rule` TEXT NOT NULL, `example` TEXT, " +
                        "`serialNumber` INTEGER NOT NULL, `enable` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * v9 → v10:books 新增 tocRule(记住本书选中的 TXT 目录正则,对应 legado 的 book.tocUrl)。
         * 存量行回填空串=自动择优,行为与升级前的单正则识别一致(下次打开时由引擎择优并写回)。
         * DEFAULT 的写法与既有 MIGRATION_7_8(lastReadAt) 保持一致。
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN tocRule TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 全部迁移,单独暴露给迁移测试:测试需要按历史 schema 手工构造旧版库,
         * 再用同一批迁移升到当前版本并校验(见 androidTest 的 AppDatabaseMigrationTest)。
         */
        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
        )

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "folio.db",
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
