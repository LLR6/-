package com.lr.immersiveaudiobook.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportState { IMPORTING, READY, ERROR }

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val description: String = "",
    val category: String = "未分类",
    val coverUri: String? = null,
    val tags: String = "",
    val sourcePath: String,
    val sourceEncoding: String,
    val importedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
    val currentSentenceId: Long? = null,
    val progress: Float = 0f,
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val chapterCount: Int = 0,
    val sentenceCount: Int = 0,
    val importState: ImportState = ImportState.IMPORTING,
    val importError: String? = null,
    val playbackPreset: String = "低沉悬疑",
    val speechRate: Float = 0.88f,
    val pitch: Float = 0.82f,
    val volume: Float = 1f
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId"), Index(value = ["novelId", "chapterIndex"], unique = true)]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val chapterIndex: Int,
    val title: String,
    val characterCount: Int,
    val firstSentenceSequence: Int,
    val lastSentenceSequence: Int
)

@Entity(
    tableName = "sentences",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("novelId"),
        Index("chapterId"),
        Index(value = ["novelId", "sequenceOrder"], unique = true)
    ]
)
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val chapterId: Long,
    val sequenceOrder: Int,
    val orderInChapter: Int,
    val text: String,
    val isDialogue: Boolean,
    val characterName: String = "旁白",
    val emotion: String = "平静",
    val pauseAfterMs: Int = 260,
    val emphasis: Float = 0f,
    val editedText: String? = null
) {
    val displayText: String get() = editedText ?: text
}

@Entity(
    tableName = "characters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId"), Index(value = ["novelId", "name"], unique = true)]
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val name: String,
    val gender: String = "未知",
    val ageGroup: String = "成年",
    val voiceId: String = "",
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = 1f,
    val isLocked: Boolean = false
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId"), Index("sentenceId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val sentenceId: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId"), Index("sentenceId")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val sentenceId: Long,
    val content: String,
    val colorArgb: Long = 0x66D4A45F,
    val createdAt: Long = System.currentTimeMillis()
)
