/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToLong
import moe.rukamori.archivetune.shared.MusicRecognitionResult
import java.awt.EventQueue
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Desktop replacement for ShazamKit: Chromaprint (fpcalc) fingerprint + AcoustID/MusicBrainz metadata lookup. */
internal class DesktopMusicRecognition {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun chooseAudioFile(): File? {
        var chosen: File? = null
        val show = Runnable {
            val chooser = JFileChooser().apply {
                dialogTitle = "Identify a music file"
                isMultiSelectionEnabled = false
                fileFilter = FileNameExtensionFilter("Audio files", "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "aiff", "aif", "wma")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
        }
        if (EventQueue.isDispatchThread()) show.run() else EventQueue.invokeAndWait(show)
        return chosen
    }

    /**
     * Explicitly records a short microphone sample, fingerprints it locally, and deletes the temporary WAV.
     * The recorded audio itself is never uploaded; only the Chromaprint fingerprint and duration are sent.
     */
    fun recordAmbientAndIdentify(seconds: Int = 12): MusicRecognitionResult {
        require(seconds in 6..20) { "Ambient recognition duration must be between 6 and 20 seconds" }
        val format = listOf(44_100f, 48_000f, 22_050f, 16_000f)
            .map { rate -> AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, 1, 2, rate, false) }
            .firstOrNull { AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, it)) }
            ?: error("This computer does not expose a compatible microphone capture device. Choose a local audio file instead.")
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val line = AudioSystem.getLine(info) as TargetDataLine
        val captured = ByteArrayOutputStream(seconds * format.sampleRate.toInt() * format.frameSize)
        val scratch = ByteArray(8_192)
        try {
            line.open(format)
            line.start()
            val deadline = System.nanoTime() + seconds * 1_000_000_000L
            while (System.nanoTime() < deadline) {
                val remainingNanos = deadline - System.nanoTime()
                val desiredBytes = (remainingNanos * format.sampleRate.toLong() * format.frameSize.toLong() / 1_000_000_000L)
                    .coerceAtLeast(format.frameSize.toLong()).coerceAtMost(scratch.size.toLong()).toInt()
                val count = line.read(scratch, 0, desiredBytes)
                if (count > 0) captured.write(scratch, 0, count)
            }
        } finally {
            runCatching { line.stop() }
            runCatching { line.close() }
        }
        val audioBytes = captured.toByteArray()
        require(audioBytes.size >= format.frameSize * format.sampleRate.toInt() * 5) {
            "The microphone capture was too short. Check microphone permissions and try again."
        }
        val wav = Files.createTempFile("archivetune-ambient-", ".wav").toFile()
        return try {
            val frames = audioBytes.size / format.frameSize
            AudioSystem.write(
                javax.sound.sampled.AudioInputStream(ByteArrayInputStream(audioBytes), format, frames.toLong()),
                AudioFileFormat.Type.WAVE,
                wav,
            )
            identify(wav).copy(message = "Ambient sample (${seconds}s) · Chromaprint / AcoustID")
        } finally {
            runCatching { wav.delete() }
        }
    }


    fun identify(file: File): MusicRecognitionResult {
        require(file.isFile && file.canRead()) { "Selected audio file cannot be read" }
        val clientId = System.getenv("ACOUSTID_API_KEY")?.trim().orEmpty()
        require(clientId.isNotBlank()) {
            "Set ACOUSTID_API_KEY to a registered AcoustID application key before using recognition. See acoustid.org/webservice."
        }
        val fpcalc = findExecutable("fpcalc") ?: error("Chromaprint fpcalc was not found. Install Chromaprint/fpcalc and add it to PATH.")
        val output = ProcessBuilder(fpcalc, "-json", file.absolutePath).redirectErrorStream(true).start().let { process ->
            val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val exit = process.waitFor()
            check(exit == 0) { "Chromaprint could not fingerprint this file: ${text.take(300)}" }
            text
        }
        val fingerprintRoot = json.parseToJsonElement(output).jsonObject
        val duration = fingerprintRoot["duration"]?.jsonPrimitive?.doubleOrNull?.roundToLong() ?: error("fpcalc returned no duration")
        val fingerprint = fingerprintRoot["fingerprint"]?.jsonPrimitive?.contentOrNull ?: error("fpcalc returned no fingerprint")
        val query = linkedMapOf(
            "client" to clientId, "meta" to "recordings+releasegroups+compress", "duration" to duration.toString(),
            "fingerprint" to fingerprint, "format" to "json",
        ).entries.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }
        val endpoint = "https://api.acoustid.org/v2/lookup?$query"
        val response = httpGet(endpoint)
        val root = json.parseToJsonElement(response).jsonObject
        check(root["status"]?.jsonPrimitive?.contentOrNull == "ok") {
            "AcoustID rejected the fingerprint lookup: ${root["error"] ?: "unknown service response"}"
        }
        val results = root["results"]?.jsonArray.orEmpty()
        val best = results.maxByOrNull { it.jsonObject["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0 }
            ?: return MusicRecognitionResult(file.nameWithoutExtension, "", "", 0f, "No match")
        val bestObj = best.jsonObject
        val recording = bestObj["recordings"]?.jsonArray?.firstOrNull()?.jsonObject
        val title = recording?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val artist = recording?.get("artists")?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val album = recording?.get("releasegroups")?.jsonArray?.firstOrNull()?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
        val score = bestObj["score"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
        return MusicRecognitionResult(
            title = title.ifBlank { file.nameWithoutExtension }, artist = artist, album = album,
            confidence = score.coerceIn(0f, 1f), message = if (title.isBlank()) "Fingerprint found; MusicBrainz metadata was unavailable" else "Matched using AcoustID / MusicBrainz",
        )
    }

    private fun findExecutable(name: String): String? {
        val pathValue = System.getenv("PATH").orEmpty()
        val candidates = pathValue.split(File.pathSeparator).filter(String::isNotBlank).flatMap { directory ->
            listOf(File(directory, name), File(directory, "$name.exe"))
        } + listOf(
            File("/opt/homebrew/bin/$name"), File("/usr/local/bin/$name"), File("/usr/bin/$name"),
            File("C:\\Program Files\\Chromaprint\\$name.exe"), File("C:\\Program Files (x86)\\Chromaprint\\$name.exe"),
        )
        return candidates.firstOrNull { it.isFile && (it.canExecute() || it.name.endsWith(".exe", true)) }?.absolutePath
    }

    private fun httpGet(url: String): String {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"; connection.connectTimeout = 12_000; connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ArchiveTune/15.1 desktop music recognition")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("AcoustID request failed (HTTP $code): ${body.take(300)}")
            return body
        } finally { connection.disconnect() }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
