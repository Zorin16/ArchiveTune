/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import moe.rukamori.archivetune.shared.MusicTrack
import moe.rukamori.archivetune.shared.TrackSource
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** DLNA/UPnP AVTransport target support. This is the network-renderer alternative to Android Cast SDK. */
internal class DesktopDlnaCast : AutoCloseable {
    data class Renderer(
        val id: String,
        val name: String,
        val location: String,
        val avTransportControlUrl: String,
        val renderingControlUrl: String?,
    )

    @Volatile private var renderers: List<Renderer> = emptyList()
    private var localServer: HttpServer? = null
    private var servedPath: Path? = null
    private var activeRenderer: Renderer? = null

    fun devices(): List<Renderer> = renderers

    fun discover(timeoutMs: Int = 2600): List<Renderer> {
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 240
            socket.broadcast = true
            val request = listOf(
                "M-SEARCH * HTTP/1.1",
                "HOST: 239.255.255.250:1900",
                "MAN: \"ssdp:discover\"",
                "MX: 2",
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1",
                "USER-AGENT: ArchiveTune/15.1 UPnP/1.1",
                "", "",
            ).joinToString("\r\n").toByteArray(StandardCharsets.US_ASCII)
            val packet = DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900)
            socket.send(packet)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val bytes = ByteArray(8192)
                val response = DatagramPacket(bytes, bytes.size)
                try {
                    socket.receive(response)
                    val headers = String(response.data, 0, response.length, StandardCharsets.US_ASCII)
                    val location = headers.lineSequence().firstOrNull { it.startsWith("LOCATION:", true) }
                        ?.substringAfter(':')?.trim()
                    if (!location.isNullOrBlank() && location.startsWith("http", true)) locations += location
                } catch (_: java.net.SocketTimeoutException) { /* keep listening until deadline */ }
            }
        }
        renderers = locations.mapNotNull { location -> runCatching { readRenderer(location) }.getOrNull() }
            .distinctBy(Renderer::id).sortedBy(Renderer::name)
        return renderers
    }

    fun play(rendererId: String, track: MusicTrack, resolvedRemoteUrl: String? = null) {
        val renderer = renderers.firstOrNull { it.id == rendererId } ?: error("Select a discovered renderer first")
        require(track.canPlay) { "This item cannot be played as audio" }
        stopCurrent()
        val mediaUri = if (track.source == TrackSource.LOCAL) {
            val path = Paths.get(track.location ?: track.id).toAbsolutePath().normalize()
            require(Files.isRegularFile(path)) { "Local track file no longer exists" }
            serveLocalFile(path)
        } else {
            resolvedRemoteUrl ?: track.location ?: error("Could not resolve a playable URL for this YouTube Music item")
        }
        val mediaPath = if (track.source == TrackSource.LOCAL) {
            runCatching { Paths.get(track.location ?: track.id).fileName.toString() }.getOrDefault("")
        } else runCatching { URI(mediaUri).path.orEmpty() }.getOrDefault("")
        val mediaExtension = mediaPath.substringAfterLast('.', "").lowercase()
        val remoteMime = runCatching { URI(mediaUri).rawQuery.orEmpty().lowercase() }.getOrDefault("")
        val protocolInfo = when {
            mediaExtension in setOf("mp3", "mp2") -> "http-get:*:audio/mpeg:*"
            mediaExtension == "wav" -> "http-get:*:audio/wav:*"
            mediaExtension == "flac" -> "http-get:*:audio/flac:*"
            mediaExtension in setOf("ogg", "opus") -> "http-get:*:audio/ogg:*"
            mediaExtension in setOf("m4a", "aac") || "audio%2fmp4" in remoteMime || "mime=audio/mp4" in remoteMime -> "http-get:*:audio/mp4:*"
            else -> "http-get:*:audio/mpeg:*"
        }
        val metadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="1" parentID="0" restricted="1"><dc:title>${xml(track.title)}</dc:title><upnp:artist>${xml(track.artist)}</upnp:artist><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="$protocolInfo">${xml(mediaUri)}</res></item>
            </DIDL-Lite>
        """.trimIndent()
        soap(renderer.avTransportControlUrl, "AVTransport", "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${xml(mediaUri)}</CurrentURI><CurrentURIMetaData>${xml(metadata)}</CurrentURIMetaData>")
        soap(renderer.avTransportControlUrl, "AVTransport", "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
        activeRenderer = renderer
    }

    fun pause() { activeRenderer?.let { soap(it.avTransportControlUrl, "AVTransport", "Pause", "<InstanceID>0</InstanceID>") } }
    fun resume() { activeRenderer?.let { soap(it.avTransportControlUrl, "AVTransport", "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>") } }

    fun seek(positionMs: Long) {
        val renderer = activeRenderer ?: return
        val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
        val target = "%02d:%02d:%02d".format(java.util.Locale.ROOT, totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)
        soap(renderer.avTransportControlUrl, "AVTransport", "Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>$target</Target>")
    }

    fun positionMs(): Long? {
        val renderer = activeRenderer ?: return null
        val response = soap(renderer.avTransportControlUrl, "AVTransport", "GetPositionInfo", "<InstanceID>0</InstanceID>")
        val raw = Regex("<(?:[A-Za-z0-9_]+:)?RelTime[^>]*>([^<]+)</(?:[A-Za-z0-9_]+:)?RelTime>", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val match = Regex("""^(\d{1,3}):(\d{2}):(\d{2})(?:\.(\d+))?$""").matchEntire(raw) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millis = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (((hours * 60L + minutes) * 60L) + seconds) * 1000L + millis
    }

    fun setVolume(volume: Float) {
        val renderer = activeRenderer ?: return
        val url = renderer.renderingControlUrl ?: return
        soap(url, "RenderingControl", "SetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>${(volume.coerceIn(0f, 1f) * 100).toInt()}</DesiredVolume>")
    }

    fun stop() {
        activeRenderer?.let { runCatching { soap(it.avTransportControlUrl, "AVTransport", "Stop", "<InstanceID>0</InstanceID>") } }
        stopCurrent()
    }

    private fun stopCurrent() {
        activeRenderer = null
        runCatching { localServer?.stop(0) }
        localServer = null
        servedPath = null
    }

    private fun readRenderer(location: String): Renderer? {
        val xml = http(location, "GET", null, emptyMap())
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
        val deviceNodes = doc.getElementsByTagNameNS("*", "device")
        for (i in 0 until deviceNodes.length) {
            val device = deviceNodes.item(i) as? Element ?: continue
            if (!childText(device, "deviceType").contains("MediaRenderer", true)) continue
            val udn = childText(device, "UDN").ifBlank { location }
            val name = childText(device, "friendlyName").ifBlank { "DLNA Media Renderer" }
            val services = device.getElementsByTagNameNS("*", "service")
            var avUrl: String? = null
            var renderUrl: String? = null
            for (j in 0 until services.length) {
                val service = services.item(j) as? Element ?: continue
                val serviceType = childText(service, "serviceType")
                val control = childText(service, "controlURL")
                if (control.isBlank()) continue
                when {
                    serviceType.contains("AVTransport", true) -> avUrl = URI(location).resolve(control).toString()
                    serviceType.contains("RenderingControl", true) -> renderUrl = URI(location).resolve(control).toString()
                }
            }
            if (avUrl != null) return Renderer(udn, name, location, avUrl!!, renderUrl)
        }
        return null
    }

    private fun serveLocalFile(path: Path): String {
        servedPath = path
        val server = HttpServer.create(InetSocketAddress("0.0.0.0", 0), 0)
        val key = UUID.randomUUID().toString().replace("-", "")
        server.createContext("/$key") { exchange -> serveRangeAwareFile(exchange, path) }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "ArchiveTune-DLNA-http").apply { isDaemon = true } }
        server.start(); localServer = server
        val ip = networkAddress()
        if (ip == null) {
            stopCurrent()
            error("No LAN IPv4 address found. Connect this PC to the same network as the renderer.")
        }
        return "http://$ip:${server.address.port}/$key"
    }

    private fun serveRangeAwareFile(exchange: HttpExchange, path: Path) {
        try {
            val size = Files.size(path)
            val range = exchange.requestHeaders.getFirst("Range")
            var start = 0L; var end = size - 1; var status = 200
            if (!range.isNullOrBlank() && range.startsWith("bytes=")) {
                val parts = range.removePrefix("bytes=").substringBefore(',').split('-', limit = 2)
                start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                end = parts.getOrNull(1)?.toLongOrNull() ?: (size - 1)
                if (start !in 0 until size || end < start) {
                    exchange.responseHeaders.add("Content-Range", "bytes */$size")
                    exchange.sendResponseHeaders(416, -1); return
                }
                end = end.coerceAtMost(size - 1); status = 206
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$size")
            }
            exchange.responseHeaders.add("Content-Type", mimeType(path))
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("Content-Length", (end - start + 1).toString())
            if (exchange.requestMethod.equals("HEAD", true)) { exchange.sendResponseHeaders(status, -1); return }
            exchange.sendResponseHeaders(status, end - start + 1)
            Files.newByteChannel(path, StandardOpenOption.READ).use { channel ->
                channel.position(start)
                val out = exchange.responseBody
                val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
                var remaining = end - start + 1
                while (remaining > 0) {
                    buffer.clear(); buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                    val count = channel.read(buffer); if (count <= 0) break
                    out.write(buffer.array(), 0, count); remaining -= count
                }
                out.flush()
            }
        } catch (_: Throwable) {
            runCatching { exchange.close() }
        } finally { exchange.close() }
    }

    private fun networkAddress(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    private fun soap(controlUrl: String, service: String, action: String, body: String): String {
        val serviceType = when (service) {
            "AVTransport" -> "urn:schemas-upnp-org:service:AVTransport:1"
            else -> "urn:schemas-upnp-org:service:RenderingControl:1"
        }
        val envelope = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="$serviceType">$body</u:$action></s:Body></s:Envelope>"""
        return http(controlUrl, "POST", envelope, mapOf("Content-Type" to "text/xml; charset=\"utf-8\"", "SOAPACTION" to "\"$serviceType#$action\""))
    }

    private fun http(url: String, method: String, body: String?, headers: Map<String, String>): String {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method; conn.connectTimeout = 4000; conn.readTimeout = 7000
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) } }
            val code = conn.responseCode
            val input = if (code in 200..299) conn.inputStream else conn.errorStream
            val content = input?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("DLNA $method failed (HTTP $code): ${content.take(200)}")
            return content
        } finally { conn.disconnect() }
    }

    private fun childText(element: Element, localName: String): String =
        (element.getElementsByTagNameNS("*", localName).item(0)?.textContent ?: "").trim()

    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun mimeType(path: Path): String = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "m4a", "aac" -> "audio/mp4"; "flac" -> "audio/flac"; "ogg", "opus" -> "audio/ogg"; else -> "application/octet-stream"
    }

    override fun close() = stop()
}
