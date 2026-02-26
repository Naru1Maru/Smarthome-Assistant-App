package com.local.smarthomeassistant.asr

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Vosk ASR wrapper for "push-to-talk" in MVP.
 *
 * Model must be placed in:
 *   app/src/main/assets/<modelAssetDir>/
 * Example:
 *   app/src/main/assets/models/vosk-model-small-ru-0.22/
 *
 * NOTE:
 * Vosk StorageService expects file `uuid` in the model root.
 */
class VoskAsrEngine(
    private val appContext: Context,
    private val modelAssetDir: String
) {

    private companion object {
        const val TAG = "VoskAsrEngine"
    }

    private var model: Model? = null
    private var currentRecognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null

    private var onPartialCb: ((String) -> Unit)? = null
    private var onFinalCb: ((String, Long) -> Unit)? = null
    private var onErrorCb: ((String) -> Unit)? = null
    private var onAudioLevelCb: ((Float) -> Unit)? = null

    private val prepared = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private var listenStartedAtMs: Long = 0
    private var lastLevelEmitMs: Long = 0

    fun prepareModelIfNeeded(onError: (String) -> Unit): Boolean {
        if (prepared.get()) return true

        Log.i(TAG, "prepareModelIfNeeded: assetDir='$modelAssetDir'")

        val latch = CountDownLatch(1)
        var ok = false

        StorageService.unpack(
            appContext,
            modelAssetDir,
            "vosk_model",
            { m ->
                model = m
                prepared.set(true)
                ok = true
                Log.i(TAG, "Vosk model unpacked OK")
                latch.countDown()
            },
            { e ->
                Log.e(TAG, "Vosk model unpack FAILED. assetDir='$modelAssetDir'", e)

                val detail = buildString {
                    append("Не удалось распаковать модель Vosk из assets: '$modelAssetDir'\n")
                    append("Проверьте: app/src/main/assets/$modelAssetDir\n")
                    append("Exception: ${e::class.java.name}: ${e.message}\n")
                    append(Log.getStackTraceString(e))
                }
                onError(detail)

                ok = false
                latch.countDown()
            }
        )

        latch.await()
        return ok
    }

    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String, Long) -> Unit,
        onError: (String) -> Unit,
        onAudioLevel: (Float) -> Unit
    ) {
        val m = model
        if (m == null) {
            onError("model not ready")
            return
        }

        stopListening(deliverFinal = false)

        onPartialCb = onPartial
        onFinalCb = onFinal
        onErrorCb = onError
        onAudioLevelCb = onAudioLevel

        val sampleRate = 16000
        currentRecognizer = Recognizer(m, sampleRate.toFloat())

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onError("AudioRecord buffer init failed")
            return
        }

        val bufferSize = minBuffer * 2
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("AudioRecord init failed")
            return
        }

        audioRecord = recorder
        listenStartedAtMs = SystemClock.elapsedRealtime()
        recording.set(true)
        lastLevelEmitMs = 0

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            recording.set(false)
            recorder.release()
            onError("AudioRecord start failed: ${e.message}")
            return
        }

        audioThread = thread(name = "VoskAudioThread") {
            processAudio(recorder, bufferSize)
        }
    }

    fun stopListening(deliverFinal: Boolean = true) {
        val wasRecording = recording.getAndSet(false)
        stopRecorder()
        joinAudioThread()

        val recognizer = currentRecognizer
        currentRecognizer = null

        if (deliverFinal && recognizer != null) {
            val elapsed = SystemClock.elapsedRealtime() - listenStartedAtMs
            val finalJson = try {
                recognizer.finalResult
            } catch (e: Exception) {
                Log.e(TAG, "finalResult error", e)
                ""
            }
            val text = safeExtractText(finalJson)
            onFinalCb?.invoke(text, elapsed)
        }

        recognizer?.close()
        onAudioLevelCb?.invoke(0f)
        if (!wasRecording && !deliverFinal) return
    }

    fun close() {
        stopListening()
        model?.close()
        model = null
    }

    private fun processAudio(recorder: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            while (recording.get()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                emitAudioLevel(buffer, read)

                val recognizer = currentRecognizer ?: continue
                val hasFinal = try {
                    recognizer.acceptWaveForm(buffer, read)
                } catch (e: Exception) {
                    Log.e(TAG, "acceptWaveForm failed", e)
                    onErrorCb?.invoke("ASR read error: ${e.message}")
                    false
                }

                if (hasFinal) {
                    val text = safeExtractText(recognizer.result)
                    finalizeFromThread(text)
                    break
                } else {
                    val partial = safeExtractPartial(recognizer.partialResult)
                    if (partial.isNotBlank()) onPartialCb?.invoke(partial)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processAudio loop error", e)
            onErrorCb?.invoke("Audio loop error: ${e.message}")
        } finally {
            stopRecorder()
        }
    }

    private fun finalizeFromThread(text: String) {
        if (!recording.getAndSet(false)) return
        val elapsed = SystemClock.elapsedRealtime() - listenStartedAtMs
        onFinalCb?.invoke(text, elapsed)
        currentRecognizer?.close()
        currentRecognizer = null
        onAudioLevelCb?.invoke(0f)
    }

    private fun stopRecorder() {
        audioRecord?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        audioRecord = null
    }

    private fun joinAudioThread() {
        val thread = audioThread ?: return
        if (thread !== Thread.currentThread()) {
            try {
                thread.join(200)
            } catch (_: InterruptedException) {
            }
        }
        audioThread = null
    }

    private fun emitAudioLevel(buffer: ByteArray, length: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLevelEmitMs < 50) return
        lastLevelEmitMs = now

        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return

        val rms = sqrt(sum / samples) / Short.MAX_VALUE
        onAudioLevelCb?.invoke(rms.toFloat().coerceIn(0f, 1f))
    }

    private fun safeExtractText(json: String): String =
        try { JSONObject(json).optString("text", "") } catch (_: Exception) { "" }

    private fun safeExtractPartial(json: String): String =
        try { JSONObject(json).optString("partial", "") } catch (_: Exception) { "" }
}
