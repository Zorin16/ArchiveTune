/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import moe.rukamori.archivetune.shared.ArchiveTuneRepository
import java.awt.Color
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.event.ActionListener
import javax.swing.SwingUtilities

/** Native AWT system tray integration; available only on platforms with a usable system tray. */
internal class DesktopTrayController(
    private val repository: ArchiveTuneRepository,
    private val onShow: () -> Unit,
    private val onMiniPlayer: () -> Unit,
    private val onQuit: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trayIcon: TrayIcon? = null
    @Volatile private var lastNotifiedTrackKey: String? = null

    fun install(): Boolean {
        if (!SystemTray.isSupported()) return false
        return runCatching {
            val menu = PopupMenu()
            fun addItem(label: String, action: () -> Unit) {
                val item = MenuItem(label)
                item.addActionListener { action() }
                menu.add(item)
            }

            addItem("Show ArchiveTune") { onEdt(onShow) }
            addItem("Open mini player") { onEdt(onMiniPlayer) }
            menu.addSeparator()
            addItem("Previous track") { scope.launch { repository.playPrevious() } }
            addItem("Play / pause") { scope.launch { repository.togglePlayback() } }
            addItem("Next track") { scope.launch { repository.playNext() } }
            addItem("Stop playback") { scope.launch { repository.stop() } }
            menu.addSeparator()
            addItem("Quit ArchiveTune") {
                onEdt {
                    remove()
                    onQuit()
                }
            }

            val icon = TrayIcon(createTrayImage(), "ArchiveTune", menu).apply {
                isImageAutoSize = true
                addActionListener(ActionListener { onEdt(onShow) })
            }
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
            scope.launch {
                repository.playerState.collect { snapshot ->
                    val track = snapshot.currentTrack
                    val description = when {
                        track == null -> "ArchiveTune"
                        snapshot.isPlaying -> "ArchiveTune · ${track.title} — ${track.artist}"
                        else -> "ArchiveTune · Paused · ${track.title}"
                    }
                    onEdt { runCatching { trayIcon?.toolTip = description.take(63) } }
                    if (track != null && snapshot.isPlaying && track.stableKey != lastNotifiedTrackKey) {
                        lastNotifiedTrackKey = track.stableKey
                        onEdt {
                            runCatching {
                                trayIcon?.displayMessage(
                                    "Now playing · ${track.title.take(80)}",
                                    track.artist.take(180).ifBlank { "ArchiveTune" },
                                    TrayIcon.MessageType.INFO,
                                )
                            }
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun remove() {
        val current = trayIcon
        trayIcon = null
        if (current != null) runCatching { SystemTray.getSystemTray().remove(current) }
        scope.cancel()
    }

    private fun onEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    private fun createTrayImage(): BufferedImage {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0xED, 0x55, 0x64)
            g.fillRoundRect(2, 2, 60, 60, 18, 18)
            g.color = Color(0x25, 0x17, 0x31)
            g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 43)
            val metrics = g.fontMetrics
            val letter = "A"
            g.drawString(letter, (64 - metrics.stringWidth(letter)) / 2, (64 - metrics.height) / 2 + metrics.ascent)
        } finally {
            g.dispose()
        }
        return image
    }
}
