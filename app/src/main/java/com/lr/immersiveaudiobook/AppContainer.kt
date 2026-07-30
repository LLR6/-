package com.lr.immersiveaudiobook

import android.app.Application
import com.lr.immersiveaudiobook.cache.LocalAudioCacheManager
import com.lr.immersiveaudiobook.data.NovelRepository
import com.lr.immersiveaudiobook.data.importer.TextImportService
import com.lr.immersiveaudiobook.data.local.AppDatabase
import com.lr.immersiveaudiobook.data.settings.SettingsRepository

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.get(application)
    val novels = NovelRepository(database)
    val settings = SettingsRepository(application)
    val importer = TextImportService(application, database)
    val audioCache = LocalAudioCacheManager(application)
}

class LrAudiobookApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        lateinit var instance: LrAudiobookApplication
            private set
    }
}
