package com.lr.immersiveaudiobook.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels ORDER BY lastPlayedAt DESC, importedAt DESC")
    fun observeAll(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id")
    fun observe(id: Long): Flow<NovelEntity?>

    @Query("SELECT * FROM novels WHERE id = :id")
    suspend fun get(id: Long): NovelEntity?

    @Insert
    suspend fun insert(novel: NovelEntity): Long

    @Update
    suspend fun update(novel: NovelEntity)

    @Query(
        """
        UPDATE novels
        SET importState = :state, chapterCount = :chapterCount, sentenceCount = :sentenceCount,
            importError = :error
        WHERE id = :novelId
        """
    )
    suspend fun finishImport(
        novelId: Long,
        state: ImportState,
        chapterCount: Int,
        sentenceCount: Int,
        error: String?
    )

    @Query(
        """
        UPDATE novels SET currentSentenceId = :sentenceId, progress = :progress,
            lastPlayedAt = :playedAt, isCompleted = :completed
        WHERE id = :novelId
        """
    )
    suspend fun updateProgress(
        novelId: Long,
        sentenceId: Long,
        progress: Float,
        completed: Boolean,
        playedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE novels SET speechRate = :rate, pitch = :pitch, volume = :volume WHERE id = :novelId"
    )
    suspend fun updateVoice(novelId: Long, rate: Float, pitch: Float, volume: Float)

    @Delete
    suspend fun delete(novel: NovelEntity)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex")
    fun observeForNovel(novelId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex")
    suspend fun getForNovel(novelId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: Long): ChapterEntity?

    @Insert
    suspend fun insert(chapter: ChapterEntity): Long

    @Update
    suspend fun update(chapter: ChapterEntity)
}

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE chapterId = :chapterId ORDER BY orderInChapter")
    fun observeForChapter(chapterId: Long): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE chapterId = :chapterId ORDER BY orderInChapter")
    suspend fun getForChapter(chapterId: Long): List<SentenceEntity>

    @Query("SELECT * FROM sentences WHERE id = :id")
    suspend fun get(id: Long): SentenceEntity?

    @Query(
        "SELECT * FROM sentences WHERE novelId = :novelId AND sequenceOrder > :sequence ORDER BY sequenceOrder LIMIT 1"
    )
    suspend fun next(novelId: Long, sequence: Int): SentenceEntity?

    @Query(
        "SELECT * FROM sentences WHERE novelId = :novelId AND sequenceOrder < :sequence ORDER BY sequenceOrder DESC LIMIT 1"
    )
    suspend fun previous(novelId: Long, sequence: Int): SentenceEntity?

    @Query("SELECT * FROM sentences WHERE novelId = :novelId ORDER BY sequenceOrder LIMIT 1")
    suspend fun first(novelId: Long): SentenceEntity?

    @Query(
        """
        SELECT * FROM sentences
        WHERE novelId = :novelId AND (text LIKE '%' || :query || '%' OR editedText LIKE '%' || :query || '%')
        ORDER BY sequenceOrder LIMIT :limit
        """
    )
    suspend fun search(novelId: Long, query: String, limit: Int = 200): List<SentenceEntity>

    @Query(
        """
        SELECT characterName, COUNT(*) AS count FROM sentences
        WHERE novelId = :novelId GROUP BY characterName ORDER BY count DESC
        """
    )
    fun observeCharacterCounts(novelId: Long): Flow<List<CharacterCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sentences: List<SentenceEntity>): List<Long>

    @Update
    suspend fun update(sentence: SentenceEntity)
}

data class CharacterCount(val characterName: String, val count: Int)

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE novelId = :novelId ORDER BY name")
    fun observeForNovel(novelId: Long): Flow<List<CharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(characters: List<CharacterEntity>)

    @Update
    suspend fun update(character: CharacterEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE novelId = :novelId ORDER BY createdAt DESC")
    fun observeForNovel(novelId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE novelId = :novelId ORDER BY createdAt DESC")
    fun observeForNovel(novelId: Long): Flow<List<AnnotationEntity>>

    @Insert
    suspend fun insert(annotation: AnnotationEntity): Long

    @Delete
    suspend fun delete(annotation: AnnotationEntity)
}
