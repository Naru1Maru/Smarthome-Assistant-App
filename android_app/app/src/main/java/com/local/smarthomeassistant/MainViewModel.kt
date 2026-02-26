package com.local.smarthomeassistant

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.smarthomeassistant.asr.VoskAsrEngine
import com.local.smarthomeassistant.data.SettingsRepository
import com.local.smarthomeassistant.net.GatewayCallResult
import com.local.smarthomeassistant.net.GatewayClient
import com.local.smarthomeassistant.net.GatewayPingResult
import com.local.smarthomeassistant.net.GatewayResult
import com.local.smarthomeassistant.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogKind { INFO, ACTION, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val kind: LogKind = LogKind.INFO
)

data class RecentCommand(
    val text: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkStatusInfo(
    val label: String = "Не проверено",
    val ok: Boolean = false,
    val latencyMs: Long? = null,
    val checkedAt: Long = 0L
)

data class UiState(
    val gatewayUrl: String = "",
    val apiKey: String = "",
    val parserMode: String = "rules",
    val dryRun: Boolean = false,

    val voskReady: Boolean = false,

    val isListening: Boolean = false,
    val busy: Boolean = false,

    val lastAsrText: String = "",
    val lastGatewayStatus: String = "",
    val lastSayText: String = "",
    val lastError: String = "",

    val lastAreaName: String = "",

    val clarificationQuestion: String = "",
    val clarificationOptions: List<String> = emptyList(),
    val pendingOriginalText: String = "",

    val asrMs: Long = 0,
    val netMs: Long = 0,
    val totalMs: Long = 0,

    val audioLevel: Float = 0f,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val ttsSpeaking: Boolean = false,
    val networkStatus: NetworkStatusInfo = NetworkStatusInfo(),

    val lastGatewayRequestRaw: String = "",
    val lastGatewayResponseRaw: String = "",
    val logPreview: String = "",
    val logFilePath: String = "",

    val logs: List<LogEntry> = emptyList(),
    val tipsDismissed: Boolean = false,
    val recentCommands: List<RecentCommand> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        private const val SILENCE_TIMEOUT_MS = 2_500L
        private const val MAX_LISTENING_MS = 10_000L
        private const val AUDIO_ACTIVE_THRESHOLD = 0.08f
    }

    private val settings = SettingsRepository(app.applicationContext)
    private val gateway = GatewayClient()
    private val tts = TtsEngine(app.applicationContext)
    private val logFile = File(app.applicationContext.filesDir, "asr_diagnostics.log")
    private val recentCommandsFile = File(app.applicationContext.filesDir, "recent_commands.json")
    private val logFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val asr = VoskAsrEngine(
        appContext = app.applicationContext,
        modelAssetDir = "models/vosk-model-small-ru-0.22"
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var listenWatchdogJob: Job? = null
    private var listeningStartedAtMs: Long = 0
    private var lastVoiceAtMs: Long = 0

    init {
        _ui.value = _ui.value.copy(
            recentCommands = loadRecentCommands(),
            logFilePath = logFile.absolutePath
        )
        refreshLogPreview()
        tts.setOnSpeakingChanged { isSpeaking ->
            viewModelScope.launch {
                _ui.value = _ui.value.copy(ttsSpeaking = isSpeaking)
            }
        }

        viewModelScope.launch(Dispatchers.Main.immediate) {
            settings.settingsFlow().collect { s ->
                _ui.value = _ui.value.copy(
                    gatewayUrl = s.gatewayUrl,
                    apiKey = s.apiKey,
                    lastAreaName = s.lastAreaName,
                    speechRate = s.speechRate,
                    speechPitch = s.speechPitch
                )
                tts.setParams(s.speechRate, s.speechPitch)
            }
        }

        viewModelScope.launch {
            setBusy(true)
            val ok = withContext(Dispatchers.IO) {
                asr.prepareModelIfNeeded(onError = { msg -> setError(msg) })
            }
            _ui.value = _ui.value.copy(voskReady = ok)
            appendLog(if (ok) "Vosk модель готова к записи" else "Vosk модель не готова")
            setBusy(false)
        }
    }

    fun dismissTips() {
        if (!_ui.value.tipsDismissed) {
            _ui.value = _ui.value.copy(tipsDismissed = true)
        }
    }

    fun onGatewayUrlChanged(v: String) {
        val vv = v.trim()
        _ui.value = _ui.value.copy(gatewayUrl = vv)
        viewModelScope.launch { settings.setGatewayUrl(vv) }
    }

    fun onApiKeyChanged(v: String) {
        val vv = v.trim()
        _ui.value = _ui.value.copy(apiKey = vv)
        viewModelScope.launch { settings.setApiKey(vv) }
    }

    fun onDryRunChanged(v: Boolean) {
        _ui.value = _ui.value.copy(dryRun = v)
        appendLog("dry_run=${if (v) "ON" else "OFF"}", LogKind.ACTION)
    }

    fun onSpeechRateChanged(value: Float) {
        val clamped = value.coerceIn(0.5f, 1.5f)
        _ui.value = _ui.value.copy(speechRate = clamped)
        tts.setParams(clamped, _ui.value.speechPitch)
        viewModelScope.launch { settings.setSpeechRate(clamped) }
    }

    fun onSpeechPitchChanged(value: Float) {
        val clamped = value.coerceIn(0.5f, 1.5f)
        _ui.value = _ui.value.copy(speechPitch = clamped)
        tts.setParams(_ui.value.speechRate, clamped)
        viewModelScope.launch { settings.setSpeechPitch(clamped) }
    }

    fun stopTts() {
        tts.stop()
    }

    fun onParserModeChanged(mode: String) {
        val normalized = when (mode.trim().lowercase()) {
            "rules" -> "rules"
            "llm", "ml", "ai" -> "llm"
            "llm_safe", "safe" -> "llm_safe"
            else -> "rules"
        }
        if (_ui.value.parserMode == normalized) return
        _ui.value = _ui.value.copy(parserMode = normalized)
        appendLog("Parser mode -> $normalized", LogKind.ACTION)
    }

    fun pingGateway() {
        val state = _ui.value
        val base = state.gatewayUrl.trim()
        if (base.isBlank()) {
            setError("Не задан Gateway URL")
            return
        }
        viewModelScope.launch {
            updateNetworkStatus(
                ok = false,
                message = "Проверка соединения...",
                latencyMs = null
            )
            val result = withContext(Dispatchers.IO) {
                gateway.ping(base, state.apiKey.trim())
            }
            updateNetworkStatus(result.ok, result.message, result.latencyMs)
        }
    }

    fun resendRecentCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        appendLog("Повтор команды: $trimmed", LogKind.ACTION)
        sendText(trimmed, originalText = trimmed, asrMs = 0)
    }

    fun clearLogs() {
        _ui.value = _ui.value.copy(logs = emptyList())
    }

    fun startListening() {
        val state = _ui.value
        if (!state.voskReady || state.busy || state.isListening) return

        listeningStartedAtMs = SystemClock.elapsedRealtime()
        lastVoiceAtMs = listeningStartedAtMs

        _ui.value = state.copy(
            isListening = true,
            lastError = "",
            lastAsrText = "",
            audioLevel = 0f
        )
        appendLog("Голосовой режим: старт", LogKind.ACTION)
        startListenWatchdog()

        asr.startListening(
            onPartial = { /* ignore */ },
            onFinal = { text, asrMs ->
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(
                        isListening = false,
                        lastAsrText = text,
                        asrMs = asrMs
                    )
                    if (text.isNotBlank()) {
                        appendLog("ASR: $text", LogKind.ACTION)
                        sendText(text, originalText = text, asrMs = asrMs)
                    } else {
                        setError("ASR: пустой результат")
                    }
                }
            },
            onError = { msg ->
                _ui.value = _ui.value.copy(isListening = false)
                setError("ASR: $msg")
            },
            onAudioLevel = ::handleAudioLevel
        )
    }

    fun stopListening() {
        if (!_ui.value.isListening) return
        listenWatchdogJob?.cancel()
        listenWatchdogJob = null
        asr.stopListening()
        _ui.value = _ui.value.copy(isListening = false, audioLevel = 0f)
        appendLog("Голосовой режим: остановка", LogKind.ACTION)
    }

    fun toggleListening() {
        if (_ui.value.isListening) stopListening() else startListening()
    }

    fun sendDevText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        sendText(trimmed, originalText = trimmed, asrMs = 0)
    }

    fun selectClarificationOption(option: String) {
        val state = _ui.value
        if (state.pendingOriginalText.isBlank()) return
        val followup = "${state.pendingOriginalText} $option".trim()
        clearClarification()
        appendLog("Выбор уточнения: $option", LogKind.ACTION)
        sendText(followup, originalText = followup, asrMs = 0)
    }

    private fun clearClarification() {
        _ui.value = _ui.value.copy(
            clarificationQuestion = "",
            clarificationOptions = emptyList(),
            pendingOriginalText = ""
        )
    }

    private fun sendText(text: String, originalText: String, asrMs: Long) {
        val s = _ui.value
        val baseUrl = s.gatewayUrl.trim()
        val apiKey = s.apiKey.trim()

        if (baseUrl.isBlank()) {
            setError("Не задан Gateway URL")
            return
        }
        if (apiKey.isBlank()) {
            setError("Не задан X-API-Key")
            return
        }

        appendLog("Отправка команды: \"$text\"", LogKind.ACTION)

        viewModelScope.launch {
            setBusy(true)
            val t0 = System.currentTimeMillis()

            val envelope: GatewayCallResult = withContext(Dispatchers.IO) {
                gateway.sendCommand(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    text = text,
                    parserMode = s.parserMode,
                    dryRun = s.dryRun,
                    lastAreaName = s.lastAreaName.ifBlank { null }
                )
            }
            _ui.value = _ui.value.copy(
                lastGatewayRequestRaw = envelope.rawRequest,
                lastGatewayResponseRaw = envelope.rawResponse
            )
            val res = envelope.result

            val t1 = System.currentTimeMillis()
            val netMs = t1 - t0
            val totalMs = netMs + asrMs

            when (res) {
                is GatewayResult.Ok -> {
                    val say = res.sayText
                    val status = res.status

                    _ui.value = _ui.value.copy(
                        lastGatewayStatus = status,
                        lastSayText = say,
                        lastError = "",
                        netMs = netMs,
                        totalMs = totalMs
                    )

                    if (!res.contextUpdatesLastAreaName.isNullOrBlank()) {
                        settings.setLastAreaName(res.contextUpdatesLastAreaName)
                        _ui.value = _ui.value.copy(lastAreaName = res.contextUpdatesLastAreaName)
                    }

                    if (status == "NEEDS_CLARIFICATION" && res.clarification != null) {
                        _ui.value = _ui.value.copy(
                            clarificationQuestion = res.clarification.question,
                            clarificationOptions = res.clarification.options,
                            pendingOriginalText = originalText
                        )
                        appendLog("Нужно уточнение: ${res.clarification.question}", LogKind.ACTION)
                        if (res.clarification.question.isNotBlank()) {
                            tts.speak(res.clarification.question)
                        }
                    } else {
                        clearClarification()
                        if (say.isNotBlank()) tts.speak(say)
                    }

                    appendLog("Gateway: $status ${say.take(80)}", LogKind.INFO)
                    updateNetworkStatus(true, "OK: $status", netMs)
                    recordRecentCommand(originalText, status)
                }

                is GatewayResult.Error -> {
                    _ui.value = _ui.value.copy(
                        lastGatewayStatus = "ERROR",
                        lastSayText = "",
                        lastError = res.message,
                        netMs = netMs,
                        totalMs = totalMs
                    )
                    tts.speak("Ошибка. ${res.message}")
                    appendLog("Gateway ошибка: ${res.message}", LogKind.ERROR)
                    updateNetworkStatus(false, res.message, netMs)
                }
            }

            setBusy(false)
        }
    }

    private fun setBusy(v: Boolean) {
        _ui.value = _ui.value.copy(busy = v)
    }

    private fun setError(msg: String) {
        _ui.value = _ui.value.copy(lastError = msg)
        appendLog("Ошибка: $msg", LogKind.ERROR)
    }

    private fun appendLog(message: String, kind: LogKind = LogKind.INFO) {
        val entry = LogEntry(message = message, kind = kind)
        _ui.value = _ui.value.let { current ->
            val updated = (current.logs + entry).takeLast(50)
            current.copy(logs = updated)
        }
        persistLog(entry)
    }

    private fun persistLog(entry: LogEntry) {
        runCatching {
            val stamp = synchronized(logFormatter) {
                logFormatter.format(Date(entry.timestamp))
            }
            logFile.appendText("$stamp\t${entry.kind.name}\t${entry.message}\n")
        }.onFailure { e ->
            Log.e("MainViewModel", "Failed to write log file", e)
        }
        refreshLogPreview()
    }

    fun refreshDiagnostics() {
        refreshLogPreview()
    }

    private fun refreshLogPreview() {
        val preview = if (logFile.exists()) {
            runCatching {
                val lines = logFile.readLines()
                lines.takeLast(5).joinToString("\n")
            }.getOrDefault("")
        } else ""
        _ui.value = _ui.value.copy(logPreview = preview)
    }

    override fun onCleared() {
        super.onCleared()
        listenWatchdogJob?.cancel()
        listenWatchdogJob = null
        asr.close()
        tts.close()
    }

    private fun handleAudioLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        viewModelScope.launch {
            if (clamped >= AUDIO_ACTIVE_THRESHOLD) {
                lastVoiceAtMs = SystemClock.elapsedRealtime()
            }
            _ui.value = _ui.value.copy(audioLevel = clamped)
        }
    }

    private fun startListenWatchdog() {
        listenWatchdogJob?.cancel()
        val job = viewModelScope.launch {
            while (isActive) {
                delay(200)
                if (!_ui.value.isListening) break
                val now = SystemClock.elapsedRealtime()
                if (now - lastVoiceAtMs > SILENCE_TIMEOUT_MS) {
                    appendLog("Автоостановка: тишина", LogKind.ACTION)
                    stopListening()
                    break
                }
                if (now - listeningStartedAtMs > MAX_LISTENING_MS) {
                    appendLog("Автоостановка: лимит речи", LogKind.ACTION)
                    stopListening()
                    break
                }
            }
        }
        job.invokeOnCompletion { listenWatchdogJob = null }
        listenWatchdogJob = job
    }

    private fun updateNetworkStatus(ok: Boolean, message: String, latencyMs: Long?) {
        _ui.value = _ui.value.copy(
            networkStatus = NetworkStatusInfo(
                label = message,
                ok = ok,
                latencyMs = latencyMs,
                checkedAt = System.currentTimeMillis()
            )
        )
    }

    private fun recordRecentCommand(text: String, status: String) {
        val entry = RecentCommand(text = text, status = status)
        val updated = (listOf(entry) + _ui.value.recentCommands)
            .distinctBy { it.text }
            .take(10)
        _ui.value = _ui.value.copy(recentCommands = updated)
        persistRecentCommands(updated)
    }

    private fun loadRecentCommands(): List<RecentCommand> {
        if (!recentCommandsFile.exists()) return emptyList()
        return runCatching {
            val json = recentCommandsFile.readText()
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val text = obj.optString("text", "")
                    if (text.isBlank()) continue
                    add(
                        RecentCommand(
                            text = text,
                            status = obj.optString("status", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun persistRecentCommands(list: List<RecentCommand>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("text", entry.text)
                        put("status", entry.status)
                        put("timestamp", entry.timestamp)
                    }
                )
            }
            recentCommandsFile.writeText(arr.toString())
        }.onFailure { e ->
            Log.e("MainViewModel", "Failed to persist recent commands", e)
        }
    }
}
