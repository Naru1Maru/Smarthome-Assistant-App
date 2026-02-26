package com.local.smarthomeassistant.net

import android.util.Log
import androidx.annotation.VisibleForTesting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class GatewayClient {

    private val client = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun ping(baseUrl: String, apiKey: String?): GatewayPingResult {
        val url = normalizeBaseUrl(baseUrl) + "/health"
        val safeKey = normalizeApiKey(apiKey.orEmpty())

        val builder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")

        if (safeKey.isNotEmpty()) {
            builder.header("X-API-Key", safeKey)
        }

        val req = builder.build()
        val t0 = System.currentTimeMillis()

        return try {
            client.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - t0
                val body = resp.body?.string().orEmpty()

                if (resp.isSuccessful) {
                    GatewayPingResult(
                        ok = true,
                        message = "Доступен (${resp.code})",
                        latencyMs = latency
                    )
                } else {
                    val detail = extractJsonDetail(body)
                    val msg = if (detail.isNotBlank()) detail else "HTTP ${resp.code}"
                    GatewayPingResult(
                        ok = false,
                        message = msg,
                        latencyMs = latency
                    )
                }
            }
        } catch (e: IOException) {
            GatewayPingResult(ok = false, message = "Network error: ${e.message}", latencyMs = null)
        } catch (e: Exception) {
            GatewayPingResult(ok = false, message = "Unknown error: ${e.message}", latencyMs = null)
        }
    }

    fun sendCommand(
        baseUrl: String,
        apiKey: String,
        text: String,
        parserMode: String,
        dryRun: Boolean,
        lastAreaName: String?
    ): GatewayCallResult {
        val url = normalizeBaseUrl(baseUrl) + "/v1/command"

        // Normalize API key:
        val safeKey = normalizeApiKey(apiKey)

        val dotCount = safeKey.count { it == '.' }
        Log.i(
            "GatewayClient",
            "sendCommand: url=$url key.safeLen=${safeKey.length} key.dotCount=$dotCount key.sha6=${sha256Prefix6(safeKey)}"
        )

        val bodyJson = JSONObject().apply {
            put("text", text)
            put("parser_mode", parserMode)
            put("dry_run", dryRun)
            if (!lastAreaName.isNullOrBlank()) {
                put("context", JSONObject().apply { put("last_area_name", lastAreaName) })
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val bodyText = bodyJson.toString()
        val body = bodyText.toRequestBody(mediaType)

        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        val headerAdded = safeKey.isNotEmpty()
        if (headerAdded) {
            builder.header("X-API-Key", safeKey)
        }

        Log.i("GatewayClient", "request: header.X-API-Key.added=$headerAdded body.len=${bodyJson.toString().length}")

        val req = builder.build()

        return try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val respBody = resp.body?.string().orEmpty()

                Log.i("GatewayClient", "response: code=$code body.len=${respBody.length}")

                if (!resp.isSuccessful) {
                    val detail = extractJsonDetail(respBody)
                    val msg = when (code) {
                        401 -> "HTTP 401: Invalid or missing X-API-Key"
                        403 -> "HTTP 403: Forbidden"
                        400 -> "HTTP 400: Bad Request"
                        else -> "HTTP $code"
                    }

                    val msgWithDetail = if (detail.isNotBlank()) "$msg. detail=$detail" else msg
                    val bodyPreview = respBody.take(300).replace("\n", " ").replace("\r", " ")
                    val finalMsg = if (bodyPreview.isNotBlank() && detail.isBlank()) {
                        "$msgWithDetail. body=$bodyPreview"
                    } else msgWithDetail

                    val error = GatewayResult.Error(code = code, message = finalMsg)
                    return GatewayCallResult(error, bodyText, respBody)
                }

                val ok = parseOk(respBody)
                GatewayCallResult(ok, bodyText, respBody)
            }
        } catch (e: IOException) {
            GatewayCallResult(
                GatewayResult.Error(code = null, message = "Network error: ${e.message}"),
                bodyText,
                e.message.orEmpty()
            )
        } catch (e: Exception) {
            GatewayCallResult(
                GatewayResult.Error(code = null, message = "Parse/unknown error: ${e.message}"),
                bodyText,
                e.message.orEmpty()
            )
        }
    }

    private fun parseOk(jsonText: String): GatewayResult.Ok = parseGatewayOk(jsonText)

    private fun sha256Prefix6(s: String): String {
        if (s.isBlank()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(s.toByteArray(Charsets.UTF_8))
        return dig.take(6).joinToString("") { "%02x".format(it) }
    }
}

internal fun normalizeBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().removeSuffix("/")
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
}

internal fun extractJsonDetail(body: String): String {
    if (body.isBlank()) return ""
    return try {
        JSONObject(body).optString("detail", "")
    } catch (_: Exception) {
        ""
    }
}

@VisibleForTesting
internal fun normalizeApiKey(raw: String): String {
    val trimmed = raw.trim()
    val withoutPrefix = if (trimmed.contains(":") &&
        trimmed.substringBefore(":").contains("X-API-Key", ignoreCase = true)
    ) {
        trimmed.substringAfter(":").trim()
    } else {
        trimmed
    }

    val noWs = withoutPrefix.replace(Regex("\\s+"), "")

    return noWs.filter { ch ->
        ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_'
    }
}

@VisibleForTesting
internal fun parseGatewayOk(jsonText: String): GatewayResult.Ok {
    val root = JSONObject(jsonText)

    val status = root.optString("status", "EXECUTED")
    val say = root.optString("say_text", "")

    val clarObj = root.optJSONObject("clarification")
    val clarification = if (clarObj != null && clarObj.optBoolean("needed", false)) {
        val question = clarObj.optString("question", "")
        val optionsJson = clarObj.optJSONArray("options") ?: JSONArray()
        val options = buildList {
            for (i in 0 until optionsJson.length()) add(optionsJson.optString(i))
        }
        Clarification(needed = true, question = question, options = options)
    } else null

    var lastArea: String? = null
    val validated = root.optJSONObject("validated_command")
    val normalized = validated?.optJSONObject("normalized")
    val ctxUpdates = normalized?.optJSONObject("context_updates")
    if (ctxUpdates != null) {
        lastArea = ctxUpdates.optString("last_area_name", "").ifBlank { null }
    }

    return GatewayResult.Ok(
        status = status,
        sayText = say,
        clarification = clarification,
        contextUpdatesLastAreaName = lastArea
    )
}
