/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.desktop

import javafx.application.Platform
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object FxRuntime {
    private val started = AtomicBoolean(false)
    private val ready = CompletableFuture<Unit>()

    fun start() {
        if (started.compareAndSet(false, true)) {
            try {
                Platform.startup { ready.complete(Unit) }
            } catch (_: IllegalStateException) {
                ready.complete(Unit)
            }
        }
        ready.get(15, TimeUnit.SECONDS)
    }
}
