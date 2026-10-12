/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveTuneModelsTest {
    @Test
    fun stableKeyIncludesSourceAndId() {
        val local = track("same-id", TrackSource.LOCAL)
        val remote = track("same-id", TrackSource.YOUTUBE_MUSIC)
        assertEquals("LOCAL:same-id", local.stableKey)
        assertEquals("YOUTUBE_MUSIC:same-id", remote.stableKey)
        assertFalse(local.stableKey == remote.stableKey)
    }

    @Test
    fun onlyPlayableItemKindsCanPlayDirectly() {
        assertTrue(track("song", TrackSource.LOCAL, MusicKind.SONG).canPlay)
        assertTrue(track("video", TrackSource.YOUTUBE_MUSIC, MusicKind.VIDEO).canPlay)
        assertTrue(track("episode", TrackSource.YOUTUBE_MUSIC, MusicKind.EPISODE).canPlay)
        assertFalse(track("album", TrackSource.YOUTUBE_MUSIC, MusicKind.ALBUM).canPlay)
        assertFalse(track("artist", TrackSource.YOUTUBE_MUSIC, MusicKind.ARTIST).canPlay)
        assertFalse(track("playlist", TrackSource.YOUTUBE_MUSIC, MusicKind.PLAYLIST).canPlay)
        assertFalse(track("podcast", TrackSource.YOUTUBE_MUSIC, MusicKind.PODCAST).canPlay)
    }

    @Test
    fun queueCurrentTrackIsBoundedByCurrentIndex() {
        val first = track("1", TrackSource.LOCAL)
        val second = track("2", TrackSource.LOCAL)
        assertNull(PlaybackQueueSnapshot(listOf(first, second), -1).currentTrack)
        assertEquals(first, PlaybackQueueSnapshot(listOf(first, second), 0).currentTrack)
        assertEquals(second, PlaybackQueueSnapshot(listOf(first, second), 1).currentTrack)
        assertNull(PlaybackQueueSnapshot(listOf(first, second), 2).currentTrack)
    }

    @Test
    fun repeatModesAreExplicitAndStable() {
        assertEquals(listOf(RepeatMode.OFF, RepeatMode.ALL, RepeatMode.ONE), RepeatMode.entries)
    }

    @Test
    fun lyricLinesCanPreserveWordTiming() {
        val firstWord = LyricWord(timeMs = 0L, endTimeMs = 350L, text = "Hello")
        val secondWord = LyricWord(timeMs = 350L, endTimeMs = 800L, text = " world")
        val line = LyricLine(timeMs = 0L, text = "Hello world", words = listOf(firstWord, secondWord))
        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals(350L, line.words.first().endTimeMs)
        assertEquals(350L, line.words.last().timeMs)
    }

    @Test
    fun lyricLinesCanPreserveTranslationAndRomanization() {
        val line = LyricLine(
            timeMs = 1200L,
            text = "Kimi no koe",
            translation = "Your voice",
            romanizedText = "Kimi no koe",
        )
        assertEquals("Your voice", line.translation)
        assertEquals("Kimi no koe", line.romanizedText)
    }

    @Test
    fun desktopPlaybackPreferencesHaveSafeDefaults() {
        val preferences = DesktopPreferences()
        assertEquals(1f, preferences.playbackSpeed)
        assertEquals(0.72f, preferences.volume)
        assertEquals("Dark", preferences.theme)
        assertEquals("Auto", preferences.lyricsProvider)
        assertEquals(14, preferences.lyricsProviderOrder.size)
        assertEquals("BetterLyrics", preferences.lyricsProviderOrder.first())
        assertEquals(DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER, preferences.enabledLyricsProviders)
        assertTrue(preferences.showLyricsTranslation)
        assertFalse(preferences.showLyricsRomanization)
        assertFalse(preferences.startMinimized)
        assertFalse(preferences.closeToTray)
        assertFalse(preferences.listenBrainzEnabled)
    }

    private fun track(id: String, source: TrackSource, kind: MusicKind = MusicKind.SONG) = MusicTrack(
        id = id,
        title = "Track $id",
        artist = "Test artist",
        source = source,
        kind = kind,
    )
}
