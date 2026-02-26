package com.local.smarthomeassistant.net

data class Clarification(
    val needed: Boolean,
    val question: String,
    val options: List<String>
)

sealed class GatewayResult {
    data class Ok(
        val status: String,
        val sayText: String,
        val clarification: Clarification?,
        val contextUpdatesLastAreaName: String?
    ) : GatewayResult()

    data class Error(
        val code: Int?,
        val message: String
    ) : GatewayResult()
}

data class GatewayPingResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long?
)

data class GatewayCallResult(
    val result: GatewayResult,
    val rawRequest: String,
    val rawResponse: String
)
