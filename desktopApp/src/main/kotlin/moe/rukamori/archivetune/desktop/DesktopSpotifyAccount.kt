/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Spotify user login via system browser + Authorization Code with PKCE; no embedded login or client secret. */
internal class DesktopSpotifyAccount {
    data class Status(val connected: Boolean = false, val displayName: String = "", val message: String = "Not connected")
    data class Playback(
        val activeDevice: Boolean = false,
        val isPlaying: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val deviceName: String = "",
        val progressMs: Long = 0L,
        val durationMs: Long = 0L,
        val message: String = "No active Spotify Connect playback",
    )
    private data class Token(val accessToken: String, val refreshToken: String, val expiresAt: Long)
    private data class Profile(val id: String, val displayName: String)

    private val dataDir = Paths.get(System.getProperty("user.home"), ".archivetune")
    private val tokenPath = dataDir.resolve("spotify-token.properties")
    private val redirectUri = "http://127.0.0.1:43821/callback"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var token: Token? = loadToken()
    @Volatile private var currentStatus: Status = Status()

    fun status(): Status = currentStatus

    suspend fun connect(clientIdRaw: String): Status = withContext(Dispatchers.IO) {
        val clientId = clientIdRaw.trim()
        require(clientId.isNotBlank() && clientId.length in 20..80) {
            "Enter a Spotify Developer app Client ID first."
        }
        val verifier = randomToken(48)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        val state = randomToken(24)
        val callback = CompletableFuture<Map<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 43821), 0)
        val callbackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "ArchiveTune-Spotify-callback").apply { isDaemon = true }
        }
        server.executor = callbackExecutor
        server.createContext("/callback") { exchange ->
            val query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val validState = query["state"] == state
            respond(exchange, if (validState && query["code"] != null) 200 else 400,
                if (validState && query["code"] != null) "Spotify connected. You can return to ArchiveTune." else "Spotify authorization failed. Return to ArchiveTune and retry.")
            if (validState) callback.complete(query) else callback.completeExceptionally(IllegalStateException("Spotify OAuth state validation failed"))
        }
        server.start()
        try {
            val authUri = URI.create("https://accounts.spotify.com/authorize?" + encodeParameters(linkedMapOf(
                "client_id" to clientId,
                "response_type" to "code",
                "redirect_uri" to redirectUri,
                "scope" to "user-read-private user-read-email user-read-playback-state user-read-currently-playing user-modify-playback-state",
                "state" to state,
                "code_challenge_method" to "S256",
                "code_challenge" to challenge,
            )))
            check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                "A desktop browser could not be opened. Open this URL manually: $authUri"
            }
            Desktop.getDesktop().browse(authUri)
            val params = callback.get(3, TimeUnit.MINUTES)
            if (params["error"] != null) throw IllegalStateException("Spotify authorization failed: ${params["error"]}")
            val code = params["code"] ?: error("Spotify returned no authorization code")
            val tokenProps = postForm("https://accounts.spotify.com/api/token", linkedMapOf(
                "client_id" to clientId,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
            ))
            val access = tokenProps["access_token"].orEmpty()
            check(access.isNotBlank()) { "Spotify returned an empty access token" }
            token = Token(access, tokenProps["refresh_token"].orEmpty(), System.currentTimeMillis() / 1000 + (tokenProps["expires_in"]?.toLongOrNull() ?: 3600))
            persistToken(clientId)
            val profile = fetchProfile(access)
            currentStatus = Status(true, profile.displayName.ifBlank { profile.id }, "Connected as ${profile.displayName.ifBlank { profile.id }}")
            currentStatus
        } catch (error: Throwable) {
            currentStatus = Status(false, "", error.message ?: "Spotify authorization failed")
            throw error
        } finally {
            server.stop(0)
            callbackExecutor.shutdownNow()
        }
    }

    suspend fun refreshStatus(): Status = withContext(Dispatchers.IO) {
        val loaded = token ?: run { currentStatus = Status(); return@withContext currentStatus }
        try {
            val access = if (loaded.expiresAt > System.currentTimeMillis() / 1000 + 30) loaded.accessToken else refreshToken(loaded)
            val profile = fetchProfile(access)
            currentStatus = Status(true, profile.displayName.ifBlank { profile.id }, "Connected as ${profile.displayName.ifBlank { profile.id }}")
        } catch (error: Throwable) {
            currentStatus = Status(false, "", "Saved Spotify authorization expired. Reconnect to continue.")
        }
        currentStatus
    }

    fun playback(): Playback {
        val access = currentAccessToken()
        val body = httpRequest("https://api.spotify.com/v1/me/player", "GET", null, mapOf("Authorization" to "Bearer $access"))
        if (body.isBlank()) return Playback(message = "No active Spotify Connect device. Open Spotify and start playback on a device first.")
        val root = json.parseToJsonElement(body).jsonObject
        val item = root["item"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val device = root["device"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val artist = item?.get("artists")?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { artistItem -> artistItem.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString(", ").orEmpty()
        val active = device?.get("id")?.jsonPrimitive?.contentOrNull != null
        val playing = root["is_playing"]?.jsonPrimitive?.contentOrNull?.equals("true", true) == true
        val title = item?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val album = item?.get("album")?.let { runCatching { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull() }.orEmpty()
        val progress = root["progress_ms"]?.jsonPrimitive?.longOrNull ?: 0L
        val duration = item?.get("duration_ms")?.jsonPrimitive?.longOrNull ?: 0L
        val deviceName = device?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        return Playback(
            activeDevice = active, isPlaying = playing, title = title, artist = artist, album = album,
            deviceName = deviceName, progressMs = progress, durationMs = duration,
            message = when {
                !active -> "No active Spotify Connect device. Open Spotify and select a playback device."
                title.isBlank() -> "Spotify is connected to $deviceName but no track is currently active."
                else -> "${if (playing) "Playing" else "Paused"} on $deviceName"
            },
        )
    }

    fun control(command: String) {
        val access = currentAccessToken()
        val current = if (command == "TOGGLE_PLAY_PAUSE") playback() else null
        val (method, endpoint) = when (command) {
            "NEXT" -> "POST" to "next"
            "PREVIOUS" -> "POST" to "previous"
            "TOGGLE_PLAY_PAUSE" -> if (current?.isPlaying == true) "PUT" to "pause" else "PUT" to "play"
            else -> error("Unsupported Spotify playback command")
        }
        httpRequest("https://api.spotify.com/v1/me/player/$endpoint", method, null, mapOf("Authorization" to "Bearer $access"))
    }

    private fun currentAccessToken(): String {
        val loaded = token ?: error("Connect your Spotify account first.")
        return if (loaded.expiresAt > System.currentTimeMillis() / 1000 + 30) loaded.accessToken else refreshToken(loaded)
    }

    fun disconnect() {
        token = null
        currentStatus = Status(false, "", "Disconnected")
        runCatching { Files.deleteIfExists(tokenPath) }
    }

    private fun refreshToken(old: Token): String {
        if (old.refreshToken.isBlank()) throw IllegalStateException("Spotify authorization must be renewed")
        val props = postForm("https://accounts.spotify.com/api/token", linkedMapOf(
            "client_id" to readClientId(), "grant_type" to "refresh_token", "refresh_token" to old.refreshToken,
        ))
        val next = Token(props["access_token"].orEmpty(), props["refresh_token"] ?: old.refreshToken,
            System.currentTimeMillis() / 1000 + (props["expires_in"]?.toLongOrNull() ?: 3600))
        token = next; persistToken(readClientId())
        return next.accessToken
    }

    private fun fetchProfile(access: String): Profile {
        val response = httpRequest("https://api.spotify.com/v1/me", "GET", null, mapOf("Authorization" to "Bearer $access"))
        val root = json.parseToJsonElement(response).jsonObject
        return Profile(root["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), root["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    private fun postForm(url: String, values: Map<String, String>): Map<String, String> {
        val result = httpRequest(url, "POST", encodeParameters(values), mapOf("Content-Type" to "application/x-www-form-urlencoded"))
        val parsed = json.parseToJsonElement(result).jsonObject
        return parsed.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull ?: value.toString().trim('"') }
    }

    private fun httpRequest(url: String, method: String, body: String?, headers: Map<String, String>): String {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) } }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Spotify request failed (HTTP $code): ${text.take(300)}")
            return text
        } finally { connection.disconnect() }
    }

    private fun persistToken(clientId: String) {
        runCatching {
            Files.createDirectories(dataDir)
            val props = Properties().apply {
                setProperty("clientId", clientId); setProperty("accessToken", token?.accessToken.orEmpty())
                setProperty("refreshToken", token?.refreshToken.orEmpty()); setProperty("expiresAt", token?.expiresAt.toString())
            }
            Files.newOutputStream(tokenPath).use { props.store(it, "ArchiveTune Spotify OAuth tokens - do not share") }
            runCatching { Files.setPosixFilePermissions(tokenPath, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
        }
    }

    private fun loadToken(): Token? = runCatching {
        if (!Files.isRegularFile(tokenPath)) return null
        val props = Properties(); Files.newInputStream(tokenPath).use(props::load)
        Token(props.getProperty("accessToken", ""), props.getProperty("refreshToken", ""), props.getProperty("expiresAt")?.toLongOrNull() ?: 0).takeIf { it.accessToken.isNotBlank() }
    }.getOrNull()

    private fun readClientId(): String = runCatching {
        val props = Properties(); Files.newInputStream(tokenPath).use(props::load); props.getProperty("clientId", "")
    }.getOrDefault("")

    private fun randomToken(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun encodeParameters(values: Map<String, String>): String = values.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }

    private fun parseQuery(raw: String): Map<String, String> = raw.split('&').mapNotNull { part ->
        val index = part.indexOf('='); if (index < 0) null else URLDecoder.decode(part.substring(0, index), "UTF-8") to URLDecoder.decode(part.substring(index + 1), "UTF-8")
    }.toMap()

    private fun respond(exchange: HttpExchange, status: Int, message: String) {
        val bytes = ("<!doctype html><meta charset='utf-8'><title>ArchiveTune Spotify</title><style>body{font:16px sans-serif;max-width:620px;margin:10vh auto;padding:0 24px;background:#111;color:#fff}h1{color:#ed5564}</style><h1>ArchiveTune</h1><p>$message</p><p>You may close this tab.</p>").toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
    }
}
