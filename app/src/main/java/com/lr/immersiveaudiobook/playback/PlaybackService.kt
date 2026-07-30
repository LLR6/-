package com.lr.immersiveaudiobook.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.lr.immersiveaudiobook.LrAudiobookApplication
import com.lr.immersiveaudiobook.MainActivity
import com.lr.immersiveaudiobook.R
import com.lr.immersiveaudiobook.data.local.NovelEntity
import com.lr.immersiveaudiobook.data.local.SentenceEntity
import com.lr.immersiveaudiobook.data.settings.AppSettings
import com.lr.immersiveaudiobook.tts.SystemTtsEngine
import com.lr.immersiveaudiobook.tts.TtsEngine
import com.lr.immersiveaudiobook.tts.TtsEventListener
import com.lr.immersiveaudiobook.tts.TtsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class PlaybackService : Service(), TtsEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database by lazy {
        (application as LrAudiobookApplication).container.database
    }
    private lateinit var tts: TtsEngine
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest

    private var currentSentence: SentenceEntity? = null
    private var currentNovel: NovelEntity? = null
    private var pendingSentence: SentenceEntity? = null
    private var pendingPreviewVoice: String? = null
    private var previewUtteranceId: String? = null
    private var currentUtteranceId: String? = null
    private var isPlaying = false
    private var pausedByAudioFocus = false
    private var settings = AppSettings()
    private var sleepJob: Job? = null
    private var sleepDeadlineElapsed: Long? = null
    private var sleepVolumeFactor = 1f
    private var stopAfterChapterId: Long? = null
    private var remainingChapterTransitions: Int? = null
    private val commandToken = AtomicLong(0)

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pausePlayback()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByAudioFocus = isPlaying
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                sleepVolumeFactor = 0.22f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                sleepVolumeFactor = 1f
                if (pausedByAudioFocus && settings.resumeAfterInterruption) {
                    pausedByAudioFocus = false
                    currentSentence?.let(::speak)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(true)
            .build()
        mediaSession = MediaSessionCompat(this, "LR-Audiobook").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    currentSentence?.let(::speak)
                }

                override fun onPause() = pausePlayback()
                override fun onStop() = stopPlayback(removeNotification = true)
                override fun onSkipToNext() = moveNext()
                override fun onSkipToPrevious() = movePrevious()
            })
            isActive = true
        }
        tts = SystemTtsEngine(this, this)
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            (application as LrAudiobookApplication).container.settings.settings.collectLatest {
                settings = it
                tts.selectVoice(it.preferredVoiceName)
            }
        }
        updatePlaybackState(PlaybackStateCompat.STATE_NONE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                val sentenceId = intent.getLongExtra(EXTRA_SENTENCE_ID, -1L)
                if (sentenceId > 0) loadAndPlay(sentenceId)
                else currentSentence?.let(::speak)
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> moveNext()
            ACTION_PREVIOUS -> movePrevious()
            ACTION_STOP -> stopPlayback(removeNotification = true)
            ACTION_SET_TIMER -> configureTimer(intent)
            ACTION_STOP_AFTER_CHAPTER -> {
                stopAfterChapterId = currentSentence?.chapterId
                updateNotification()
            }
            ACTION_STOP_AFTER_CHAPTERS -> {
                remainingChapterTransitions =
                    intent.getIntExtra(EXTRA_CHAPTER_COUNT, 1).coerceAtLeast(1)
                updateNotification()
            }
            ACTION_CANCEL_TIMER -> cancelTimer()
            ACTION_PREVIEW_VOICE -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                val voiceName = intent.getStringExtra(EXTRA_VOICE_NAME).orEmpty()
                if (tts.isReady.value) speakPreview(voiceName) else pendingPreviewVoice = voiceName
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadAndPlay(sentenceId: Long) {
        val token = commandToken.incrementAndGet()
        scope.launch {
            val sentence = withContext(Dispatchers.IO) { database.sentenceDao().get(sentenceId) }
            if (token != commandToken.get()) return@launch
            if (sentence == null) {
                reportError("找不到朗读位置")
                return@launch
            }
            currentSentence = sentence
            currentNovel = withContext(Dispatchers.IO) { database.novelDao().get(sentence.novelId) }
            speak(sentence)
        }
    }

    private fun speak(sentence: SentenceEntity) {
        currentSentence = sentence
        if (!tts.isReady.value) {
            pendingSentence = sentence
            PlaybackBus.update { it.copy(error = "正在初始化系统语音…") }
            return
        }
        if (audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            reportError("无法获得音频播放权限")
            return
        }
        val novel = currentNovel
        val baseRate = novel?.speechRate ?: settings.speechRate
        val basePitch = novel?.pitch ?: settings.pitch
        val baseVolume = novel?.volume ?: settings.volume
        val utteranceId = "sentence:${sentence.id}:${System.nanoTime()}"
        currentUtteranceId = utteranceId
        val request = TtsRequest(
            utteranceId = utteranceId,
            text = sentence.displayText,
            rate = baseRate * emotionRate(sentence.emotion) * roleRate(sentence.characterName),
            pitch = basePitch * rolePitch(sentence.characterName),
            volume = baseVolume * sleepVolumeFactor,
            characterName = sentence.characterName
        )
        if (!tts.speak(request)) {
            reportError("系统语音暂时不可用，请检查是否安装中文语音包")
        } else {
            isPlaying = true
            updateMetadata(sentence)
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            PlaybackBus.update {
                it.copy(
                    sentenceId = sentence.id,
                    novelId = sentence.novelId,
                    chapterId = sentence.chapterId,
                    isPlaying = true,
                    currentText = sentence.displayText,
                    characterName = sentence.characterName,
                    emotion = sentence.emotion,
                    error = null
                )
            }
            persistProgress(sentence)
            updateNotification()
        }
    }

    private fun pausePlayback() {
        if (!::tts.isInitialized) return
        tts.stop()
        isPlaying = false
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        PlaybackBus.update { it.copy(isPlaying = false) }
        updateNotification()
    }

    private fun speakPreview(voiceName: String) {
        tts.stop()
        isPlaying = false
        PlaybackBus.update { it.copy(isPlaying = false, error = null) }
        tts.selectVoice(voiceName)
        val utteranceId = "voice-preview:${System.nanoTime()}"
        previewUtteranceId = utteranceId
        currentUtteranceId = utteranceId
        val request = TtsRequest(
            utteranceId = utteranceId,
            text = "夜色沉了下来，远处传来一阵缓慢的脚步声。别回头，它就在你身后。",
            rate = 0.86f,
            pitch = 0.72f,
            volume = 1f,
            characterName = "旁白"
        )
        if (!tts.speak(request)) {
            reportError("该系统音色暂时无法试听")
        } else {
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            updateNotification()
        }
    }

    private fun stopPlayback(removeNotification: Boolean) {
        commandToken.incrementAndGet()
        if (::tts.isInitialized) tts.stop()
        isPlaying = false
        pendingSentence = null
        currentUtteranceId = null
        cancelTimer()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        PlaybackBus.update { it.copy(isPlaying = false, sleepRemainingMs = null) }
        if (removeNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification()
        }
    }

    private fun moveNext() {
        val current = currentSentence ?: return
        val token = commandToken.incrementAndGet()
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                database.sentenceDao().next(current.novelId, current.sequenceOrder)
            }
            if (token != commandToken.get()) return@launch
            if (next == null) {
                withContext(Dispatchers.IO) {
                    database.novelDao().updateProgress(
                        current.novelId,
                        current.id,
                        1f,
                        completed = true
                    )
                }
                stopPlayback(removeNotification = false)
            } else {
                val crossedChapter = next.chapterId != current.chapterId
                if (stopAfterChapterId == current.chapterId && crossedChapter) {
                    stopAfterChapterId = null
                    stopPlayback(removeNotification = false)
                    return@launch
                }
                if (crossedChapter) {
                    remainingChapterTransitions = remainingChapterTransitions?.minus(1)
                    if (remainingChapterTransitions == 0) {
                        remainingChapterTransitions = null
                        stopPlayback(removeNotification = false)
                        return@launch
                    }
                }
                currentSentence = next
                speak(next)
            }
        }
    }

    private fun movePrevious() {
        val current = currentSentence ?: return
        val token = commandToken.incrementAndGet()
        scope.launch {
            val previous = withContext(Dispatchers.IO) {
                database.sentenceDao().previous(current.novelId, current.sequenceOrder)
            } ?: current
            if (token == commandToken.get()) {
                currentSentence = previous
                speak(previous)
            }
        }
    }

    private fun persistProgress(sentence: SentenceEntity) {
        scope.launch(Dispatchers.IO) {
            val novel = database.novelDao().get(sentence.novelId) ?: return@launch
            val progress = if (novel.sentenceCount <= 0) 0f else
                ((sentence.sequenceOrder + 1f) / novel.sentenceCount).coerceIn(0f, 1f)
            database.novelDao().updateProgress(
                sentence.novelId,
                sentence.id,
                progress,
                completed = progress >= 1f
            )
        }
    }

    private fun configureTimer(intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val deadlineEpoch = intent.getLongExtra(EXTRA_DEADLINE_EPOCH_MS, 0)
        val durationMs = when {
            deadlineEpoch > System.currentTimeMillis() -> deadlineEpoch - System.currentTimeMillis()
            minutes > 0 -> minutes * 60_000L
            else -> 0L
        }
        if (durationMs <= 0) {
            cancelTimer()
            return
        }
        sleepJob?.cancel()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        sleepDeadlineElapsed = startedAt + durationMs
        sleepJob = scope.launch {
            while (true) {
                val remaining = (sleepDeadlineElapsed ?: startedAt) -
                    android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    stopPlayback(removeNotification = false)
                    break
                }
                sleepVolumeFactor = if (remaining <= 60_000L) {
                    (remaining / 60_000f).coerceIn(0.08f, 1f)
                } else {
                    1f
                }
                PlaybackBus.update { it.copy(sleepRemainingMs = remaining) }
                delay(1_000L)
            }
        }
        updateNotification()
    }

    private fun cancelTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepDeadlineElapsed = null
        sleepVolumeFactor = 1f
        PlaybackBus.update { it.copy(sleepRemainingMs = null) }
    }

    private fun updateMetadata(sentence: SentenceEntity) {
        scope.launch {
            val novel = currentNovel ?: withContext(Dispatchers.IO) {
                database.novelDao().get(sentence.novelId)
            }.also { currentNovel = it }
            val chapter = withContext(Dispatchers.IO) {
                database.chapterDao().get(sentence.chapterId)
            }
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, novel?.title ?: "有声小说")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, chapter?.title ?: "正在朗读")
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, sentence.characterName)
                    .build()
            )
            updateNotification()
        }
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_STOP
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val sentence = currentSentence
        val title = currentNovel?.title ?: "LR-沉浸式有声小说"
        val text = sentence?.displayText?.take(80) ?: "准备系统语音…"
        val activityIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val toggleIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val toggleLabel = if (isPlaying) "暂停" else "播放"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(
                listOfNotNull(
                    sentence?.characterName,
                    sentence?.emotion,
                    PlaybackBus.state.value.sleepRemainingMs?.let { "定时 ${formatDuration(it)}" }
                ).joinToString(" · ")
            )
            .setContentIntent(activityIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous,
                "上一句",
                servicePendingIntent(1, ACTION_PREVIOUS)
            )
            .addAction(toggleIcon, toggleLabel, servicePendingIntent(2, toggleAction))
            .addAction(
                android.R.drawable.ic_media_next,
                "下一句",
                servicePendingIntent(3, ACTION_NEXT)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                servicePendingIntent(4, ACTION_STOP)
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(servicePendingIntent(4, ACTION_STOP))
            )
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        currentSentence?.id?.let { intent.putExtra(EXTRA_SENTENCE_ID, it) }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun reportError(message: String) {
        isPlaying = false
        PlaybackBus.update { it.copy(isPlaying = false, error = message) }
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        updateNotification()
    }

    private fun emotionRate(emotion: String): Float = when (emotion) {
        "紧张", "急促", "激动", "大声喊叫" -> 1.09f
        "恐惧", "神秘", "压迫", "悲伤", "低声耳语" -> 0.88f
        else -> 1f
    }

    private fun rolePitch(name: String): Float = when {
        name == "旁白" -> 0.78f
        name.contains("怪物") -> 0.66f
        name.contains("神秘") -> 0.74f
        name.contains("老") -> 0.80f
        name.contains("女") || name.endsWith("娘") -> 1.30f
        name.contains("小") || name.contains("童") -> 1.46f
        name.contains("男") -> 0.88f
        else -> listOf(0.84f, 0.92f, 1.02f, 1.14f)[Math.floorMod(name.hashCode(), 4)]
    }

    private fun roleRate(name: String): Float = when {
        name == "旁白" -> 0.96f
        name.contains("老") || name.contains("神秘") -> 0.90f
        name.contains("儿童") -> 1.08f
        else -> listOf(0.94f, 1f, 1.06f)[Math.floorMod(name.hashCode(), 3)]
    }

    override fun onReady() {
        scope.launch {
            PlaybackBus.update { it.copy(error = null) }
            pendingPreviewVoice?.also {
                pendingPreviewVoice = null
                speakPreview(it)
                return@launch
            }
            pendingSentence?.also {
                pendingSentence = null
                speak(it)
            }
        }
    }

    override fun onStart(utteranceId: String) = Unit

    override fun onDone(utteranceId: String) {
        if (utteranceId == previewUtteranceId) {
            previewUtteranceId = null
            currentUtteranceId = null
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            if (currentSentence == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                updateNotification()
            }
            return
        }
        if (utteranceId != currentUtteranceId || !isPlaying) return
        val pause = currentSentence?.pauseAfterMs?.toLong() ?: 200L
        scope.launch {
            delay(pause)
            if (isPlaying && utteranceId == currentUtteranceId) moveNext()
        }
    }

    override fun onError(utteranceId: String, message: String) {
        scope.launch { reportError(message) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        sleepJob?.cancel()
        if (::tts.isInitialized) tts.shutdown()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::audioManager.isInitialized && ::audioFocusRequest.isInitialized) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.lr.immersiveaudiobook.action.PLAY"
        const val ACTION_PAUSE = "com.lr.immersiveaudiobook.action.PAUSE"
        const val ACTION_NEXT = "com.lr.immersiveaudiobook.action.NEXT"
        const val ACTION_PREVIOUS = "com.lr.immersiveaudiobook.action.PREVIOUS"
        const val ACTION_STOP = "com.lr.immersiveaudiobook.action.STOP"
        const val ACTION_SET_TIMER = "com.lr.immersiveaudiobook.action.SET_TIMER"
        const val ACTION_CANCEL_TIMER = "com.lr.immersiveaudiobook.action.CANCEL_TIMER"
        const val ACTION_STOP_AFTER_CHAPTER =
            "com.lr.immersiveaudiobook.action.STOP_AFTER_CHAPTER"
        const val ACTION_STOP_AFTER_CHAPTERS =
            "com.lr.immersiveaudiobook.action.STOP_AFTER_CHAPTERS"
        const val ACTION_PREVIEW_VOICE =
            "com.lr.immersiveaudiobook.action.PREVIEW_VOICE"
        const val EXTRA_SENTENCE_ID = "sentence_id"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_DEADLINE_EPOCH_MS = "deadline_epoch_ms"
        const val EXTRA_CHAPTER_COUNT = "chapter_count"
        const val EXTRA_VOICE_NAME = "voice_name"

        private const val CHANNEL_ID = "audiobook_playback"
        private const val NOTIFICATION_ID = 2408

        fun command(context: Context, action: String, sentenceId: Long? = null) {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            sentenceId?.let { intent.putExtra(EXTRA_SENTENCE_ID, it) }
            if (action == ACTION_PLAY) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun setTimer(context: Context, minutes: Int) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_SET_TIMER)
                    .putExtra(EXTRA_MINUTES, minutes)
            )
        }

        fun previewVoice(context: Context, voiceName: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PREVIEW_VOICE)
                    .putExtra(EXTRA_VOICE_NAME, voiceName)
            )
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }
    }
}
