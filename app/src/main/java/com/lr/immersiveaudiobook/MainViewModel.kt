package com.lr.immersiveaudiobook

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lr.immersiveaudiobook.data.importer.ImportSummary
import com.lr.immersiveaudiobook.data.local.CharacterCount
import com.lr.immersiveaudiobook.data.local.NovelEntity
import com.lr.immersiveaudiobook.data.local.SentenceEntity
import com.lr.immersiveaudiobook.data.settings.AppSettings
import com.lr.immersiveaudiobook.playback.PlaybackBus
import com.lr.immersiveaudiobook.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab(val label: String) {
    SHELF("书架"),
    PLAYER("播放器"),
    CHARACTERS("角色"),
    SETTINGS("设置")
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Running(val label: String) : ImportUiState
    data class Done(val message: String) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LrAudiobookApplication).container
    private val repository = container.novels

    val selectedTab = MutableStateFlow(AppTab.SHELF)
    val selectedNovelId = MutableStateFlow(0L)
    val selectedChapterId = MutableStateFlow(0L)
    val selectedSentenceId = MutableStateFlow<Long?>(null)
    val importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<SentenceEntity>>(emptyList())

    val novels = repository.novels.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val selectedNovel: StateFlow<NovelEntity?> = selectedNovelId
        .flatMapLatest { id -> if (id > 0) repository.novel(id) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters = selectedNovelId
        .flatMapLatest { id -> if (id > 0) repository.chapters(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sentences = selectedChapterId
        .flatMapLatest { id -> if (id > 0) repository.sentences(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val characterCounts: StateFlow<List<CharacterCount>> = selectedNovelId
        .flatMapLatest { id -> if (id > 0) repository.characterCounts(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    val playback = PlaybackBus.state

    init {
        viewModelScope.launch {
            novels.collect { items ->
                if (selectedNovelId.value == 0L && items.isNotEmpty()) {
                    selectNovel(items.first().id, openPlayer = false)
                }
            }
        }
        viewModelScope.launch {
            playback.collect { state ->
                state.novelId?.let { selectedNovelId.value = it }
                state.chapterId?.let { selectedChapterId.value = it }
                state.sentenceId?.let { selectedSentenceId.value = it }
            }
        }
    }

    fun selectNovel(novelId: Long, openPlayer: Boolean = true) {
        selectedNovelId.value = novelId
        if (openPlayer) selectedTab.value = AppTab.PLAYER
        viewModelScope.launch {
            val novel = repository.getNovel(novelId)
            val chapterList = repository.getChapters(novelId)
            val current = novel?.currentSentenceId?.let(repository::getSentence)
            selectedChapterId.value = current?.chapterId ?: chapterList.firstOrNull()?.id ?: 0L
            selectedSentenceId.value = current?.id ?: repository.firstSentence(novelId)?.id
        }
    }

    fun selectChapter(chapterId: Long) {
        selectedChapterId.value = chapterId
        viewModelScope.launch {
            selectedSentenceId.value = repository.getChapterSentences(chapterId).firstOrNull()?.id
        }
    }

    fun selectSentence(sentenceId: Long) {
        selectedSentenceId.value = sentenceId
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            importState.value = ImportUiState.Running("正在复制、检测编码并解析章节…")
            val outcome = runCatching { container.importer.importUri(uri) }
            outcome.onSuccess(::handleImportSummary).onFailure {
                importState.value = ImportUiState.Error(it.message ?: "导入失败")
            }
        }
    }

    fun createNovel(title: String, text: String) {
        viewModelScope.launch {
            importState.value = ImportUiState.Running("正在创建小说…")
            runCatching { container.importer.importManual(title, text) }
                .onSuccess { id ->
                    importState.value = ImportUiState.Done("创建完成")
                    selectNovel(id)
                }
                .onFailure {
                    importState.value = ImportUiState.Error(it.message ?: "创建失败")
                }
        }
    }

    private fun handleImportSummary(summary: ImportSummary) {
        val message = buildString {
            append("成功导入 ${summary.importedNovelIds.size} 本")
            if (summary.errors.isNotEmpty()) append("，${summary.errors.size} 项失败")
        }
        importState.value = if (summary.importedNovelIds.isNotEmpty()) {
            ImportUiState.Done(message)
        } else {
            ImportUiState.Error(summary.errors.joinToString("\n").ifBlank { "未导入任何小说" })
        }
        summary.importedNovelIds.firstOrNull()?.let { selectNovel(it, openPlayer = false) }
    }

    fun dismissImportMessage() {
        importState.value = ImportUiState.Idle
    }

    fun togglePlayback() {
        val context = getApplication<Application>()
        if (playback.value.isPlaying) {
            PlaybackService.command(context, PlaybackService.ACTION_PAUSE)
            return
        }
        viewModelScope.launch {
            val id = selectedSentenceId.value
                ?: selectedNovel.value?.currentSentenceId
                ?: repository.firstSentence(selectedNovelId.value)?.id
            if (id != null) PlaybackService.command(context, PlaybackService.ACTION_PLAY, id)
        }
    }

    fun playSentence(sentence: SentenceEntity) {
        selectedSentenceId.value = sentence.id
        PlaybackService.command(
            getApplication(),
            PlaybackService.ACTION_PLAY,
            sentence.id
        )
    }

    fun previous() = PlaybackService.command(getApplication(), PlaybackService.ACTION_PREVIOUS)
    fun next() = PlaybackService.command(getApplication(), PlaybackService.ACTION_NEXT)
    fun stop() = PlaybackService.command(getApplication(), PlaybackService.ACTION_STOP)
    fun setSleepTimer(minutes: Int) = PlaybackService.setTimer(getApplication(), minutes)
    fun cancelSleepTimer() =
        PlaybackService.command(getApplication(), PlaybackService.ACTION_CANCEL_TIMER)

    fun stopAfterChapter() =
        PlaybackService.command(getApplication(), PlaybackService.ACTION_STOP_AFTER_CHAPTER)

    fun updateVoice(rate: Float? = null, pitch: Float? = null, volume: Float? = null) {
        val novel = selectedNovel.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateVoice(
                novel.id,
                rate ?: novel.speechRate,
                pitch ?: novel.pitch,
                volume ?: novel.volume
            )
        }
    }

    fun updateSentence(sentence: SentenceEntity, character: String, emotion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSentence(
                sentence.copy(
                    characterName = character.ifBlank { "默认角色" },
                    emotion = emotion
                )
            )
        }
    }

    fun addBookmark(sentence: SentenceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBookmark(sentence.novelId, sentence.id)
        }
    }

    fun search(query: String) {
        searchQuery.value = query
        viewModelScope.launch {
            searchResults.value = if (query.isBlank() || selectedNovelId.value == 0L) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    repository.search(selectedNovelId.value, query)
                }
            }
        }
    }

    fun jumpToSearchResult(sentence: SentenceEntity) {
        selectedChapterId.value = sentence.chapterId
        selectedSentenceId.value = sentence.id
        searchQuery.value = ""
        searchResults.value = emptyList()
    }

    fun deleteNovel(novel: NovelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(novel)
            if (selectedNovelId.value == novel.id) {
                selectedNovelId.value = 0L
                selectedChapterId.value = 0L
                selectedSentenceId.value = null
            }
        }
    }

    fun setTheme(value: String) = updateSettings { setTheme(value) }
    fun setFontSize(value: Int) = updateSettings { setFontSize(value) }
    fun setLineSpacing(value: Float) = updateSettings { setLineSpacing(value) }
    fun setAutoScroll(value: Boolean) = updateSettings { setAutoScroll(value) }
    fun setResumeAfterInterruption(value: Boolean) =
        updateSettings { setResumeAfterInterruption(value) }

    fun setWifiOnlyCache(value: Boolean) = updateSettings { setWifiOnlyCache(value) }

    private fun updateSettings(
        block: com.lr.immersiveaudiobook.data.settings.SettingsRepository.MutablePreferencesEditor.() -> Unit
    ) {
        viewModelScope.launch {
            container.settings.update { editor -> editor.block() }
        }
    }

    fun cacheSizeBytes(): Long = container.audioCache.sizeBytes()

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) { container.audioCache.clear() }
    }
}
