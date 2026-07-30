package com.lr.immersiveaudiobook.data

import com.lr.immersiveaudiobook.data.local.AnnotationEntity
import com.lr.immersiveaudiobook.data.local.AppDatabase
import com.lr.immersiveaudiobook.data.local.BookmarkEntity
import com.lr.immersiveaudiobook.data.local.ChapterEntity
import com.lr.immersiveaudiobook.data.local.CharacterCount
import com.lr.immersiveaudiobook.data.local.CharacterEntity
import com.lr.immersiveaudiobook.data.local.NovelEntity
import com.lr.immersiveaudiobook.data.local.SentenceEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class NovelRepository(private val database: AppDatabase) {
    val novels: Flow<List<NovelEntity>> = database.novelDao().observeAll()

    fun novel(id: Long): Flow<NovelEntity?> = database.novelDao().observe(id)
    fun chapters(novelId: Long): Flow<List<ChapterEntity>> =
        database.chapterDao().observeForNovel(novelId)

    fun sentences(chapterId: Long): Flow<List<SentenceEntity>> =
        database.sentenceDao().observeForChapter(chapterId)

    fun characterCounts(novelId: Long): Flow<List<CharacterCount>> =
        database.sentenceDao().observeCharacterCounts(novelId)

    fun characters(novelId: Long): Flow<List<CharacterEntity>> =
        database.characterDao().observeForNovel(novelId)

    suspend fun getNovel(id: Long): NovelEntity? = database.novelDao().get(id)
    suspend fun getSentence(id: Long): SentenceEntity? = database.sentenceDao().get(id)
    suspend fun firstSentence(novelId: Long): SentenceEntity? = database.sentenceDao().first(novelId)
    suspend fun getChapters(novelId: Long): List<ChapterEntity> =
        database.chapterDao().getForNovel(novelId)

    suspend fun getChapterSentences(chapterId: Long): List<SentenceEntity> =
        database.sentenceDao().getForChapter(chapterId)

    suspend fun search(novelId: Long, query: String): List<SentenceEntity> =
        database.sentenceDao().search(novelId, query.trim())

    suspend fun updateSentence(sentence: SentenceEntity) = database.sentenceDao().update(sentence)
    suspend fun updateCharacter(character: CharacterEntity) = database.characterDao().update(character)

    suspend fun updateVoice(novelId: Long, rate: Float, pitch: Float, volume: Float) =
        database.novelDao().updateVoice(novelId, rate, pitch, volume)

    suspend fun addBookmark(novelId: Long, sentenceId: Long, note: String = "") =
        database.bookmarkDao().insert(BookmarkEntity(novelId = novelId, sentenceId = sentenceId, note = note))

    suspend fun addAnnotation(novelId: Long, sentenceId: Long, content: String) =
        database.annotationDao().insert(
            AnnotationEntity(novelId = novelId, sentenceId = sentenceId, content = content)
        )

    suspend fun delete(novel: NovelEntity, deleteSource: Boolean = true) {
        database.novelDao().delete(novel)
        if (deleteSource) runCatching { File(novel.sourcePath).delete() }
    }
}
