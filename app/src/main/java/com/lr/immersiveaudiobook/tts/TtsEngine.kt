package com.lr.immersiveaudiobook.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale

data class TtsRequest(
    val utteranceId: String,
    val text: String,
    val rate: Float,
    val pitch: Float,
    val volume: Float
)

interface TtsEventListener {
    fun onReady()
    fun onStart(utteranceId: String)
    fun onDone(utteranceId: String)
    fun onError(utteranceId: String, message: String)
}

interface TtsEngine {
    val id: String
    val isReady: StateFlow<Boolean>
    fun speak(request: TtsRequest): Boolean
    fun synthesizeToFile(request: TtsRequest, destination: File): Boolean
    fun stop()
    fun shutdown()
}

class SystemTtsEngine(
    context: Context,
    private val listener: TtsEventListener
) : TtsEngine {
    override val id: String = "android-system"
    private val _ready = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _ready
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val localeResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = listener.onStart(utteranceId)
                    override fun onDone(utteranceId: String) = listener.onDone(utteranceId)
                    override fun onError(utteranceId: String) =
                        listener.onError(utteranceId, "系统语音合成失败")

                    override fun onError(utteranceId: String, errorCode: Int) =
                        listener.onError(utteranceId, "系统语音错误：$errorCode")
                })
                _ready.value = localeResult != TextToSpeech.LANG_MISSING_DATA &&
                    localeResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (_ready.value) listener.onReady()
                else listener.onError("init", "设备未安装可用的中文系统语音")
            } else {
                listener.onError("init", "系统语音引擎初始化失败")
            }
        }
    }

    override fun speak(request: TtsRequest): Boolean {
        val engine = tts ?: return false
        if (!_ready.value) return false
        engine.setSpeechRate(request.rate.coerceIn(0.35f, 2f))
        engine.setPitch(request.pitch.coerceIn(0.5f, 1.8f))
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request.volume.coerceIn(0f, 1f))
        }
        return engine.speak(
            request.text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            request.utteranceId
        ) == TextToSpeech.SUCCESS
    }

    override fun synthesizeToFile(request: TtsRequest, destination: File): Boolean {
        val engine = tts ?: return false
        if (!_ready.value) return false
        destination.parentFile?.mkdirs()
        engine.setSpeechRate(request.rate.coerceIn(0.35f, 2f))
        engine.setPitch(request.pitch.coerceIn(0.5f, 1.8f))
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request.volume.coerceIn(0f, 1f))
        }
        return engine.synthesizeToFile(
            request.text,
            params,
            destination,
            request.utteranceId
        ) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _ready.value = false
    }
}

interface CloudTtsEngine : TtsEngine {
    val requiresNetwork: Boolean
    val privacyNotice: String
}

class UnconfiguredCloudTtsEngine : CloudTtsEngine {
    override val id: String = "cloud-unconfigured"
    override val requiresNetwork: Boolean = true
    override val privacyNotice: String = "云端语音未配置；启用前必须征得同意，且密钥只能由安全后端保存。"
    override val isReady: StateFlow<Boolean> = MutableStateFlow(false)
    override fun speak(request: TtsRequest): Boolean = false
    override fun synthesizeToFile(request: TtsRequest, destination: File): Boolean = false
    override fun stop() = Unit
    override fun shutdown() = Unit
}
