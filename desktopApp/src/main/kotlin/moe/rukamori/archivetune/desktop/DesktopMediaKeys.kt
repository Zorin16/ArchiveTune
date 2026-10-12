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
import moe.rukamori.archivetune.shared.ArchiveTuneRepository
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/** Optional system-wide multimedia key bridge. Tray and in-window controls remain available if OS hooks are blocked. */
internal class DesktopMediaKeys(private val repository: ArchiveTuneRepository) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listener = object : NativeKeyListener {
        override fun nativeKeyTyped(event: NativeKeyEvent) = Unit
        override fun nativeKeyReleased(event: NativeKeyEvent) = Unit
        override fun nativeKeyPressed(event: NativeKeyEvent) {
            when (event.keyCode) {
                NativeKeyEvent.VC_MEDIA_PLAY -> scope.launch { repository.togglePlayback() }
                NativeKeyEvent.VC_MEDIA_STOP -> scope.launch { repository.stop() }
                NativeKeyEvent.VC_MEDIA_PREVIOUS -> scope.launch { repository.playPrevious() }
                NativeKeyEvent.VC_MEDIA_NEXT -> scope.launch { repository.playNext() }
            }
        }
    }
    private var registered = false

    fun install(): Boolean {
        if (registered) return true
        return runCatching {
            // Keep JNativeHook's logger out of the console unless setup fails.
            Logger.getLogger(GlobalScreen::class.java.packageName).level = Level.OFF
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
            registered = true
            true
        }.getOrElse { error ->
            if (error !is NativeHookException) System.err.println("ArchiveTune global media-key hook is unavailable: ${error.message}")
            false
        }
    }

    override fun close() {
        if (registered) {
            runCatching { GlobalScreen.removeNativeKeyListener(listener); GlobalScreen.unregisterNativeHook() }
            registered = false
        }
        scope.cancel()
    }
}
