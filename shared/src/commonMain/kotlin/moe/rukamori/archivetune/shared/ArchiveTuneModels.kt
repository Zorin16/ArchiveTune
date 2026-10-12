/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import kotlinx.coroutines.flow.StateFlow

enum class TrackSource { LOCAL, YOUTUBE_MUSIC }
enum class MusicKind { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, PODCAST, EPISODE }
enum class SearchCategory { SONGS, VIDEOS, ALBUMS, ARTISTS, PLAYLISTS, PODCASTS }
enum class RepeatMode { OFF, ALL, ONE }
enum class SpotifyPlaybackCommand { TOGGLE_PLAY_PAUSE, NEXT, PREVIOUS }

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val source: TrackSource,
    val kind: MusicKind = MusicKind.SONG,
    /** Absolute path for local tracks, or the canonical/original YouTube Music URL for remote entries. */
    val location: String? = null,
) {
    val canPlay: Boolean get() = kind == MusicKind.SONG || kind == MusicKind.VIDEO || kind == MusicKind.EPISODE
    val stableKey: String get() = "${source.name}:$id"
}

data class PlayerSnapshot(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.72f,
    val statusMessage: String = "Ready to play",
)

data class PlaybackQueueSnapshot(
    val tracks: List<MusicTrack> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    val currentTrack: MusicTrack? get() = tracks.getOrNull(currentIndex)
}

data class MusicPlaylist(
    val id: String,
    val name: String,
    val tracks: List<MusicTrack> = emptyList(),
    val createdAtEpochMs: Long = 0L,
)

data class BrowseCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val kind: MusicKind,
    val tracks: List<MusicTrack>,
)

data class LyricWord(val timeMs: Long, val endTimeMs: Long, val text: String)
data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanizedText: String? = null,
)
data class LyricsSheet(
    val trackKey: String,
    val title: String,
    val artist: String,
    val source: String,
    val synchronized: Boolean,
    val lines: List<LyricLine>,
)

data class LastFmStatus(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val authorizationPending: Boolean = false,
    val username: String = "",
    val message: String = "Last.fm integration is not configured",
)

data class ListenBrainzStatus(
    val connected: Boolean = false,
    val username: String = "",
    val message: String = "Connect a ListenBrainz personal access token to enable scrobbling",
)

data class SpotifyAccountStatus(
    val connected: Boolean = false,
    val displayName: String = "",
    val message: String = "Spotify account is not connected",
)

data class SpotifyPlaybackStatus(
    val activeDevice: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val deviceName: String = "",
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val message: String = "Connect Spotify to inspect Spotify Connect playback",
)

data class DlnaRenderer(
    val id: String,
    val name: String,
)

data class MusicRecognitionResult(
    val title: String,
    val artist: String,
    val album: String = "",
    val confidence: Float = 0f,
    val message: String = "",
)

val DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER = listOf(
    "BetterLyrics",
    "BetterLyrics Portato",
    "YouLyPlus",
    "LRCLIB",
    "KuGou",
    "Megalobiz",
    "SimpMusic",
    "Unison",
    "Paxsenix: Apple Music",
    "Paxsenix: Spotify",
    "Paxsenix: Musixmatch",
    "YouTube Subtitle",
    "YouTube Music",
    "Paxsenix (Auto)",
)

data class DesktopPreferences(
    val playbackSpeed: Float = 1f,
    val volume: Float = 0.72f,
    val startMinimized: Boolean = false,
    val closeToTray: Boolean = false,
    val scrobblingEnabled: Boolean = false,
    val listenBrainzEnabled: Boolean = false,
    val lastFmUsername: String = "",
    val theme: String = "Dark",
    val lyricsProvider: String = "Auto",
    val lyricsProviderOrder: List<String> = DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER,
    val enabledLyricsProviders: List<String> = DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER,
    val showLyricsTranslation: Boolean = true,
    val showLyricsRomanization: Boolean = true,
    /** JavaFX equalizer band gains in dB; empty means use the backend defaults. */
    val equalizerEnabled: Boolean = false,
    val equalizerGainsDb: List<Float> = List(10) { 0f },
    /** Discord Developer Portal application ID; no user token is used. */
    val discordPresenceEnabled: Boolean = false,
    val discordApplicationId: String = "",
    /** Spotify public Client ID for Authorization Code with PKCE; no client secret is stored. */
    val spotifyClientId: String = "",
    /** Desktop equivalent of WorkManager scheduled backups. */
    val scheduledBackupEnabled: Boolean = false,
    val scheduledBackupIntervalHours: Int = 24,
)

/** Platform boundary. Shared UI owns the navigation and intent; each target implements OS media, files and persistence. */
interface ArchiveTuneRepository {
    val playerState: StateFlow<PlayerSnapshot>
    val queueState: StateFlow<PlaybackQueueSnapshot>
    val playlists: StateFlow<List<MusicPlaylist>>
    val recentlyPlayed: StateFlow<List<MusicTrack>>
    val downloadedTracks: StateFlow<List<MusicTrack>>
    val subscribedPodcasts: StateFlow<List<MusicTrack>>
    val lyricsState: StateFlow<LyricsSheet?>
    val preferences: StateFlow<DesktopPreferences>
    val lastFmStatus: StateFlow<LastFmStatus>
    val listenBrainzStatus: StateFlow<ListenBrainzStatus>
    val spotifyStatus: StateFlow<SpotifyAccountStatus>
    val spotifyPlayback: StateFlow<SpotifyPlaybackStatus>
    val dlnaRenderers: StateFlow<List<DlnaRenderer>>
    val recognitionResult: StateFlow<MusicRecognitionResult?>

    suspend fun beginLastFmAuthorization()
    suspend fun completeLastFmAuthorization()
    suspend fun disconnectLastFm()
    suspend fun connectListenBrainz(token: String)
    suspend fun disconnectListenBrainz()
    suspend fun connectSpotify(clientId: String)
    suspend fun disconnectSpotify()
    suspend fun refreshSpotifyPlayback(): SpotifyPlaybackStatus
    suspend fun controlSpotifyPlayback(command: SpotifyPlaybackCommand): SpotifyPlaybackStatus
    suspend fun discoverDlnaDevices(): List<DlnaRenderer>
    suspend fun castCurrentTrack(rendererId: String)
    suspend fun stopCasting()
    suspend fun recognizeLocalAudio(): MusicRecognitionResult?
    /** Record a short microphone sample on explicit user request and identify it through Chromaprint/AcoustID. */
    suspend fun recognizeAmbientAudio(): MusicRecognitionResult?
    suspend fun search(query: String, category: SearchCategory): List<MusicTrack>
    suspend fun loadHomeFeed(): List<MusicTrack>
    suspend fun loadCollection(track: MusicTrack): BrowseCollection
    suspend fun loadLocalLibrary(): List<MusicTrack>
    suspend fun importLocalMusic(): List<MusicTrack>
    /** Remove a track from the desktop index without deleting the user's original audio file. */
    suspend fun removeFromLocalLibrary(trackKey: String)
    suspend fun play(track: MusicTrack)
    suspend fun playQueue(tracks: List<MusicTrack>, startIndex: Int = 0)
    suspend fun queueTrack(track: MusicTrack, playNext: Boolean = false)
    suspend fun removeFromQueue(trackKey: String)
    suspend fun moveQueueItem(trackKey: String, offset: Int)
    suspend fun clearQueue()
    suspend fun moveTrackInPlaylist(playlistId: String, trackKey: String, offset: Int)
    suspend fun playNext()
    suspend fun playPrevious()
    suspend fun toggleShuffle()
    suspend fun cycleRepeatMode()
    suspend fun togglePlayback()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun stop()

    suspend fun createPlaylist(name: String): MusicPlaylist
    suspend fun renamePlaylist(id: String, name: String)
    suspend fun deletePlaylist(id: String)
    suspend fun addToPlaylist(playlistId: String, track: MusicTrack)
    suspend fun removeFromPlaylist(playlistId: String, trackKey: String)
    suspend fun loadLyrics(track: MusicTrack)
    suspend fun download(track: MusicTrack): MusicTrack
    suspend fun deleteDownload(trackKey: String)
    suspend fun subscribePodcast(track: MusicTrack)
    suspend fun unsubscribePodcast(trackKey: String)
    suspend fun savePreferences(value: DesktopPreferences)
    suspend fun exportBackup(): String
    suspend fun importBackup(): String
    /** Import the original Android app backup ZIP (settings.xml and/or song.db) into desktop-native data. */
    suspend fun importAndroidBackup(): String

    fun close()
}
