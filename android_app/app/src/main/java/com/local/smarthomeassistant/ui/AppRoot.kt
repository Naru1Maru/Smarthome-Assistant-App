@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.local.smarthomeassistant.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.smarthomeassistant.LogEntry
import com.local.smarthomeassistant.LogKind
import com.local.smarthomeassistant.MainViewModel
import com.local.smarthomeassistant.RecentCommand
import com.local.smarthomeassistant.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppTab(
    val title: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String
) {
    HOME(
        title = "Главная",
        label = "Главная",
        icon = Icons.Outlined.Home,
        contentDescription = "Главная страница"
    ),
    SETTINGS(
        title = "Настройки",
        label = "Настройки",
        icon = Icons.Outlined.Settings,
        contentDescription = "Настройки"
    ),
    LOGS(
        title = "Уведомления",
        label = "Уведомления",
        icon = Icons.Outlined.Notifications,
        contentDescription = "Журнал событий"
    )
}

private enum class LogFilter(val label: String) {
    ALL("Все"),
    ACTIONS("Действия"),
    ERRORS("Ошибки")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.ui.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTab.title) }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.contentDescription) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Crossfade(targetState = currentTab, label = "tab") { tab ->
                when (tab) {
                    AppTab.HOME -> HomeScreen(
                        state = state,
                        onStartListening = vm::startListening,
                        onStopListening = vm::stopListening,
                        onSendText = vm::sendDevText,
                        onClarification = vm::selectClarificationOption,
                        onPingGateway = vm::pingGateway,
                        onStopTts = vm::stopTts,
                        onResendCommand = vm::resendRecentCommand,
                        onDismissTips = vm::dismissTips
                    )

                    AppTab.SETTINGS -> SettingsScreen(
                        state = state,
                        onGatewayChange = vm::onGatewayUrlChanged,
                        onApiKeyChange = vm::onApiKeyChanged,
                        onDryRunChange = vm::onDryRunChanged,
                        onParserChange = vm::onParserModeChanged,
                        onSpeechRateChange = vm::onSpeechRateChanged,
                        onSpeechPitchChange = vm::onSpeechPitchChanged,
                        onRefreshDiagnostics = vm::refreshDiagnostics
                    )

                    AppTab.LOGS -> NotificationsScreen(
                        state = state,
                        onClear = vm::clearLogs,
                        onRefreshDiagnostics = vm::refreshDiagnostics
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSendText: (String) -> Unit,
    onClarification: (String) -> Unit,
    onPingGateway: () -> Unit,
    onStopTts: () -> Unit,
    onResendCommand: (String) -> Unit,
    onDismissTips: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (!state.tipsDismissed) {
            TipsCard(onDismiss = onDismissTips)
        }
        VoiceControlCard(
            state = state,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            onPingGateway = onPingGateway
        )
        DevTextSender(enabled = !state.busy, onSend = onSendText)
        ResponseCard(state = state, onStopTts = onStopTts)
        AnimatedVisibility(visible = state.clarificationQuestion.isNotBlank()) {
            ClarificationCard(state = state, onClarification = onClarification)
        }
        RecentCommandsCard(
            commands = state.recentCommands,
            onResend = onResendCommand
        )
    }
}


@Composable
private fun VoiceControlCard(
    state: UiState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onPingGateway: () -> Unit
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val networkStatus = state.networkStatus
    val timeFormatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    val timeLabel = remember(networkStatus.checkedAt) {
        if (networkStatus.checkedAt <= 0) "" else timeFormatter.format(Date(networkStatus.checkedAt))
    }
    val connectionColor = if (networkStatus.ok) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Нажмите, чтобы говорить",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                ListeningPulse(isActive = state.isListening)
                FilledIconButton(
                    onClick = {
                        if (state.isListening) onStopListening() else onStartListening()
                    },
                    enabled = !state.busy && state.voskReady,
                    modifier = Modifier
                        .size(88.dp)
                        .pointerInteropFilter { event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    if (!state.busy && state.voskReady) {
                                        onStartListening()
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    onStopListening()
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    val icon = if (state.isListening) Icons.Outlined.StopCircle else Icons.Outlined.Mic
                    Icon(icon, contentDescription = "Голосовое управление")
                }
            }
            LevelMeter(level = state.audioLevel, isActive = state.isListening)
            Text(
                text = if (state.isListening) "Идёт запись..." else "Ожидание команды",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (detailsExpanded) "Скрыть детали" else "Показать детали",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "Подробнее",
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (detailsExpanded) 180f else 0f
                        }
                    )
                }
            }
            AnimatedVisibility(visible = detailsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Связь и шлюз",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = networkStatus.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = connectionColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (networkStatus.latencyMs != null) {
                            Text(
                                text = "Задержка: ${networkStatus.latencyMs} мс",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (timeLabel.isNotBlank()) {
                            Text(
                                text = "Обновлено: $timeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onPingGateway,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Проверить соединение")
                        }
                    }
                    if (state.lastGatewayStatus.isNotBlank()) {
                        Text(
                            text = "Ответ шлюза: ${state.lastGatewayStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Диагностика · ASR=${state.asrMs}мс · NET=${state.netMs}мс · TOTAL=${state.totalMs}мс",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.lastError.isNotBlank()) {
                        Text(
                            text = "Последняя ошибка: ${state.lastError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningPulse(isActive: Boolean) {
    if (!isActive) return

    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(
        modifier = Modifier
            .size(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    )
}

@Composable
private fun LevelMeter(level: Float, isActive: Boolean) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) level else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "audioLevel"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { animatedLevel },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Text(
            text = "Уровень голоса",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DevTextSender(
    enabled: Boolean,
    onSend: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }

    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Текстовая проверка", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Введите текст") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotBlank()) {
                        onSend(trimmed)
                        text = ""
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отправить")
            }
        }
    }
}

@Composable
private fun ResponseCard(
    state: UiState,
    onStopTts: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Последний ответ", style = MaterialTheme.typography.titleMedium)
            Text("АСR: ${state.lastAsrText}")
            Text("Gateway: ${state.lastGatewayStatus}")
            Text(
                text = "Ответ: ${state.lastSayText}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (state.lastError.isNotBlank()) {
                Text(
                    text = "Ошибка: ${state.lastError}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.ttsSpeaking) {
                Button(
                    onClick = onStopTts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Остановить озвучку")
                }
            }
        }
    }
}

@Composable
private fun ClarificationCard(
    state: UiState,
    onClarification: (String) -> Unit
) {
    if (state.clarificationQuestion.isBlank()) return

    ElevatedCard(colors = CardDefaults.elevatedCardColors()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Нужно уточнение", style = MaterialTheme.typography.titleMedium)
            Text(state.clarificationQuestion)
            state.clarificationOptions.forEach { option ->
                Button(
                    onClick = { onClarification(option) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RecentCommandsCard(
    commands: List<RecentCommand>,
    onResend: (String) -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Недавние команды", style = MaterialTheme.typography.titleMedium)
            if (commands.isEmpty()) {
                Text(
                    text = "Здесь появятся успешно выполненные команды.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val timeFormatter = remember {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                }
                commands.take(5).forEach { cmd ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onResend(cmd.text) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(cmd.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            text = "${timeFormatter.format(Date(cmd.timestamp))} · ${cmd.status}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TipsCard(onDismiss: () -> Unit) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Советы по началу", style = MaterialTheme.typography.titleMedium)
            Text("• Удерживайте кнопку микрофона и говорите, когда подсветится индикатор.", style = MaterialTheme.typography.bodySmall)
            Text("• Нужна диагностика? Раскройте «Показать детали» под кнопкой, чтобы увидеть состояние связи.", style = MaterialTheme.typography.bodySmall)
            Text("• Потренируйтесь с текстовым вводом ниже, прежде чем отдавать команды голосом.", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Понятно")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    onGatewayChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDryRunChange: (Boolean) -> Unit,
    onParserChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechPitchChange: (Float) -> Unit,
    onRefreshDiagnostics: () -> Unit
) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        OutlinedTextField(
            value = state.gatewayUrl,
            onValueChange = onGatewayChange,
            label = { Text("Gateway URL") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )
        )
        var apiKeyVisible by rememberSaveable { mutableStateOf(false) }

        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("X-API-Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                val desc = if (apiKeyVisible) "Скрыть ключ" else "Показать ключ"
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(imageVector = icon, contentDescription = desc)
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Режим dry_run")
                Text(
                    text = "Команды не выполняются в Home Assistant",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.dryRun, onCheckedChange = onDryRunChange)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Parser mode", style = MaterialTheme.typography.titleMedium)
            val parserOptions = listOf(
                Triple("rules", "Правила", "Детерминированный парсер, максимум стабильности."),
                Triple("llm_safe", "LLM + правила", "LLM пытается разобрать команду, при ошибках fallback на правила."),
                Triple("llm", "Только LLM", "Экспериментально: чистый LLM без подстраховки.")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parserOptions.forEach { (value, label, desc) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = state.parserMode == value,
                            onClick = { onParserChange(value) },
                            label = { Text(label) }
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Настройки TTS", style = MaterialTheme.typography.titleMedium)
            SpeechSlider(
                label = "Скорость речи: ${"%.2f".format(state.speechRate)}x",
                value = state.speechRate,
                onChange = onSpeechRateChange
            )
            SpeechSlider(
                label = "Тон голоса: ${"%.2f".format(state.speechPitch)}x",
                value = state.speechPitch,
                onChange = onSpeechPitchChange
            )
        }

        OutlinedButton(
            onClick = { showDiagnostics = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Диагностика")
        }

        ElevatedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Последняя зона", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.lastAreaName.isBlank()) "Нет данных" else state.lastAreaName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Голосовой движок: ${if (state.voskReady) "готов" else "подготовка..."}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDiagnostics) {
            DiagnosticsDialog(
                state = state,
                onDismiss = { showDiagnostics = false },
                onRefresh = onRefreshDiagnostics
            )
        }
    }
}

@Composable
private fun NotificationsScreen(
    state: UiState,
    onClear: () -> Unit,
    onRefreshDiagnostics: () -> Unit
) {
    val logs = state.logs
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val filteredLogs = remember(logs, filter) {
        when (filter) {
            LogFilter.ALL -> logs
            LogFilter.ACTIONS -> logs.filter { it.kind == LogKind.ACTION }
            LogFilter.ERRORS -> logs.filter { it.kind == LogKind.ERROR }
        }
    }
    val displayLogs = remember(filteredLogs) { filteredLogs.asReversed() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Журнал действий", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClear, enabled = logs.isNotEmpty()) {
                Text("Очистить")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            LogFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.label) }
                )
            }
        }

        if (displayLogs.isEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Пока нет событий по выбранному фильтру.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayLogs) { entry ->
                    LogEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val formatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    val time = remember(entry.timestamp) { formatter.format(Date(entry.timestamp)) }
    val icon = when (entry.kind) {
        LogKind.ERROR -> Icons.Outlined.Warning
        LogKind.ACTION -> Icons.Outlined.PlayArrow
        LogKind.INFO -> Icons.Outlined.Info
    }
    val tint = when (entry.kind) {
        LogKind.ERROR -> MaterialTheme.colorScheme.error
        LogKind.ACTION -> MaterialTheme.colorScheme.primary
        LogKind.INFO -> MaterialTheme.colorScheme.secondary
    }

    ElevatedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = entry.kind.name,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DiagnosticBlock(
    title: String,
    value: String
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(value))
            }, enabled = value.isNotBlank()) {
                Text("Копировать")
            }
        }
        SelectionContainer {
            Text(
                text = if (value.isBlank()) "Нет данных" else value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiagnosticsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onRefresh(); onDismiss() }) { Text("Обновить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = { Text("Диагностика") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Файл логов: ${state.logFilePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DiagnosticBlock("Последние записи", state.logPreview)
                DiagnosticBlock("RAW запрос", state.lastGatewayRequestRaw)
                DiagnosticBlock("RAW ответ", state.lastGatewayResponseRaw)
            }
        }
    )
}

@Composable
private fun SpeechSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.5f..1.5f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
