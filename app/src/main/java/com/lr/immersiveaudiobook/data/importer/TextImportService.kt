package com.lr.immersiveaudiobook.data.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lr.immersiveaudiobook.data.local.AppDatabase
import com.lr.immersiveaudiobook.data.local.ImportState
import com.lr.immersiveaudiobook.data.local.NovelEntity
import com.lr.immersiveaudiobook.domain.NovelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ImportSummary(
    val importedNovelIds: List<Long>,
    val errors: List<String>
)

class TextImportService(
    private val context: Context,
    private val database: AppDatabase
) {
    private val parser = NovelParser(
        database.chapterDao(),
        database.sentenceDao(),
        database.characterDao()
    )

    fun hasPendingBundledNovels(): Boolean =
        !File(context.filesDir, "private_novels_v2.imported").exists() &&
            "private_novels.zip" in context.assets.list("").orEmpty()

    suspend fun importUri(
        uri: Uri,
        preferredEncoding: String = EncodingDetector.AUTO
    ): ImportSummary = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val displayName = queryName(uri) ?: "未命名小说.txt"
        if (displayName.endsWith(".zip", ignoreCase = true)) {
            importZip(uri, preferredEncoding)
        } else {
            val file = copyUriToPrivateFile(uri, displayName)
            val result = importPrivateTxt(
                file,
                displayName.removeSuffixIgnoreCase(".txt"),
                preferredEncoding
            )
            ImportSummary(listOfNotNull(result.first), listOfNotNull(result.second))
        }
    }

    /**
     * A private build may contain assets/private_novels.zip. Public builds intentionally omit it.
     * This keeps copyrighted user content out of the public source while allowing a personal APK.
     */
    suspend fun importBundledNovelsIfPresent(): ImportSummary? = withContext(Dispatchers.IO) {
        val marker = File(context.filesDir, "private_novels_v2.imported")
        if (marker.exists()) return@withContext null
        val bundled = context.assets.list("").orEmpty()
        if ("private_novels.zip" !in bundled) return@withContext null
        val result = context.assets.open("private_novels.zip").buffered().use { input ->
            importZipStream(input, EncodingDetector.AUTO)
        }
        if (result.importedNovelIds.isNotEmpty()) marker.writeText("ok")
        result
    }

    suspend fun importManual(title: String, text: String): Long = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "正文不能为空" }
        val safeTitle = title.trim().ifBlank { "手动创建-${System.currentTimeMillis()}" }
        val file = privateFile("${safeName(safeTitle)}.txt")
        file.writeText(text, Charsets.UTF_8)
        val (id, error) = importPrivateTxt(file, safeTitle, "UTF-8")
        if (id == null) throw IllegalStateException(error ?: "导入失败")
        id
    }

    private suspend fun importZip(uri: Uri, preferredEncoding: String): ImportSummary {
        return context.contentResolver.openInputStream(uri)?.buffered()?.use { raw ->
            importZipStream(raw, preferredEncoding)
        } ?: ImportSummary(emptyList(), listOf("无法读取 ZIP 文件"))
    }

    private suspend fun importZipStream(
        raw: InputStream,
        preferredEncoding: String
    ): ImportSummary {
        val ids = mutableListOf<Long>()
        val errors = mutableListOf<String>()
        ZipInputStream(raw).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".txt", ignoreCase = true)) {
                    val originalName = entry.name.substringAfterLast('/').ifBlank { "小说.txt" }
                    val destination = privateFile(originalName)
                    FileOutputStream(destination).buffered().use { output ->
                        zip.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                    val (id, error) = importPrivateTxt(
                        destination,
                        originalName.removeSuffixIgnoreCase(".txt"),
                        preferredEncoding
                    )
                    if (id != null) ids += id
                    if (error != null) errors += "$originalName：$error"
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (ids.isEmpty() && errors.isEmpty()) errors += "ZIP 中没有找到 TXT 文件"
        return ImportSummary(ids, errors)
    }

    private suspend fun importPrivateTxt(
        file: File,
        title: String,
        preferredEncoding: String
    ): Pair<Long?, String?> {
        val charset = EncodingDetector.detect(file, preferredEncoding)
        val novelId = database.novelDao().insert(
            NovelEntity(
                title = title.ifBlank { file.nameWithoutExtension },
                sourcePath = file.absolutePath,
                sourceEncoding = charset.name()
            )
        )
        return try {
            val result = parser.parse(novelId, file, charset)
            database.novelDao().finishImport(
                novelId = novelId,
                state = ImportState.READY,
                chapterCount = result.chapterCount,
                sentenceCount = result.sentenceCount,
                error = null
            )
            novelId to null
        } catch (error: Throwable) {
            database.novelDao().finishImport(
                novelId = novelId,
                state = ImportState.ERROR,
                chapterCount = 0,
                sentenceCount = 0,
                error = error.message?.take(300) ?: "未知解析错误"
            )
            null to (error.message ?: "解析失败")
        }
    }

    private fun copyUriToPrivateFile(uri: Uri, displayName: String): File {
        val destination = privateFile(displayName)
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            FileOutputStream(destination).buffered().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        } ?: error("无法打开所选文件")
        return destination
    }

    private fun privateFile(displayName: String): File {
        val directory = File(context.filesDir, "novels").apply { mkdirs() }
        val base = safeName(displayName.substringBeforeLast('.').take(80))
        val extension = displayName.substringAfterLast('.', "txt").lowercase()
        return File(directory, "${base}_${UUID.randomUUID()}.$extension")
    }

    private fun queryName(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return uri.lastPathSegment
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").ifBlank { "novel" }
}

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
