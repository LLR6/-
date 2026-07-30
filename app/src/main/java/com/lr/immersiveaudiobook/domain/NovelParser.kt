package com.lr.immersiveaudiobook.domain

import com.lr.immersiveaudiobook.data.local.ChapterDao
import com.lr.immersiveaudiobook.data.local.ChapterEntity
import com.lr.immersiveaudiobook.data.local.CharacterDao
import com.lr.immersiveaudiobook.data.local.CharacterEntity
import com.lr.immersiveaudiobook.data.local.SentenceDao
import com.lr.immersiveaudiobook.data.local.SentenceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext

data class ParseResult(val chapterCount: Int, val sentenceCount: Int)

class NovelParser(
    private val chapterDao: ChapterDao,
    private val sentenceDao: SentenceDao,
    private val characterDao: CharacterDao
) {
    suspend fun parse(novelId: Long, file: File, charset: Charset): ParseResult =
        withContext(Dispatchers.IO) {
            var chapterIndex = 0
            var sequence = 0
            var title = "正文"
            val paragraphs = mutableListOf<String>()
            val characters = linkedSetOf("旁白", "默认角色")
            val characterResolver = CharacterResolver()

            suspend fun flushChapter() {
                if (paragraphs.isEmpty() && chapterIndex == 0 && title == "正文") return
                if (paragraphs.isEmpty() && chapterIndex > 0) return
                val analyzed = paragraphs
                    .asSequence()
                    .flatMap { SentenceSegmenter.segment(it, characterResolver).asSequence() }
                    .toList()
                    .ifEmpty {
                        listOf(
                            AnalyzedSentence(
                                text = "（本章节暂无正文）",
                                isDialogue = false,
                                characterName = "旁白",
                                emotion = "平静",
                                pauseAfterMs = 300
                            )
                        )
                    }
                val firstSequence = sequence
                val chapter = ChapterEntity(
                    novelId = novelId,
                    chapterIndex = chapterIndex,
                    title = title.ifBlank { "第${chapterIndex + 1}章" },
                    characterCount = paragraphs.sumOf(String::length),
                    firstSentenceSequence = firstSequence,
                    lastSentenceSequence = firstSequence + analyzed.lastIndex
                )
                val chapterId = chapterDao.insert(chapter)
                val rows = analyzed.mapIndexed { order, sentence ->
                    characters += sentence.characterName
                    SentenceEntity(
                        novelId = novelId,
                        chapterId = chapterId,
                        sequenceOrder = sequence++,
                        orderInChapter = order,
                        text = sentence.text,
                        isDialogue = sentence.isDialogue,
                        characterName = sentence.characterName,
                        emotion = sentence.emotion,
                        pauseAfterMs = sentence.pauseAfterMs
                    )
                }
                rows.chunked(500).forEach { sentenceDao.insertAll(it) }
                paragraphs.clear()
                chapterIndex++
            }

            file.bufferedReader(charset, 128 * 1024).useLines { lines ->
                lines.forEach { rawLine ->
                    coroutineContext.ensureActive()
                    val line = rawLine
                        .removePrefix("\uFEFF")
                        .replace("\u0000", "")
                        .replace('\u3000', ' ')
                        .trim()
                    if (line.isBlank()) return@forEach
                    if (ChapterDetector.isChapterTitle(line)) {
                        flushChapter()
                        title = line.take(80)
                    } else {
                        paragraphs += line
                    }
                }
            }
            flushChapter()
            characterDao.insertAll(
                characters.map { name ->
                    CharacterEntity(
                        novelId = novelId,
                        name = name,
                        gender = when {
                            name == "旁白" -> "男"
                            name.contains("女") -> "女"
                            name.contains("男") || name.contains("老") -> "男"
                            else -> "未知"
                        },
                        ageGroup = when {
                            name == "旁白" || name.contains("中年") -> "中年"
                            name.contains("老年") -> "老年"
                            name.contains("儿童") -> "儿童"
                            name.contains("青年") -> "青年"
                            else -> "成年"
                        },
                        pitch = when {
                            name == "旁白" -> 0.72f
                            name.contains("女") -> 1.15f
                            name.contains("儿童") -> 1.28f
                            name.contains("老") || name.contains("怪物") -> 0.75f
                            else -> 0.94f
                        },
                        speechRate = if (name == "旁白") 0.88f else 1f,
                        isLocked = name == "旁白"
                    )
                }
            )
            ParseResult(chapterCount = chapterIndex, sentenceCount = sequence)
        }
}
