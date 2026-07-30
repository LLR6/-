package com.lr.immersiveaudiobook.backup

import android.content.Context
import com.lr.immersiveaudiobook.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

interface BackupManager {
    suspend fun createBackup(output: OutputStream)
    suspend fun restoreBackup(input: InputStream)
}

class LocalBackupManager(
    private val context: Context,
    private val database: AppDatabase
) : BackupManager {
    override suspend fun createBackup(output: OutputStream) = withContext(Dispatchers.IO) {
        database.query("PRAGMA wal_checkpoint(FULL)", emptyArray()).close()
        ZipOutputStream(output.buffered()).use { zip ->
            val dbFile = context.getDatabasePath("lr_audiobook.db")
            if (dbFile.exists()) addFile(zip, dbFile, "database/lr_audiobook.db")
            val novelRoot = File(context.filesDir, "novels")
            if (novelRoot.exists()) {
                novelRoot.walkTopDown().filter(File::isFile).forEach { file ->
                    addFile(zip, file, "novels/${file.relativeTo(novelRoot).invariantSeparatorsPath}")
                }
            }
            zip.putNextEntry(ZipEntry("BACKUP_VERSION"))
            zip.write("1\n".toByteArray())
            zip.closeEntry()
        }
    }

    override suspend fun restoreBackup(input: InputStream) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val destination = File(staging, entry.name).canonicalFile
                require(destination.path.startsWith(staging.canonicalPath + File.separator)) {
                    "备份包路径不安全"
                }
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().buffered().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(File(staging, "BACKUP_VERSION").readText().trim() == "1") { "不支持的备份版本" }
        val restoredDb = File(staging, "database/lr_audiobook.db")
        require(restoredDb.exists()) { "备份中缺少数据库" }

        database.close()
        val targetDb = context.getDatabasePath("lr_audiobook.db")
        targetDb.parentFile?.mkdirs()
        restoredDb.copyTo(targetDb, overwrite = true)
        File(targetDb.path + "-wal").delete()
        File(targetDb.path + "-shm").delete()

        val restoredNovels = File(staging, "novels")
        if (restoredNovels.exists()) {
            val targetNovels = File(context.filesDir, "novels")
            targetNovels.deleteRecursively()
            restoredNovels.copyRecursively(targetNovels, overwrite = true)
        }
        staging.deleteRecursively()
    }

    private fun addFile(zip: ZipOutputStream, file: File, path: String) {
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
