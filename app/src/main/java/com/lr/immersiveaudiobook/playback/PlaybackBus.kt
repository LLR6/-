package com.lr.immersiveaudiobook.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackUiState(
    val sentenceId: Long? = null,
    val novelId: Long? = null,
    val chapterId: Long? = null,
    val isPlaying: Boolean = false,
    val currentText: String = "",
    val characterName: String = "旁白",
    val emotion: String = "平静",
    val error: String? = null,
    val sleepRemainingMs: Long? = null
)

object PlaybackBus {
    private val mutable = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = mutable.asStateFlow()

    fun update(transform: (PlaybackUiState) -> PlaybackUiState) {
        mutable.value = transform(mutable.value)
    }
}
