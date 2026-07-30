package com.lr.immersiveaudiobook.cache

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lr.immersiveaudiobook.LrAudiobookApplication
import com.lr.immersiveaudiobook.tts.SystemTtsEngine
import com.lr.immersiveaudiobook.tts.TtsEngine
import com.lr.immersiveaudiobook.tts.TtsEventListener
import com.lr.immersiveaudiobook.tts.TtsRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TtsCacheWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val sentenceId = inputData.getLong(KEY_SENTENCE_ID, -1L)
        if (sentenceId <= 0) return Result.failure()
        val app = applicationContext as LrAudiobookApplication
        val sentence = app.container.database.sentenceDao().get(sentenceId) ?: return Result.failure()
        val novel = app.container.database.novelDao().get(sentence.novelId) ?: return Result.failure()
        val destination = app.container.audioCache.fileFor(
            sentenceId,
            "android-system",
            novel.speechRate,
            novel.pitch
        )
        if (destination.exists() && destination.length() > 44) {
            return Result.success(Data.Builder().putString(KEY_OUTPUT_PATH, destination.absolutePath).build())
        }
        return suspendCancellableCoroutine { continuation ->
            var engine: TtsEngine? = null
            var finished = false
            fun finish(result: Result) {
                if (finished) return
                finished = true
                engine?.shutdown()
                if (continuation.isActive) continuation.resume(result)
            }
            engine = SystemTtsEngine(applicationContext, object : TtsEventListener {
                override fun onReady() {
                    val request = TtsRequest(
                        utteranceId = "cache:$sentenceId",
                        text = sentence.displayText,
                        rate = novel.speechRate,
                        pitch = novel.pitch,
                        volume = novel.volume
                    )
                    if (engine?.synthesizeToFile(request, destination) != true) {
                        finish(Result.retry())
                    }
                }

                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    if (utteranceId == "cache:$sentenceId" && destination.length() > 44) {
                        finish(
                            Result.success(
                                Data.Builder()
                                    .putString(KEY_OUTPUT_PATH, destination.absolutePath)
                                    .build()
                            )
                        )
                    }
                }

                override fun onError(utteranceId: String, message: String) {
                    finish(if (runAttemptCount < 2) Result.retry() else Result.failure())
                }
            })
            continuation.invokeOnCancellation { engine?.shutdown() }
        }
    }

    companion object {
        const val KEY_SENTENCE_ID = "sentence_id"
        const val KEY_OUTPUT_PATH = "output_path"

        fun enqueue(context: Context, sentenceId: Long) {
            val request = OneTimeWorkRequestBuilder<TtsCacheWorker>()
                .setInputData(Data.Builder().putLong(KEY_SENTENCE_ID, sentenceId).build())
                .addTag("tts-cache")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "tts-cache-$sentenceId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
