/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import moe.rukamori.archivetune.shared.ArchiveTuneDesktopApp

fun main() {
    FxRuntime.start()
    application {
        val repository = remember { DesktopArchiveTuneRepository() }
        val windowVisibility = remember { mutableStateOf(!repository.preferences.value.startMinimized) }
        val miniPlayerVisibility = remember { mutableStateOf(false) }
        val mediaKeys = remember(repository) { DesktopMediaKeys(repository).apply { install() } }
        val trayController = remember(repository) {
            DesktopTrayController(
                repository = repository,
                onShow = { windowVisibility.value = true },
                onMiniPlayer = { miniPlayerVisibility.value = true },
                onQuit = {
                    repository.close()
                    exitApplication()
                },
            )
        }
        val trayAvailable = remember(trayController) { trayController.install() }

        LaunchedEffect(trayAvailable) {
            windowVisibility.value = !repository.preferences.value.startMinimized || !trayAvailable
        }
        DisposableEffect(trayController) {
            onDispose { trayController.remove() }
        }
        DisposableEffect(mediaKeys) {
            onDispose { mediaKeys.close() }
        }

        Window(
            visible = windowVisibility.value,
            onCloseRequest = {
                if (repository.preferences.value.closeToTray && trayAvailable) {
                    windowVisibility.value = false
                } else {
                    trayController.remove()
                    repository.close()
                    exitApplication()
                }
            },
            title = "ArchiveTune",
            state = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
        ) {
            ArchiveTuneDesktopApp(repository)
        }
        if (miniPlayerVisibility.value) {
            Window(
                visible = true,
                onCloseRequest = { miniPlayerVisibility.value = false },
                title = "ArchiveTune Mini Player",
                state = rememberWindowState(size = DpSize(360.dp, 150.dp)),
                alwaysOnTop = true,
                resizable = false,
            ) {
                moe.rukamori.archivetune.shared.ArchiveTuneMiniPlayer(repository) { miniPlayerVisibility.value = false }
            }
        }
    }
}
