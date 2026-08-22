package com.folio.read.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书籍元数据;正文解析与章节结构后续扩展。
 * filePath 保存 SAF 原始 URI(可读形式);dedupKey 归一化后用于跨入口去重。
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["dedupKey"], unique = true)],
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** SAF 返回的原始 content URI(tree 前缀形式或直接 grant 形式),保持可读 */
    val filePath: String,
    /** 归一化去重键:URI 中 /document/ 之后的文档 ID,同一文件跨入口(手动/书库)一致 */
    val dedupKey: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    /** 旧阅读进度 0..1(章节模型前的字段,保留列兼容,不再写入) */
    val progress: Float = 0f,
    /** 阅读位置:章节号(章节模型按 ChapterDetector 块首划分) */
    val currentChapterIndex: Int = 0,
    /** 章内字符偏移(该页起始字符在章内的位置) */
    val chapterPosition: Int = 0,
)
