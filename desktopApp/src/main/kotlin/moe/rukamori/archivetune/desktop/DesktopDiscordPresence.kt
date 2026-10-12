/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.desktop

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import moe.rukamori.archivetune.shared.MusicTrack
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ByteChannel
import java.nio.channels.FileChannel
import java.nio.channels.SocketChannel
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Discord desktop Rich Presence through its documented local RPC IPC protocol.
 * Does not use, collect, or persist a Discord user token. A Discord desktop client and
 * an application ID created in the Discord Developer Portal are required.
 */
internal class DesktopDiscordPresence : AutoCloseable {
    private var currentClientId: String? = null
    private var channel: ByteChannel? = null

    @Synchronized
    fun update(clientIdRaw: String, track: MusicTrack?, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val clientId = clientIdRaw.trim()
        if (clientId.isBlank() || !clientId.all(Char::isDigit) || track == null) {
            clear()
            return
        }
        runCatching {
            ensureConnected(clientId)
            val nowSeconds = System.currentTimeMillis() / 1000
            val positionSeconds = (positionMs.coerceAtLeast(0) / 1000)
            val remainingSeconds = ((durationMs - positionMs).coerceAtLeast(0) / 1000)
            val activity = buildJsonObject {
                put("type", 2)
                put("name", "ArchiveTune")
                put("details", track.title.take(128))
                put("state", (track.artist.ifBlank { "Unknown artist" } + if (isPlaying) " · Playing" else " · Paused").take(128))
                if (isPlaying && durationMs > 0) {
                    putJsonObject("timestamps") {
                        put("start", nowSeconds - positionSeconds)
                        put("end", nowSeconds + remainingSeconds)
                    }
                }
                put("instance", false)
            }
            val command = buildJsonObject {
                put("cmd", "SET_ACTIVITY")
                putJsonObject("args") {
                    put("pid", ProcessHandle.current().pid())
                    put("activity", activity)
                }
                put("nonce", UUID.randomUUID().toString())
            }
            writeFrame(channel ?: error("Discord IPC is not connected"), 1, command.toString())
            // Consume Discord's acknowledgement so requests remain ordered on the persistent pipe.
            readFrame(channel ?: error("Discord IPC is not connected"))
        }.onFailure {
            disconnect()
            System.err.println("ArchiveTune Discord Rich Presence: ${it.message ?: it::class.simpleName}")
        }
    }

    @Synchronized
    fun clear() {
        val active = channel ?: run { currentClientId = null; return }
        runCatching {
            val command = buildJsonObject {
                put("cmd", "SET_ACTIVITY")
                putJsonObject("args") { put("pid", ProcessHandle.current().pid()); put("activity", kotlinx.serialization.json.JsonNull) }
                put("nonce", UUID.randomUUID().toString())
            }
            writeFrame(active, 1, command.toString())
            readFrame(active)
        }
        disconnect()
    }

    private fun ensureConnected(clientId: String) {
        if (currentClientId == clientId && channel != null) return
        disconnect()
        var lastFailure: Throwable? = null
        for (candidate in ipcCandidates()) {
            var opened: ByteChannel? = null
            try {
                opened = open(candidate)
                val handshake = buildJsonObject { put("v", 1); put("client_id", clientId) }
                writeFrame(opened, 0, handshake.toString())
                readFrame(opened) // READY event
                channel = opened
                currentClientId = clientId
                return
            } catch (error: Throwable) {
                lastFailure = error
                runCatching { opened?.close() }
            }
        }
        throw IllegalStateException("Discord desktop IPC is unavailable. Start Discord and check that Rich Presence is enabled.", lastFailure)
    }

    private fun open(path: String): ByteChannel {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            val file = RandomAccessFile(path, "rw")
            return object : ByteChannel {
                override fun read(dst: ByteBuffer): Int = file.channel.read(dst)
                override fun write(src: ByteBuffer): Int = file.channel.write(src)
                override fun isOpen(): Boolean = file.channel.isOpen
                override fun close() = file.close()
            }
        }
        val socket = SocketChannel.open(StandardProtocolFamily.UNIX)
        socket.configureBlocking(true)
        socket.connect(UnixDomainSocketAddress.of(Path.of(path)))
        return socket
    }

    private fun ipcCandidates(): List<String> {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            return (0..9).map { "\\\\?\\pipe\\discord-ipc-$it" }
        }
        val baseDirs = listOfNotNull(
            System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"),
            System.getenv("TMP"), System.getenv("TEMP"), "/tmp",
        ).distinct()
        return baseDirs.flatMap { base -> (0..9).map { "$base/discord-ipc-$it" } }
            .filter { runCatching { Files.exists(Path.of(it)) }.getOrDefault(false) }
    }

    private fun writeFrame(target: ByteChannel, opcode: Int, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1_048_576) { "Discord IPC frame too large" }
        val buffer = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode).putInt(bytes.size).put(bytes).flip()
        while (buffer.hasRemaining()) target.write(buffer)
    }

    private fun readFrame(target: ByteChannel): String {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        readFully(target, header)
        header.flip()
        val opcode = header.int
        val size = header.int
        require(size in 0..1_048_576) { "Invalid Discord IPC frame size: $size" }
        val payload = ByteBuffer.allocate(size)
        readFully(target, payload)
        return payload.array().toString(Charsets.UTF_8)
    }

    private fun readFully(target: ByteChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val count = target.read(buffer)
            if (count < 0) throw IllegalStateException("Discord IPC closed the connection")
        }
    }

    @Synchronized
    private fun disconnect() {
        runCatching { channel?.close() }
        channel = null
        currentClientId = null
    }

    override fun close() = clear()
}
