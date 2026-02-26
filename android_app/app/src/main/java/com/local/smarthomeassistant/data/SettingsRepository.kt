package com.local.smarthomeassistant.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val gatewayUrl: String,
    val apiKey: String,
    val lastAreaName: String,
    val speechRate: Float,
    val speechPitch: Float
)

class SettingsRepository(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("smarthome_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readFromPrefs())

    fun settingsFlow(): Flow<AppSettings> = state.asStateFlow()

    suspend fun setGatewayUrl(v: String) {
        prefs.edit().putString("gateway_url", v).apply()
        state.value = readFromPrefs()
    }

    suspend fun setApiKey(v: String) {
        prefs.edit().putString("api_key", v).apply()
        state.value = readFromPrefs()
    }

    suspend fun setLastAreaName(v: String) {
        prefs.edit().putString("last_area_name", v).apply()
        state.value = readFromPrefs()
    }

    suspend fun setSpeechRate(v: Float) {
        prefs.edit().putFloat("speech_rate", v).apply()
        state.value = readFromPrefs()
    }

    suspend fun setSpeechPitch(v: Float) {
        prefs.edit().putFloat("speech_pitch", v).apply()
        state.value = readFromPrefs()
    }

    private fun readFromPrefs(): AppSettings =
        AppSettings(
            gatewayUrl = prefs.getString("gateway_url", "") ?: "",
            apiKey = prefs.getString("api_key", "") ?: "",
            lastAreaName = prefs.getString("last_area_name", "") ?: "",
            speechRate = prefs.getFloat("speech_rate", 1.0f),
            speechPitch = prefs.getFloat("speech_pitch", 1.0f)
        )
}
