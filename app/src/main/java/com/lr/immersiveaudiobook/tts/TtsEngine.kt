package com.lr.immersiveaudiobook.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale

const val AUTO_MALE_VOICE = "AUTO_MALE"

data class SystemVoiceOption(
    val name: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
    val quality: Int,
    val maleLikelihood: Int
) {
    val description: String
        get() = buildString {
            append(if (maleLikelihood > 0) "男声候选" else if (maleLikelihood < 0) "女声候选" else "性别未知")
            append(" · ")
            append(if (requiresNetwork) "联网" else "本地")
            append(" · ")
            append(localeTag)
        }
}

data class TtsRequest(
    val utteranceId: String,
    val text: String,
    val rate: Float,
    val pitch: Float,
    val volume: Float,
    val characterName: String = "旁白"
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
    fun selectVoice(voiceName: String)
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
    private var preferredVoiceName: String = AUTO_MALE_VOICE
    private var chineseVoices: List<Voice> = emptyList()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val localeResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                chineseVoices = SystemVoiceSelector.chineseVoices(engine)
                applyVoiceForRole("旁白")
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

    override fun selectVoice(voiceName: String) {
        preferredVoiceName = voiceName.ifBlank { AUTO_MALE_VOICE }
        applyVoiceForRole("旁白")
    }

    override fun speak(request: TtsRequest): Boolean {
        val engine = tts ?: return false
        if (!_ready.value) return false
        applyVoiceForRole(request.characterName)
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
        applyVoiceForRole(request.characterName)
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

    private fun applyVoiceForRole(characterName: String) {
        val engine = tts ?: return
        val voice = SystemVoiceSelector.voiceForRole(
            voices = chineseVoices,
            preferredVoiceName = preferredVoiceName,
            characterName = characterName
        ) ?: return
        runCatching { engine.voice = voice }
    }
}

object SystemVoiceSelector {
    private val maleTokens = listOf(
        "male", "man", "boy", "masculine", "yunxi", "yunjian", "yunyang",
        "zhiyuan", "kangkang", "xiaoyao", "xiaomo", "xiaochen", "xiaogang",
        "dahu", "laosun", "male_"
    )
    private val femaleTokens = listOf(
        "female", "woman", "girl", "feminine", "xiaoxiao", "xiaoyi", "xiaobei",
        "huihui", "yaoyao", "xiaomeng", "female_"
    )

    fun chineseVoices(engine: TextToSpeech): List<Voice> =
        runCatching {
            engine.voices.orEmpty()
                .filter { it.locale.language.equals("zh", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<Voice> { maleLikelihood(it.name) }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.name }
                )
        }.getOrDefault(emptyList())

    fun options(engine: TextToSpeech): List<SystemVoiceOption> =
        chineseVoices(engine).map { voice ->
            SystemVoiceOption(
                name = voice.name,
                localeTag = voice.locale.toLanguageTag(),
                requiresNetwork = voice.isNetworkConnectionRequired,
                quality = voice.quality,
                maleLikelihood = maleLikelihood(voice.name)
            )
        }

    fun voiceForRole(
        voices: List<Voice>,
        preferredVoiceName: String,
        characterName: String
    ): Voice? {
        if (voices.isEmpty()) return null
        val narrator = voices.firstOrNull { it.name == preferredVoiceName }
            ?: voices.maxWithOrNull(
                compareBy<Voice> { maleLikelihood(it.name) }
                    .thenBy { if (it.isNetworkConnectionRequired) 0 else 1 }
                    .thenBy { it.quality }
            )
            ?: voices.first()
        if (characterName == "旁白") return narrator

        val isFemaleRole = characterName.contains("女") ||
            characterName.endsWith("娘") ||
            characterName.contains("儿童")
        val roleCandidates = if (isFemaleRole) {
            voices.filter { maleLikelihood(it.name) < 0 }
        } else {
            voices.filter { maleLikelihood(it.name) >= 0 }
        }.ifEmpty { listOf(narrator) }
        return roleCandidates[Math.floorMod(characterName.hashCode(), roleCandidates.size)]
    }

    fun maleLikelihood(voiceName: String): Int {
        val value = voiceName.lowercase(Locale.ROOT)
        return maleTokens.count(value::contains) - femaleTokens.count(value::contains)
    }
}

class SystemVoiceCatalog(
    context: Context,
    onLoaded: (List<SystemVoiceOption>, String?) -> Unit
) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                val options = SystemVoiceSelector.options(engine)
                onLoaded(
                    options,
                    if (options.isEmpty()) "设备没有可用的中文语音，请安装中文系统语音包" else null
                )
            } else {
                onLoaded(emptyList(), "无法读取系统语音列表")
            }
            engine?.shutdown()
            tts = null
        }
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
    override fun selectVoice(voiceName: String) = Unit
    override fun speak(request: TtsRequest): Boolean = false
    override fun synthesizeToFile(request: TtsRequest, destination: File): Boolean = false
    override fun stop() = Unit
    override fun shutdown() = Unit
}
