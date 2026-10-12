/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import moe.rukamori.archivetune.shared.LyricLine
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional translation fallback for providers that return lyrics without translations.
 * It is completely disabled unless ARCHIVETUNE_TRANSLATION_URL is explicitly configured.
 * A local/self-hosted LibreTranslate endpoint is supported to keep lyric text on the user's machine/LAN.
 */
internal object DesktopLyricsTranslator {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun enrich(lines: List<LyricLine>): List<LyricLine> {
        val config = configuration() ?: return lines
        val targets = lines.indices.filter { index ->
            lines[index].text.isNotBlank() && lines[index].translation.isNullOrBlank()
        }
        if (targets.isEmpty()) return lines
        val gate = Semaphore(4)
        val translated = coroutineScope {
            targets.map { index ->
                async(Dispatchers.IO) {
                    gate.withPermit { translateOne(lines[index].text, config.first, config.second, config.third) }
                }
            }.awaitAll()
        }
        return lines.toMutableList().apply {
            targets.zip(translated).forEach { (index, text) ->
                if (!text.isNullOrBlank()) this[index] = this[index].copy(translation = text)
            }
        }
    }

    private fun configuration(): Triple<String, String, String>? {
        val configuredUrl = System.getenv("ARCHIVETUNE_TRANSLATION_URL")?.trim().orEmpty()
        if (configuredUrl.isBlank()) return null
        val target = System.getenv("ARCHIVETUNE_TRANSLATION_TARGET")?.trim()?.takeIf { it.matches(Regex("[A-Za-z-]{2,16}")) } ?: "en"
        val apiKey = System.getenv("ARCHIVETUNE_TRANSLATION_API_KEY")?.trim().orEmpty()
        val endpoint = if (configuredUrl.endsWith("/translate", true)) configuredUrl else "${configuredUrl.trimEnd('/')}/translate"
        val uri = runCatching { URI.create(endpoint) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return Triple(endpoint, target, apiKey)
    }

    private fun translateOne(text: String, endpoint: String, target: String, apiKey: String): String? {
        if (text.length > 1_200) return null
        val key = "$endpoint\u0000$target\u0000$text"
        cache[key]?.let { return it }
        val payload = buildJsonObject {
            put("q", text)
            put("source", "auto")
            put("target", target)
            put("format", "text")
            if (apiKey.isNotBlank()) put("api_key", apiKey)
        }.toString()
        val connection = runCatching { URI.create(endpoint).toURL().openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return null
            val result = runCatching {
                json.parseToJsonElement(response).jsonObject["translatedText"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() && it != text }
            if (result != null) cache[key] = result
            result
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
