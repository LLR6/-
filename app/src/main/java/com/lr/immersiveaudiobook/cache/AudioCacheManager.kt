package com.lr.immersiveaudiobook.cache

import android.content.Context
import java.io.File

interface AudioCacheManager {
    fun fileFor(sentenceId: Long, engineId: String, rate: Float, pitch: Float): File
    fun sizeBytes(): Long
    fun clear(): Boolean
    fun deleteSentence(sentenceId: Long): Boolean
}

class LocalAudioCacheManager(context: Context) : AudioCacheManager {
    private val root = File(context.cacheDir, "tts_audio").apply { mkdirs() }

    override fun fileFor(sentenceId: Long, engineId: String, rate: Float, pitch: Float): File {
        val key = "${sentenceId}_${engineId}_${rate}_${pitch}".hashCode().toUInt().toString(16)
        return File(root, "${sentenceId}_$key.wav")
    }

    override fun sizeBytes(): Long =
        root.walkTopDown().filter(File::isFile).sumOf(File::length)

    override fun clear(): Boolean =
        root.listFiles()?.fold(true) { result, file -> file.deleteRecursively() && result } ?: true

    override fun deleteSentence(sentenceId: Long): Boolean =
        root.listFiles()
            ?.filter { it.name.startsWith(sentenceId.toString()) }
            ?.fold(true) { result, file -> file.delete() && result }
            ?: true
}
