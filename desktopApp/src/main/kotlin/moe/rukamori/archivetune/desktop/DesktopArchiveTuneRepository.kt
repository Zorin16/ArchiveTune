/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.desktop

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.lrclib.LrcLib
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.simpmusic.SimpMusicLyrics
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.unison.Unison
import moe.koiverse.rukamori.betterlyrics.BetterLyrics
import moe.koiverse.rukamori.betterlyrics.TTMLParser
import moe.rukamori.archivetune.youlyplus.YouLyPlus
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.NewPipeUtils
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.shared.ArchiveTuneRepository
import moe.rukamori.archivetune.shared.BrowseCollection
import moe.rukamori.archivetune.shared.DesktopPreferences
import moe.rukamori.archivetune.shared.DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER
import moe.rukamori.archivetune.shared.LyricLine
import moe.rukamori.archivetune.shared.LyricWord
import moe.rukamori.archivetune.shared.LyricsSheet
import moe.rukamori.archivetune.shared.LastFmStatus
import moe.rukamori.archivetune.shared.ListenBrainzStatus
import moe.rukamori.archivetune.shared.SpotifyAccountStatus
import moe.rukamori.archivetune.shared.SpotifyPlaybackCommand
import moe.rukamori.archivetune.shared.SpotifyPlaybackStatus
import moe.rukamori.archivetune.shared.DlnaRenderer
import moe.rukamori.archivetune.shared.MusicRecognitionResult
import moe.rukamori.archivetune.shared.MusicKind
import moe.rukamori.archivetune.shared.MusicPlaylist
import moe.rukamori.archivetune.shared.MusicTrack
import moe.rukamori.archivetune.shared.PlaybackQueueSnapshot
import moe.rukamori.archivetune.shared.PlayerSnapshot
import moe.rukamori.archivetune.shared.RepeatMode
import moe.rukamori.archivetune.shared.SearchCategory
import moe.rukamori.archivetune.shared.TrackSource
import java.awt.Desktop
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.awt.EventQueue
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import java.util.Base64
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.random.Random

internal class DesktopArchiveTuneRepository : ArchiveTuneRepository {
    private data class DiscordWatchSnapshot(
        val enabled: Boolean,
        val applicationId: String,
        val track: MusicTrack?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val positionBucketMs: Long,
        val durationMs: Long,
    )
    private data class ParsedProviderLyrics(
        val lines: List<LyricLine>,
        val synchronized: Boolean,
        val quality: Int,
    )

    private data class DesktopLyricsCandidate(
        val order: Int,
        val provider: String,
        val parsed: ParsedProviderLyrics,
    )

    private data class PendingLyricsWord(val timeMs: Long, val text: String)
    private data class PendingLyricsLine(val times: List<Long>, val text: String, val words: List<PendingLyricsWord>)

    private companion object {
        const val LYRICS_PROVIDER_TIMEOUT_MS = 8_000L
        const val LYRICS_FETCH_TIMEOUT_MS = 12_000L
        const val LYRICS_QUALITY_GRACE_MS = 1_500L
        const val LYRICS_WORD_SYNC_GRACE_MS = 350L
        const val MAX_CONCURRENT_LYRICS_PROVIDERS = 6
    }
    private val dataRoot: Path = Paths.get(System.getProperty("user.home"), ".archivetune")
    private val libraryPath: Path = dataRoot.resolve("desktop-library.txt")
    private val playlistsPath: Path = dataRoot.resolve("desktop-playlists.db")
    private val historyPath: Path = dataRoot.resolve("desktop-history.db")
    private val queuePath: Path = dataRoot.resolve("desktop-queue.db")
    private val podcastsPath: Path = dataRoot.resolve("desktop-podcasts.db")
    private val playerStatePath: Path = dataRoot.resolve("desktop-player-state.db")
    private val transcodedDir: Path = dataRoot.resolve("transcoded")
    private val preferencesPath: Path = dataRoot.resolve("desktop-preferences.properties")
    private val lastFmSessionPath: Path = dataRoot.resolve("lastfm-session.properties")
    private val listenBrainzTokenPath: Path = dataRoot.resolve("listenbrainz-token.properties")
    private val downloadsDir: Path = dataRoot.resolve("downloads")
    private val supportedExtensions = setOf("mp3", "wav", "aiff", "aif", "au", "m4a", "aac", "flac", "ogg", "opus")
    // JavaFX supports common MP3/WAV/AIFF/AAC containers, but FLAC/Ogg/Opus support is not portable.
    private val directJavaFxExtensions = setOf("mp3", "wav", "aiff", "aif", "au", "m4a", "aac")
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ArchiveTune-BackupScheduler").apply { isDaemon = true }
    }
    private val castPollScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ArchiveTune-DLNA-position").apply { isDaemon = true }
    }
    @Volatile private var activeCastRendererId: String? = null
    @Volatile private var castPositionTask: ScheduledFuture<*>? = null
    @Volatile private var castEndHandledTrackKey: String? = null

    private val mutablePlayerState = MutableStateFlow(PlayerSnapshot())
    override val playerState: StateFlow<PlayerSnapshot> = mutablePlayerState.asStateFlow()
    private val mutableQueueState = MutableStateFlow(PlaybackQueueSnapshot())
    override val queueState: StateFlow<PlaybackQueueSnapshot> = mutableQueueState.asStateFlow()
    private val mutablePlaylists = MutableStateFlow<List<MusicPlaylist>>(emptyList())
    override val playlists: StateFlow<List<MusicPlaylist>> = mutablePlaylists.asStateFlow()
    private val mutableRecentlyPlayed = MutableStateFlow<List<MusicTrack>>(emptyList())
    override val recentlyPlayed: StateFlow<List<MusicTrack>> = mutableRecentlyPlayed.asStateFlow()
    private val mutableDownloadedTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    override val downloadedTracks: StateFlow<List<MusicTrack>> = mutableDownloadedTracks.asStateFlow()
    private val mutableSubscribedPodcasts = MutableStateFlow<List<MusicTrack>>(emptyList())
    override val subscribedPodcasts: StateFlow<List<MusicTrack>> = mutableSubscribedPodcasts.asStateFlow()
    private val mutableLyrics = MutableStateFlow<LyricsSheet?>(null)
    override val lyricsState: StateFlow<LyricsSheet?> = mutableLyrics.asStateFlow()
    private val mutablePreferences = MutableStateFlow(DesktopPreferences())
    override val preferences: StateFlow<DesktopPreferences> = mutablePreferences.asStateFlow()
    private val mutableLastFmStatus = MutableStateFlow(LastFmStatus())
    override val lastFmStatus: StateFlow<LastFmStatus> = mutableLastFmStatus.asStateFlow()
    private val mutableListenBrainzStatus = MutableStateFlow(ListenBrainzStatus())
    override val listenBrainzStatus: StateFlow<ListenBrainzStatus> = mutableListenBrainzStatus.asStateFlow()
    private val mutableSpotifyStatus = MutableStateFlow(SpotifyAccountStatus())
    override val spotifyStatus: StateFlow<SpotifyAccountStatus> = mutableSpotifyStatus.asStateFlow()
    private val mutableSpotifyPlayback = MutableStateFlow(SpotifyPlaybackStatus())
    override val spotifyPlayback: StateFlow<SpotifyPlaybackStatus> = mutableSpotifyPlayback.asStateFlow()
    private val mutableDlnaRenderers = MutableStateFlow<List<DlnaRenderer>>(emptyList())
    override val dlnaRenderers: StateFlow<List<DlnaRenderer>> = mutableDlnaRenderers.asStateFlow()
    private val mutableRecognitionResult = MutableStateFlow<MusicRecognitionResult?>(null)
    override val recognitionResult: StateFlow<MusicRecognitionResult?> = mutableRecognitionResult.asStateFlow()
    private val discordPresence = DesktopDiscordPresence()
    private val spotifyAccount = DesktopSpotifyAccount()
    private val dlnaCast = DesktopDlnaCast()
    private val musicRecognition = DesktopMusicRecognition()
    private var pendingLastFmToken: String? = null
    @Volatile private var listenBrainzToken: String = ""
    private var lastFmScrobbledTrackKey: String? = null
    private var listenBrainzScrobbledTrackKey: String? = null
    private var trackStartedAtEpochSeconds: Long = 0L
    private var lastPersistedPositionMs: Long = -5_000L
    private val playerStatePersistLock = Any()
    @Volatile private var playerStatePersistVersion: Long = 0L

    private var mediaPlayer: MediaPlayer? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        runCatching {
            Files.createDirectories(dataRoot)
            Files.createDirectories(downloadsDir)
        }
        restorePreferences()
        mutablePlayerState.value = mutablePlayerState.value.copy(volume = mutablePreferences.value.volume)
        initializeLastFm()
        initializeListenBrainz()
        restorePlaylistsAndHistory()
        restoreQueue()
        restorePodcastSubscriptions()
        restorePlayerState()
        refreshDownloads()
        backupScheduler.scheduleWithFixedDelay(
            { runCatching { runAutomaticBackupIfDue() }.onFailure { System.err.println("ArchiveTune automatic backup: ${it.message ?: it::class.simpleName}") } },
            30,
            15,
            TimeUnit.MINUTES,
        )
        ioScope.launch {
            val status = runCatching { spotifyAccount.refreshStatus() }.getOrElse {
                DesktopSpotifyAccount.Status(false, "", it.message ?: "Spotify account status unavailable")
            }
            mutableSpotifyStatus.value = SpotifyAccountStatus(status.connected, status.displayName, status.message)
            if (status.connected) runCatching { refreshSpotifyPlayback() }
        }
        ioScope.launch {
            combine(mutablePlayerState, mutablePreferences) { player, prefs ->
                DiscordWatchSnapshot(
                    enabled = prefs.discordPresenceEnabled,
                    applicationId = prefs.discordApplicationId,
                    track = player.currentTrack,
                    isPlaying = player.isPlaying,
                    positionMs = player.positionMs,
                    positionBucketMs = player.positionMs / 10_000L * 10_000L,
                    durationMs = player.durationMs,
                )
            }.distinctUntilChanged { old, new ->
                old.enabled == new.enabled && old.applicationId == new.applicationId &&
                    old.track?.stableKey == new.track?.stableKey && old.isPlaying == new.isPlaying &&
                    (!new.isPlaying || old.positionBucketMs == new.positionBucketMs)
            }.collect { state ->
                if (state.enabled && state.track != null) {
                    discordPresence.update(state.applicationId, state.track, state.isPlaying, state.positionMs, state.durationMs)
                } else {
                    discordPresence.clear()
                }
            }
        }
    }

    override suspend fun beginLastFmAuthorization() = withContext(Dispatchers.IO) {
        if (!LastFM.isInitialized()) {
            mutableLastFmStatus.value = LastFmStatus(
                configured = false,
                connected = false,
                message = "Set LASTFM_API_KEY and LASTFM_SECRET environment variables, then restart ArchiveTune.",
            )
            throw IllegalStateException(mutableLastFmStatus.value.message)
        }
        val token = LastFM.getToken().getOrThrow().token
        pendingLastFmToken = token
        val authUrl = LastFM.getAuthUrl(token)
        Desktop.getDesktop().browse(URI(authUrl))
        mutableLastFmStatus.value = mutableLastFmStatus.value.copy(
            configured = true,
            authorizationPending = true,
            message = "Authorize ArchiveTune in your browser, then return here and choose Finish sign-in.",
        )
    }

    override suspend fun completeLastFmAuthorization() = withContext(Dispatchers.IO) {
        val token = pendingLastFmToken ?: throw IllegalStateException("Start Last.fm sign-in first.")
        val session = LastFM.getSession(token).getOrThrow().session
        LastFM.sessionKey = session.key
        pendingLastFmToken = null
        persistLastFmSession(session.key)
        savePreferences(mutablePreferences.value.copy(lastFmUsername = session.name, scrobblingEnabled = false))
        mutableLastFmStatus.value = LastFmStatus(
            configured = true,
            connected = true,
            authorizationPending = false,
            username = session.name,
            message = "Connected to Last.fm as ${session.name}.",
        )
    }

    override suspend fun disconnectLastFm() = withContext(Dispatchers.IO) {
        LastFM.sessionKey = null
        pendingLastFmToken = null
        runCatching { Files.deleteIfExists(lastFmSessionPath) }
        mutablePreferences.value = mutablePreferences.value.copy(lastFmUsername = "", scrobblingEnabled = false)
        savePreferences(mutablePreferences.value)
        mutableLastFmStatus.value = LastFmStatus(
            configured = LastFM.isInitialized(),
            connected = false,
            authorizationPending = false,
            message = "Disconnected from Last.fm.",
        )
    }

    override suspend fun connectListenBrainz(token: String) = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        require(cleanToken.length in 8..512) { "Enter a valid ListenBrainz personal access token." }
        mutableListenBrainzStatus.value = mutableListenBrainzStatus.value.copy(message = "Validating ListenBrainz token…")
        try {
            val username = validateListenBrainzToken(cleanToken)
            persistListenBrainzToken(cleanToken).getOrThrow()
            listenBrainzToken = cleanToken
            mutableListenBrainzStatus.value = ListenBrainzStatus(
                connected = true,
                username = username,
                message = if (username.isBlank()) "ListenBrainz token validated and securely stored." else "Connected to ListenBrainz as $username.",
            )
        } catch (failure: Throwable) {
            mutableListenBrainzStatus.value = mutableListenBrainzStatus.value.copy(
                connected = false,
                message = failure.message ?: "ListenBrainz token validation failed.",
            )
            throw failure
        }
    }

    override suspend fun disconnectListenBrainz() = withContext(Dispatchers.IO) {
        listenBrainzToken = ""
        runCatching { Files.deleteIfExists(listenBrainzTokenPath) }
        mutableListenBrainzStatus.value = ListenBrainzStatus(
            connected = false,
            message = "Disconnected from ListenBrainz; the saved token was removed.",
        )
        savePreferences(mutablePreferences.value.copy(listenBrainzEnabled = false))
    }

    override suspend fun connectSpotify(clientId: String) = withContext(Dispatchers.IO) {
        val sanitized = mutablePreferences.value.copy(spotifyClientId = clientId.trim())
        savePreferences(sanitized)
        mutableSpotifyStatus.value = SpotifyAccountStatus(message = "Opening Spotify authorization in your browser…")
        try {
            val status = spotifyAccount.connect(clientId)
            mutableSpotifyStatus.value = SpotifyAccountStatus(status.connected, status.displayName, status.message)
            runCatching { refreshSpotifyPlayback() }
        } catch (failure: Throwable) {
            mutableSpotifyStatus.value = SpotifyAccountStatus(false, "", failure.message ?: "Spotify connection failed")
            throw failure
        }
    }

    override suspend fun disconnectSpotify() = withContext(Dispatchers.IO) {
        spotifyAccount.disconnect()
        mutableSpotifyStatus.value = SpotifyAccountStatus(false, "", "Disconnected from Spotify; saved authorization removed")
        mutableSpotifyPlayback.value = SpotifyPlaybackStatus(message = "Connect Spotify to inspect Spotify Connect playback")
    }

    override suspend fun refreshSpotifyPlayback(): SpotifyPlaybackStatus = withContext(Dispatchers.IO) {
        val playback = spotifyAccount.playback()
        val status = SpotifyPlaybackStatus(
            activeDevice = playback.activeDevice, isPlaying = playback.isPlaying, title = playback.title,
            artist = playback.artist, album = playback.album, deviceName = playback.deviceName,
            progressMs = playback.progressMs, durationMs = playback.durationMs, message = playback.message,
        )
        mutableSpotifyPlayback.value = status
        status
    }

    override suspend fun controlSpotifyPlayback(command: SpotifyPlaybackCommand): SpotifyPlaybackStatus = withContext(Dispatchers.IO) {
        spotifyAccount.control(command.name)
        refreshSpotifyPlayback()
    }

    override suspend fun discoverDlnaDevices(): List<DlnaRenderer> = withContext(Dispatchers.IO) {
        val discovered = dlnaCast.discover().map { DlnaRenderer(it.id, it.name) }
        mutableDlnaRenderers.value = discovered
        mutablePlayerState.value = mutablePlayerState.value.copy(
            statusMessage = if (discovered.isEmpty()) "No DLNA/UPnP renderers found on the local network" else "Found ${discovered.size} DLNA/UPnP playback target(s)",
        )
        discovered
    }

    override suspend fun castCurrentTrack(rendererId: String) = withContext(Dispatchers.IO) {
        val track = mutablePlayerState.value.currentTrack ?: throw IllegalStateException("Start a track before casting")
        val remoteUrl = if (track.source == TrackSource.YOUTUBE_MUSIC) resolveDirectAudioUrl(track) else null
        dlnaCast.play(rendererId, track, remoteUrl)
        FxRuntime.start()
        val stoppedLocal = CompletableDeferred<Unit>()
        Platform.runLater {
            runCatching { mediaPlayer?.stop(); mediaPlayer?.dispose() }
            mediaPlayer = null
            stoppedLocal.complete(Unit)
        }
        stoppedLocal.await()
        activeCastRendererId = rendererId
        castEndHandledTrackKey = null
        val targetName = mutableDlnaRenderers.value.firstOrNull { it.id == rendererId }?.name ?: "DLNA device"
        mutablePlayerState.value = mutablePlayerState.value.copy(
            isPlaying = true, isLoading = false,
            statusMessage = "Casting ${track.title} to $targetName",
        )
        startCastPositionPolling()
        persistPlayerState()
    }

    override suspend fun stopCasting() = withContext(Dispatchers.IO) {
        dlnaCast.stop()
        activeCastRendererId = null
        castPositionTask?.cancel(false); castPositionTask = null
        mutablePlayerState.value = mutablePlayerState.value.copy(
            isPlaying = false,
            statusMessage = "DLNA casting stopped. Press Play to continue locally.",
        )
        persistPlayerState()
    }

    private fun startCastPositionPolling() {
        castPositionTask?.cancel(false)
        castPositionTask = castPollScheduler.scheduleWithFixedDelay({
            val rendererId = activeCastRendererId
            if (rendererId != null) {
                runCatching { dlnaCast.positionMs() }.getOrNull()?.let { rawPosition ->
                    if (activeCastRendererId == rendererId) {
                        val before = mutablePlayerState.value
                        val position = if (before.durationMs > 0L) rawPosition.coerceAtMost(before.durationMs) else rawPosition
                        mutablePlayerState.value = before.copy(positionMs = position)
                        if (before.durationMs > 0L && before.isPlaying && position >= before.durationMs - 1_000L) {
                            val key = before.currentTrack?.stableKey
                            if (key != null && castEndHandledTrackKey != key) {
                                castEndHandledTrackKey = key
                                ioScope.launch {
                                    if (activeCastRendererId == rendererId && mutablePlayerState.value.currentTrack?.stableKey == key) playNext()
                                }
                            }
                        }
                    }
                }
            }
        }, 1, 2, TimeUnit.SECONDS)
    }

    override suspend fun recognizeLocalAudio(): MusicRecognitionResult? = withContext(Dispatchers.IO) {
        val file = musicRecognition.chooseAudioFile() ?: return@withContext null
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Identifying ${file.name}…")
        try {
            val result = musicRecognition.identify(file)
            mutableRecognitionResult.value = result
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = result.message)
            result
        } catch (failure: Throwable) {
            val result = MusicRecognitionResult(file.nameWithoutExtension, "", confidence = 0f, message = failure.message ?: "Audio recognition failed")
            mutableRecognitionResult.value = result
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = result.message)
            throw failure
        }
    }

    override suspend fun recognizeAmbientAudio(): MusicRecognitionResult? = withContext(Dispatchers.IO) {
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Listening for 12 seconds…")
        try {
            val result = musicRecognition.recordAmbientAndIdentify(12)
            mutableRecognitionResult.value = result
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = result.message)
            result
        } catch (failure: Throwable) {
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = failure.message ?: "Ambient recognition failed")
            throw failure
        }
    }

    override suspend fun search(query: String, category: SearchCategory): List<MusicTrack> = withContext(Dispatchers.IO) {
        val filter = when (category) {
            SearchCategory.SONGS -> SearchFilter.FILTER_SONG
            SearchCategory.VIDEOS -> SearchFilter.FILTER_VIDEO
            SearchCategory.ALBUMS -> SearchFilter.FILTER_ALBUM
            SearchCategory.ARTISTS -> SearchFilter.FILTER_ARTIST
            SearchCategory.PLAYLISTS -> SearchFilter.FILTER_COMMUNITY_PLAYLIST
            SearchCategory.PODCASTS -> SearchFilter.FILTER_PODCAST
        }
        val response = YouTube.search(query = query, filter = filter, useAccountContext = false).getOrThrow()
        response.items.mapNotNull(::toMusicTrack)
    }

    override suspend fun loadHomeFeed(): List<MusicTrack> = withContext(Dispatchers.IO) {
        YouTube.home().getOrThrow().sections
            .flatMap { it.items }
            .mapNotNull(::toMusicTrack)
            .distinctBy { it.stableKey }
            .take(60)
    }

    override suspend fun loadCollection(track: MusicTrack): BrowseCollection = withContext(Dispatchers.IO) {
        when (track.kind) {
            MusicKind.ALBUM -> {
                val page = YouTube.album(track.id).getOrThrow()
                BrowseCollection(
                    id = track.id,
                    title = page.album.title,
                    subtitle = page.album.artists.orEmpty().joinToString(", ") { it.name }.ifBlank { track.artist },
                    artworkUrl = page.album.thumbnail,
                    kind = MusicKind.ALBUM,
                    tracks = page.songs.mapNotNull(::toMusicTrack),
                )
            }
            MusicKind.PLAYLIST -> {
                val page = YouTube.playlist(track.id.removePrefix("VL")).getOrThrow()
                BrowseCollection(
                    id = track.id,
                    title = page.playlist.title,
                    subtitle = page.playlist.author?.name ?: "YouTube Music playlist",
                    artworkUrl = page.playlist.thumbnail ?: track.artworkUrl,
                    kind = MusicKind.PLAYLIST,
                    tracks = page.songs.mapNotNull(::toMusicTrack),
                )
            }
            MusicKind.ARTIST -> {
                val page = YouTube.artist(track.id).getOrThrow()
                BrowseCollection(
                    id = track.id,
                    title = page.artist.title,
                    subtitle = page.artist.subscriberCountText ?: "Artist",
                    artworkUrl = page.artist.thumbnail ?: track.artworkUrl,
                    kind = MusicKind.ARTIST,
                    tracks = page.sections.flatMap { it.items }.mapNotNull(::toMusicTrack).distinctBy { it.stableKey },
                )
            }
            MusicKind.PODCAST -> {
                val page = YouTube.podcast(track.id).getOrThrow()
                BrowseCollection(
                    id = track.id,
                    title = page.podcast.title,
                    subtitle = page.podcast.author?.name ?: "Podcast",
                    artworkUrl = page.podcast.thumbnail ?: track.artworkUrl,
                    kind = MusicKind.PODCAST,
                    tracks = page.episodes.mapNotNull(::toMusicTrack),
                )
            }
            else -> throw IllegalArgumentException("${track.title} is not a browseable collection")
        }
    }

    override suspend fun loadLocalLibrary(): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (!Files.isRegularFile(libraryPath)) return@withContext emptyList()
        val paths = runCatching { Files.readAllLines(libraryPath).map { it.trim() }.filter(String::isNotBlank) }.getOrDefault(emptyList())
        tracksForPaths(paths.map { Paths.get(it) }.filter { Files.isRegularFile(it) })
    }

    override suspend fun importLocalMusic(): List<MusicTrack> {
        val selected = chooseLocalMusic() ?: return loadLocalLibrary()
        val allPaths = withContext(Dispatchers.IO) {
            val selectedFiles = selected.flatMap { file ->
                if (file.isDirectory) {
                    runCatching {
                        Files.walk(file.toPath()).use { paths ->
                            paths.filter { path -> Files.isRegularFile(path) && path.extensionLowercase() in supportedExtensions }.toList()
                        }
                    }.getOrDefault(emptyList())
                } else if (file.isFile && file.extension.lowercase() in supportedExtensions) listOf(file.toPath()) else emptyList()
            }
            val existing = if (Files.isRegularFile(libraryPath)) {
                runCatching { Files.readAllLines(libraryPath).map { it.trim() }.filter(String::isNotBlank) }.getOrDefault(emptyList())
            } else emptyList()
            (existing + selectedFiles.map { it.toAbsolutePath().normalize().toString() }).distinct()
                .filter { Files.isRegularFile(Paths.get(it)) }.also(::persistLibrary)
        }
        return withContext(Dispatchers.IO) { tracksForPaths(allPaths.map { Paths.get(it) }) }
    }

    override suspend fun removeFromLocalLibrary(trackKey: String) = withContext(Dispatchers.IO) {
        val indexed = loadLocalLibrary().firstOrNull { it.stableKey == trackKey }
            ?: return@withContext
        val location = indexed.location ?: return@withContext
        val normalized = Paths.get(location).toAbsolutePath().normalize().toString()
        val remaining = if (Files.isRegularFile(libraryPath)) {
            runCatching { Files.readAllLines(libraryPath).map(String::trim).filter(String::isNotBlank) }
                .getOrDefault(emptyList())
                .filterNot { raw ->
                    runCatching { Paths.get(raw).toAbsolutePath().normalize().toString() == normalized }.getOrDefault(false)
                }
        } else emptyList()
        persistLibrary(remaining)
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Removed ${indexed.title} from the desktop library; the original file was not deleted.")
    }

    override suspend fun play(track: MusicTrack) {
        if (!track.canPlay) {
            val url = track.location ?: if (track.source == TrackSource.YOUTUBE_MUSIC) "https://music.youtube.com/watch?v=${track.id}" else null
            if (!url.isNullOrBlank()) runCatching { Desktop.getDesktop().browse(URI(url)) }
                .onSuccess { mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Opened ${track.title} in your browser") }
                .onFailure { mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Could not open this item: ${it.message}") }
            return
        }
        val existingQueue = mutableQueueState.value
        val existingIndex = existingQueue.tracks.indexOfFirst { it.stableKey == track.stableKey }
        if (existingIndex >= 0) {
            mutableQueueState.value = existingQueue.copy(currentIndex = existingIndex)
        } else {
            mutableQueueState.value = existingQueue.copy(tracks = listOf(track), currentIndex = 0)
        }
        persistQueue()
        rememberPlayed(track)
        val castRenderer = activeCastRendererId
        if (castRenderer != null) {
            try {
                val remoteUrl = if (track.source == TrackSource.YOUTUBE_MUSIC) resolveDirectAudioUrl(track) else null
                withContext(Dispatchers.IO) { dlnaCast.play(castRenderer, track, remoteUrl) }
                castEndHandledTrackKey = null
                mutablePlayerState.value = mutablePlayerState.value.copy(
                    currentTrack = track, isPlaying = true, isLoading = false, positionMs = 0L,
                    durationMs = track.durationMs ?: 0L,
                    statusMessage = "Casting ${track.title} to ${mutableDlnaRenderers.value.firstOrNull { it.id == castRenderer }?.name ?: "DLNA device"}",
                )
                persistPlayerState()
                return
            } catch (failure: Throwable) {
                activeCastRendererId = null
                castPositionTask?.cancel(false); castPositionTask = null
                withContext(Dispatchers.IO) { runCatching { dlnaCast.stop() } }
                mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Casting failed; trying local playback: ${failure.message ?: "DLNA error"}")
            }
        }
        mutablePlayerState.value = mutablePlayerState.value.copy(
            currentTrack = track, isPlaying = false, isLoading = true, positionMs = 0L,
            durationMs = track.durationMs ?: 0L,
            statusMessage = if (track.source == TrackSource.LOCAL) "Opening local audio…" else "Resolving YouTube Music stream…",
        )
        try {
            val localPath = if (track.source == TrackSource.LOCAL) {
                Paths.get(track.location ?: error("The local track path is missing")).toAbsolutePath().normalize()
            } else null
            val streamUri = when (track.source) {
                TrackSource.LOCAL -> resolveLocalPlaybackUri(localPath!!)
                TrackSource.YOUTUBE_MUSIC -> resolveDirectAudioUrl(track)
            }
            try {
                openMedia(streamUri, track)
            } catch (firstFailure: Throwable) {
                // Retry common-container files through FFmpeg if the file's actual codec is not
                // supported by the JavaFX runtime (e.g. an MP3 container holding an unusual codec).
                if (localPath == null || localPath.extensionLowercase() !in directJavaFxExtensions) throw firstFailure
                val transcoded = withContext(Dispatchers.IO) { transcodeLocalAudio(localPath) }
                openMedia(transcoded.toUri().toString(), track)
            }
        } catch (failure: Throwable) {
            mutablePlayerState.value = mutablePlayerState.value.copy(
                isPlaying = false, isLoading = false,
                statusMessage = failure.message?.takeIf(String::isNotBlank) ?: "Playback failed. Try another track.",
            )
        }
    }

    override suspend fun playQueue(tracks: List<MusicTrack>, startIndex: Int) {
        val playable = tracks.filter { it.canPlay }
        if (playable.isEmpty()) {
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "There are no playable tracks in this queue")
            return
        }
        val index = startIndex.coerceIn(0, playable.lastIndex)
        mutableQueueState.value = mutableQueueState.value.copy(tracks = playable, currentIndex = index)
        persistQueue()
        play(playable[index])
    }

    override suspend fun queueTrack(track: MusicTrack, playNext: Boolean) {
        if (!track.canPlay) {
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Only playable tracks can be queued")
            return
        }
        val snapshot = mutableQueueState.value
        val list = snapshot.tracks.toMutableList()
        if (list.any { it.stableKey == track.stableKey }) {
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Track is already in the queue")
            return
        }
        val insertionIndex = if (playNext && snapshot.currentIndex >= 0) snapshot.currentIndex + 1 else list.size
        list.add(insertionIndex.coerceIn(0, list.size), track)
        mutableQueueState.value = snapshot.copy(tracks = list)
        persistQueue()
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = if (playNext) "Queued next: ${track.title}" else "Added to queue: ${track.title}")
    }

    override suspend fun playNext() {
        val queue = mutableQueueState.value
        if (queue.tracks.isEmpty()) return
        if (queue.repeatMode == RepeatMode.ONE) {
            val track = queue.currentTrack ?: return
            play(track)
            return
        }
        val nextIndex = when {
            queue.shuffleEnabled && queue.tracks.size > 1 -> {
                val choices = queue.tracks.indices.filter { it != queue.currentIndex }
                choices.randomOrNull() ?: queue.currentIndex
            }
            queue.currentIndex < queue.tracks.lastIndex -> queue.currentIndex + 1
            queue.repeatMode == RepeatMode.ALL -> 0
            else -> {
                mutablePlayerState.value = mutablePlayerState.value.copy(isPlaying = false, positionMs = mutablePlayerState.value.durationMs, statusMessage = "Queue finished")
                return
            }
        }
        mutableQueueState.value = queue.copy(currentIndex = nextIndex)
        persistQueue()
        play(queue.tracks[nextIndex])
    }

    override suspend fun playPrevious() {
        val queue = mutableQueueState.value
        if (queue.tracks.isEmpty()) return
        if (mutablePlayerState.value.positionMs > 3_000L) {
            seekTo(0L)
            return
        }
        val previous = if (queue.currentIndex > 0) queue.currentIndex - 1 else if (queue.repeatMode == RepeatMode.ALL) queue.tracks.lastIndex else 0
        mutableQueueState.value = queue.copy(currentIndex = previous)
        persistQueue()
        play(queue.tracks[previous])
    }

    override suspend fun removeFromQueue(trackKey: String) {
        val snapshot = mutableQueueState.value
        val removingIndex = snapshot.tracks.indexOfFirst { it.stableKey == trackKey }
        if (removingIndex < 0) return
        val wasCurrent = removingIndex == snapshot.currentIndex
        val oldCurrentKey = snapshot.currentTrack?.stableKey
        val nextTracks = snapshot.tracks.toMutableList().also { it.removeAt(removingIndex) }
        val nextIndex = when {
            nextTracks.isEmpty() -> -1
            oldCurrentKey != null && !wasCurrent -> nextTracks.indexOfFirst { it.stableKey == oldCurrentKey }.takeIf { it >= 0 } ?: 0
            else -> removingIndex.coerceAtMost(nextTracks.lastIndex)
        }
        mutableQueueState.value = snapshot.copy(tracks = nextTracks, currentIndex = nextIndex)
        persistQueue()
        if (wasCurrent) {
            if (nextIndex >= 0) play(nextTracks[nextIndex]) else stop()
        }
    }

    override suspend fun moveQueueItem(trackKey: String, offset: Int) {
        val snapshot = mutableQueueState.value
        val from = snapshot.tracks.indexOfFirst { it.stableKey == trackKey }
        val to = from + offset
        if (from < 0 || to !in snapshot.tracks.indices) return
        val tracks = snapshot.tracks.toMutableList()
        val moved = tracks.removeAt(from)
        tracks.add(to, moved)
        val currentKey = snapshot.currentTrack?.stableKey
        val currentIndex = if (currentKey == null) snapshot.currentIndex else tracks.indexOfFirst { it.stableKey == currentKey }
        mutableQueueState.value = snapshot.copy(tracks = tracks, currentIndex = currentIndex)
        persistQueue()
    }

    override suspend fun clearQueue() {
        mutableQueueState.value = PlaybackQueueSnapshot(shuffleEnabled = mutableQueueState.value.shuffleEnabled, repeatMode = mutableQueueState.value.repeatMode)
        persistQueue()
        stop()
    }

    override suspend fun moveTrackInPlaylist(playlistId: String, trackKey: String, offset: Int) = withContext(Dispatchers.IO) {
        mutablePlaylists.value = mutablePlaylists.value.map { playlist ->
            if (playlist.id != playlistId) return@map playlist
            val from = playlist.tracks.indexOfFirst { it.stableKey == trackKey }
            val to = from + offset
            if (from < 0 || to !in playlist.tracks.indices) return@map playlist
            val tracks = playlist.tracks.toMutableList()
            val moved = tracks.removeAt(from)
            tracks.add(to, moved)
            playlist.copy(tracks = tracks)
        }
        persistPlaylists()
    }

    override suspend fun toggleShuffle() {
        mutableQueueState.value = mutableQueueState.value.copy(shuffleEnabled = !mutableQueueState.value.shuffleEnabled)
        persistQueue()
    }

    override suspend fun cycleRepeatMode() {
        val next = when (mutableQueueState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        mutableQueueState.value = mutableQueueState.value.copy(repeatMode = next)
        persistQueue()
    }

    override suspend fun togglePlayback() {
        val castRenderer = activeCastRendererId
        if (castRenderer != null) {
            val currentlyPlaying = mutablePlayerState.value.isPlaying
            withContext(Dispatchers.IO) { if (currentlyPlaying) dlnaCast.pause() else dlnaCast.resume() }
            mutablePlayerState.value = mutablePlayerState.value.copy(
                isPlaying = !currentlyPlaying,
                statusMessage = if (currentlyPlaying) "Casting paused" else "Casting resumed",
            )
            persistPlayerState()
            return
        }
        val player = mediaPlayer
        if (player == null) {
            // After restarting, the saved track is shown paused. Play restores that track and
            // then seeks back to the last persisted position instead of showing a dead control.
            val remembered = mutablePlayerState.value.currentTrack
            if (remembered != null) {
                val resumePosition = mutablePlayerState.value.positionMs
                play(remembered)
                if (resumePosition > 1_000L) seekTo(resumePosition)
            } else {
                mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Choose a track first")
            }
            return
        }
        FxRuntime.start()
        val done = CompletableDeferred<Unit>()
        Platform.runLater {
            runCatching {
                if (mutablePlayerState.value.isPlaying) {
                    player.pause()
                    mutablePlayerState.value = mutablePlayerState.value.copy(isPlaying = false, statusMessage = "Paused")
                    persistPlayerState()
                } else {
                    if (mutablePlayerState.value.durationMs > 0L && mutablePlayerState.value.positionMs >= mutablePlayerState.value.durationMs - 500L) {
                        player.seek(Duration.ZERO)
                        mutablePlayerState.value = mutablePlayerState.value.copy(positionMs = 0L)
                    }
                    player.play()
                    mutablePlayerState.value = mutablePlayerState.value.copy(isPlaying = true, statusMessage = "Playing")
                    persistPlayerState()
                }
            }.onFailure { mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = it.message ?: "Playback control failed") }
            done.complete(Unit)
        }
        done.await()
    }

    override suspend fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        if (activeCastRendererId != null) {
            withContext(Dispatchers.IO) { dlnaCast.seek(target) }
            val duration = mutablePlayerState.value.durationMs
            mutablePlayerState.value = mutablePlayerState.value.copy(positionMs = if (duration > 0L) target.coerceAtMost(duration) else target)
            persistPlayerState()
            return
        }
        FxRuntime.start()
        val done = CompletableDeferred<Unit>()
        Platform.runLater {
            mediaPlayer?.seek(Duration.millis(target.toDouble()))
            val duration = mutablePlayerState.value.durationMs
            mutablePlayerState.value = mutablePlayerState.value.copy(positionMs = if (duration > 0L) target.coerceAtMost(duration) else target)
            persistPlayerState()
            done.complete(Unit)
        }
        done.await()
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        mutablePlayerState.value = mutablePlayerState.value.copy(volume = normalized)
        mutablePreferences.value = mutablePreferences.value.copy(volume = normalized)
        if (activeCastRendererId != null) withContext(Dispatchers.IO) { dlnaCast.setVolume(normalized) }
        FxRuntime.start()
        val done = CompletableDeferred<Unit>()
        Platform.runLater { mediaPlayer?.volume = normalized.toDouble(); done.complete(Unit) }
        done.await()
    }

    override suspend fun stop() {
        if (activeCastRendererId != null) {
            withContext(Dispatchers.IO) { dlnaCast.stop() }
            activeCastRendererId = null
            castPositionTask?.cancel(false); castPositionTask = null
        }
        FxRuntime.start()
        val done = CompletableDeferred<Unit>()
        Platform.runLater {
            runCatching { mediaPlayer?.stop(); mediaPlayer?.dispose() }
            mediaPlayer = null
            mutablePlayerState.value = mutablePlayerState.value.copy(currentTrack = null, isPlaying = false, isLoading = false, positionMs = 0L, durationMs = 0L, statusMessage = "Playback stopped")
            persistPlayerState()
            done.complete(Unit)
        }
        done.await()
    }

    override suspend fun createPlaylist(name: String): MusicPlaylist = withContext(Dispatchers.IO) {
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "Playlist name cannot be empty" }
        require(mutablePlaylists.value.none { it.name.equals(clean, ignoreCase = true) }) { "A playlist with that name already exists" }
        val playlist = MusicPlaylist(id = UUID.randomUUID().toString(), name = clean, createdAtEpochMs = System.currentTimeMillis())
        mutablePlaylists.value = mutablePlaylists.value + playlist
        persistPlaylists()
        playlist
    }

    override suspend fun renamePlaylist(id: String, name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "Playlist name cannot be empty" }
        require(mutablePlaylists.value.none { it.id != id && it.name.equals(clean, ignoreCase = true) }) { "A playlist with that name already exists" }
        mutablePlaylists.value = mutablePlaylists.value.map { if (it.id == id) it.copy(name = clean) else it }
        persistPlaylists()
    }

    override suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        mutablePlaylists.value = mutablePlaylists.value.filterNot { it.id == id }
        persistPlaylists()
    }

    override suspend fun addToPlaylist(playlistId: String, track: MusicTrack) = withContext(Dispatchers.IO) {
        require(track.canPlay) { "Only playable tracks can be added to a playlist" }
        mutablePlaylists.value = mutablePlaylists.value.map { playlist ->
            if (playlist.id == playlistId && playlist.tracks.none { it.stableKey == track.stableKey }) playlist.copy(tracks = playlist.tracks + track) else playlist
        }
        persistPlaylists()
    }

    override suspend fun removeFromPlaylist(playlistId: String, trackKey: String) = withContext(Dispatchers.IO) {
        mutablePlaylists.value = mutablePlaylists.value.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(tracks = playlist.tracks.filterNot { it.stableKey == trackKey }) else playlist
        }
        persistPlaylists()
    }

    override suspend fun loadLyrics(track: MusicTrack) = withContext(Dispatchers.IO) {
        val selectedProvider = mutablePreferences.value.lyricsProvider
        mutableLyrics.value = LyricsSheet(track.stableKey, track.title, track.artist, "Searching lyrics providers…", false, emptyList())
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Searching lyrics providers…")
        val durationSeconds = track.durationMs?.div(1000L)?.toInt() ?: -1
        val videoId = track.id.takeIf {
            track.source == TrackSource.YOUTUBE_MUSIC && track.kind in setOf(MusicKind.SONG, MusicKind.VIDEO, MusicKind.EPISODE)
        }
        val providerOrder = if (selectedProvider == "Auto") {
            normalizeLyricsProviderOrder(mutablePreferences.value.lyricsProviderOrder)
                .filter { it in mutablePreferences.value.enabledLyricsProviders }
        } else {
            listOf(selectedProvider)
        }
        val providerPermits = Semaphore(MAX_CONCURRENT_LYRICS_PROVIDERS)
        val best = supervisorScope {
            val jobs = providerOrder.mapIndexed { index, provider ->
                async(Dispatchers.IO) {
                    providerPermits.withPermit {
                        val content = withTimeoutOrNull(LYRICS_PROVIDER_TIMEOUT_MS) {
                            fetchLyricsFromProvider(track, provider, videoId, durationSeconds)
                        }
                        content?.let(::parseLyricsFromProvider)?.let { parsed ->
                            DesktopLyricsCandidate(index, provider, parsed)
                        }
                    }
                }
            }.toMutableList()
            val originalJobs = jobs.toList()
            val completed = mutableSetOf<Int>()
            var bestCandidate: DesktopLyricsCandidate? = null
            var deadline = System.nanoTime() / 1_000_000L + LYRICS_FETCH_TIMEOUT_MS
            try {
                while (jobs.isNotEmpty()) {
                    val remaining = deadline - System.nanoTime() / 1_000_000L
                    if (remaining <= 0L) break
                    val finishedResult = withTimeoutOrNull(remaining) {
                        select<Pair<Deferred<DesktopLyricsCandidate?>, DesktopLyricsCandidate?>> {
                            jobs.forEach { deferred -> deferred.onAwait { candidate -> deferred to candidate } }
                        }
                    } ?: break
                    val finished = finishedResult.first
                    val candidate = finishedResult.second
                    completed += originalJobs.indexOf(finished)
                    jobs.remove(finished)
                    if (candidate != null) {
                        val previous = bestCandidate
                        if (previous == null || candidate.parsed.quality > previous.parsed.quality ||
                            (candidate.parsed.quality == previous.parsed.quality && candidate.order < previous.order)
                        ) {
                            bestCandidate = candidate
                        }
                        val grace = if (bestCandidate?.parsed?.quality == 2) LYRICS_WORD_SYNC_GRACE_MS else LYRICS_QUALITY_GRACE_MS
                        deadline = minOf(deadline, System.nanoTime() / 1_000_000L + grace)
                        val selected = bestCandidate
                        if (selected?.parsed?.quality == 2 && (0 until selected.order).all { it in completed }) break
                    }
                }
                bestCandidate
            } finally {
                originalJobs.forEach { it.cancel() }
            }
        }
        if (best != null) {
            mutableLyrics.value = LyricsSheet(
                trackKey = track.stableKey,
                title = track.title,
                artist = track.artist,
                source = best.provider,
                synchronized = best.parsed.synchronized,
                lines = (if (mutablePreferences.value.showLyricsTranslation) DesktopLyricsTranslator.enrich(best.parsed.lines) else best.parsed.lines)
                    .map { line ->
                        if (!mutablePreferences.value.showLyricsRomanization || !line.romanizedText.isNullOrBlank()) line
                        else line.copy(romanizedText = DesktopLyricsRomanizer.romanize(line.text))
                    },
            )
            mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Lyrics loaded from ${best.provider}")
        } else {
            mutableLyrics.value = LyricsSheet(track.stableKey, track.title, track.artist, selectedProvider, false, emptyList())
            mutablePlayerState.value = mutablePlayerState.value.copy(
                statusMessage = if (selectedProvider == "Auto") "No lyrics found (providers timed out or returned no match)" else "Lyrics unavailable from $selectedProvider"
            )
        }
    }

    private suspend fun fetchLyricsFromProvider(
        track: MusicTrack,
        provider: String,
        videoId: String?,
        durationSeconds: Int,
    ): String? = try {
        when (provider) {
            "YouTube Music" -> videoId?.let { id ->
                val next = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
                next.lyricsEndpoint?.let { endpoint -> YouTube.lyrics(endpoint).getOrThrow() }
            }
            "YouTube Subtitle" -> videoId?.let { YouTube.transcript(it).getOrNull() }
            "SimpMusic" -> videoId?.let { SimpMusicLyrics.getLyrics(it, durationSeconds.coerceAtLeast(0)).getOrNull() }
            "Unison" -> Unison.getLyrics(
                videoId = videoId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = durationSeconds,
            ).getOrNull()
            "YouLyPlus" -> YouLyPlus.getLyrics(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = durationSeconds,
            ).getOrNull()
            "BetterLyrics" -> BetterLyrics.getLyrics(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = durationSeconds,
            ).getOrNull()
            "BetterLyrics Portato" -> BetterLyrics.getPortatoLyrics(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = durationSeconds,
            ).getOrNull()
            "LRCLIB" -> LrcLib.getLyrics(
                title = track.title,
                artist = track.artist,
                duration = durationSeconds,
                album = track.album,
            ).getOrNull()
            "KuGou" -> KuGou.getLyrics(track.title, track.artist, durationSeconds).getOrNull()
            "Megalobiz" -> loadMegalobizLyrics(track)
            "Paxsenix: Apple Music" -> PaxsenixLyrics.getAppleMusicLyrics(track.title, track.artist, durationSeconds).getOrNull()
            "Paxsenix: Spotify" -> PaxsenixLyrics.getSpotifyLyrics(track.title, track.artist, durationSeconds).getOrNull()
            "Paxsenix: Musixmatch" -> PaxsenixLyrics.getMusixmatchLyrics(track.title, track.artist, durationSeconds).getOrNull()
            "Paxsenix", "Paxsenix (Auto)" -> PaxsenixLyrics.getLyrics(track.title, track.artist, durationSeconds).getOrNull()
            else -> null
        }?.trim()?.takeIf(String::isNotBlank)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun loadMegalobizLyrics(track: MusicTrack): String? {
        fun fetchText(endpoint: String): String? {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ArchiveTune/15.1")
            return try {
                if (connection.responseCode !in 200..299) null
                else connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        val query = urlEncode("${track.artist} ${track.title}")
        val searchHtml = fetchText("https://www.megalobiz.com/searchall?qry=$query") ?: return null
        val lrcPath = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""").find(searchHtml)?.groupValues?.getOrNull(1) ?: return null
        val detailHtml = fetchText("https://www.megalobiz.com$lrcPath") ?: return null
        val rawLrc = Regex("""id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            .find(detailHtml)?.groupValues?.getOrNull(1) ?: detailHtml
        val cleaned = rawLrc
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .trim()
        return cleaned.takeIf { Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(it) }
    }

    private fun parseLyricsFromProvider(content: String): ParsedProviderLyrics? {
        val trimmed = content.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val looksLikeTtml = trimmed.startsWith("<tt", ignoreCase = true) ||
            trimmed.startsWith("<?xml", ignoreCase = true) || trimmed.contains(Regex("<(?:[A-Za-z_][\\w.-]*:)?tt(?:\\s|>)", RegexOption.IGNORE_CASE))
        if (looksLikeTtml) {
            val parsed = TTMLParser.parseTTML(content)
            val lines = parsed.mapNotNull { line ->
                line.text.trim().takeIf(String::isNotBlank)?.let { text ->
                    val words = line.words.filterNot { it.isBackground }.mapNotNull { word ->
                        word.text.takeIf(String::isNotBlank)?.let { textPart ->
                            LyricWord((word.startTime * 1000.0).toLong(), (word.endTime * 1000.0).toLong(), textPart)
                        }
                    }
                    LyricLine(
                        timeMs = (line.startTime * 1000.0).toLong(),
                        text = text,
                        words = words,
                        translation = line.providerTranslationText?.trim()?.takeIf(String::isNotBlank),
                        romanizedText = line.providerRomanizedText?.trim()?.takeIf(String::isNotBlank),
                    )
                }
            }
            if (lines.isEmpty()) return null
            val hasWordTiming = lines.any { line -> line.words.any { it.endTimeMs > it.timeMs } }
            return ParsedProviderLyrics(lines, synchronized = true, quality = if (hasWordTiming) 2 else 1)
        }
        val timed = Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(content)
        val hasInlineWordTiming = Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>").containsMatchIn(content)
        val lines = when {
            timed && hasInlineWordTiming -> parseSyncedLyricsWithWords(content)
            timed -> parseSyncedLyrics(content)
            else -> content.lines().map { LyricLine(null, it.trim()) }.filter { it.text.isNotBlank() }
        }
        if (lines.isEmpty()) return null
        val wordTimed = lines.any { line -> line.words.isNotEmpty() }
        return ParsedProviderLyrics(lines, synchronized = timed, quality = if (wordTimed) 2 else if (timed) 1 else 0)
    }

    override suspend fun download(track: MusicTrack): MusicTrack = withContext(Dispatchers.IO) {
        require(track.canPlay) { "Only playable tracks can be downloaded" }
        Files.createDirectories(downloadsDir)
        val extension = if (track.source == TrackSource.LOCAL) track.location?.substringAfterLast('.', "mp3") else "m4a"
        val safeName = "${track.artist} - ${track.title}".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(160).ifBlank { "ArchiveTune track" }
        val destination = uniqueDestination(downloadsDir.resolve("$safeName.${extension.orEmpty().ifBlank { "m4a" }}"))
        if (track.source == TrackSource.LOCAL) {
            val sourcePath = Paths.get(track.location ?: error("Local track path missing"))
            Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING)
        } else {
            val streamUrl = resolveDirectAudioUrl(track)
            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 ArchiveTune Desktop")
            connection.inputStream.use { input -> Files.newOutputStream(destination).use { output -> input.copyTo(output) } }
            connection.disconnect()
        }
        val downloaded = MusicTrack(
            id = destination.toAbsolutePath().toString(), title = track.title, artist = track.artist,
            album = track.album, durationMs = track.durationMs, artworkUrl = track.artworkUrl,
            source = TrackSource.LOCAL, kind = MusicKind.SONG, location = destination.toAbsolutePath().toString(),
        )
        refreshDownloads()
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Downloaded ${track.title}")
        downloaded
    }

    override suspend fun deleteDownload(trackKey: String) = withContext(Dispatchers.IO) {
        val target = mutableDownloadedTracks.value.firstOrNull { it.stableKey == trackKey }
            ?: throw IllegalArgumentException("This download is no longer in the download list")
        val path = Paths.get(target.location ?: throw IllegalArgumentException("The download path is missing")).toAbsolutePath().normalize()
        val safeRoot = downloadsDir.toAbsolutePath().normalize()
        require(path.startsWith(safeRoot)) { "Refusing to delete a file outside ArchiveTune's downloads folder" }
        Files.deleteIfExists(path)
        refreshDownloads()
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Deleted download: ${target.title}")
    }

    override suspend fun subscribePodcast(track: MusicTrack) = withContext(Dispatchers.IO) {
        require(track.source == TrackSource.YOUTUBE_MUSIC && track.kind == MusicKind.PODCAST) {
            "Only YouTube Music podcast collections can be followed."
        }
        val next = (mutableSubscribedPodcasts.value.filterNot { it.stableKey == track.stableKey } + track)
            .sortedBy { it.title.lowercase() }
        mutableSubscribedPodcasts.value = next
        persistPodcastSubscriptions().getOrThrow()
        mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Following podcast: ${track.title}")
    }

    override suspend fun unsubscribePodcast(trackKey: String) = withContext(Dispatchers.IO) {
        val removed = mutableSubscribedPodcasts.value.firstOrNull { it.stableKey == trackKey }
        mutableSubscribedPodcasts.value = mutableSubscribedPodcasts.value.filterNot { it.stableKey == trackKey }
        persistPodcastSubscriptions().getOrThrow()
        if (removed != null) mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Unfollowed podcast: ${removed.title}")
    }

    override suspend fun savePreferences(value: DesktopPreferences) {
        val previousPreferences = mutablePreferences.value
        val wasScrobblingEnabled = previousPreferences.scrobblingEnabled
        val wasListenBrainzEnabled = previousPreferences.listenBrainzEnabled
        val wasLyricsRomanizationEnabled = previousPreferences.showLyricsRomanization
        val sanitized = value.copy(
            playbackSpeed = value.playbackSpeed.coerceIn(0.5f, 2f),
            volume = value.volume.coerceIn(0f, 1f),
            lyricsProviderOrder = normalizeLyricsProviderOrder(value.lyricsProviderOrder),
            enabledLyricsProviders = normalizeEnabledLyricsProviders(value.enabledLyricsProviders),
            discordApplicationId = value.discordApplicationId.trim().filter(Char::isDigit).take(24),
            spotifyClientId = value.spotifyClientId.trim().take(100),
            scheduledBackupIntervalHours = value.scheduledBackupIntervalHours.coerceIn(6, 168),
            equalizerGainsDb = value.equalizerGainsDb.take(10).map { it.coerceIn(-12f, 12f) }.let { it + List((10 - it.size).coerceAtLeast(0)) { 0f } },
        )
        mutablePreferences.value = sanitized
        if (!wasLyricsRomanizationEnabled && sanitized.showLyricsRomanization) {
            mutableLyrics.value?.let { sheet ->
                mutableLyrics.value = sheet.copy(lines = sheet.lines.map { line ->
                    if (!line.romanizedText.isNullOrBlank()) line
                    else line.copy(romanizedText = DesktopLyricsRomanizer.romanize(line.text))
                })
            }
        }
        mutablePlayerState.value = mutablePlayerState.value.copy(volume = sanitized.volume)
        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                setProperty("playbackSpeed", mutablePreferences.value.playbackSpeed.toString())
                setProperty("volume", mutablePreferences.value.volume.toString())
                setProperty("startMinimized", mutablePreferences.value.startMinimized.toString())
                setProperty("closeToTray", mutablePreferences.value.closeToTray.toString())
                setProperty("scrobblingEnabled", mutablePreferences.value.scrobblingEnabled.toString())
                setProperty("listenBrainzEnabled", mutablePreferences.value.listenBrainzEnabled.toString())
                setProperty("lastFmUsername", mutablePreferences.value.lastFmUsername)
                setProperty("theme", mutablePreferences.value.theme)
                setProperty("lyricsProvider", mutablePreferences.value.lyricsProvider)
                setProperty("lyricsProviderOrder", mutablePreferences.value.lyricsProviderOrder.joinToString("|"))
                setProperty("enabledLyricsProviders", mutablePreferences.value.enabledLyricsProviders.joinToString("|"))
                setProperty("showLyricsTranslation", mutablePreferences.value.showLyricsTranslation.toString())
                setProperty("showLyricsRomanization", mutablePreferences.value.showLyricsRomanization.toString())
                setProperty("equalizerEnabled", mutablePreferences.value.equalizerEnabled.toString())
                setProperty("equalizerGainsDb", mutablePreferences.value.equalizerGainsDb.joinToString(","))
                setProperty("discordPresenceEnabled", mutablePreferences.value.discordPresenceEnabled.toString())
                setProperty("discordApplicationId", mutablePreferences.value.discordApplicationId)
                setProperty("spotifyClientId", mutablePreferences.value.spotifyClientId)
                setProperty("scheduledBackupEnabled", mutablePreferences.value.scheduledBackupEnabled.toString())
                setProperty("scheduledBackupIntervalHours", mutablePreferences.value.scheduledBackupIntervalHours.toString())
            }
            runCatching { Files.createDirectories(dataRoot); Files.newOutputStream(preferencesPath).use { props.store(it, "ArchiveTune desktop preferences") } }
        }
        FxRuntime.start()
        val done = CompletableDeferred<Unit>()
        Platform.runLater {
            mediaPlayer?.rate = mutablePreferences.value.playbackSpeed.toDouble()
            mediaPlayer?.volume = mutablePreferences.value.volume.toDouble()
            applyEqualizer(mediaPlayer)
            done.complete(Unit)
        }
        done.await()
        mutablePlayerState.value.currentTrack?.let { track ->
            if (!wasScrobblingEnabled && sanitized.scrobblingEnabled) notifyLastFmNowPlaying(track, mutablePlayerState.value.durationMs)
            if (!wasListenBrainzEnabled && sanitized.listenBrainzEnabled) notifyListenBrainzNowPlaying(track, mutablePlayerState.value.durationMs)
        }
    }

    override suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val destination = chooseBackupFile(save = true) ?: return@withContext "Backup cancelled."
        writeBackupArchive(destination)
        "Backup saved: ${destination.absolutePath}"
    }

    private fun writeBackupArchive(destination: File) {
        val outputPath = destination.toPath().toAbsolutePath().normalize()
        Files.createDirectories(dataRoot)
        Files.createDirectories(downloadsDir)
        val dataFiles = listOf(libraryPath, playlistsPath, historyPath, queuePath, podcastsPath, playerStatePath, preferencesPath)
        val downloadFiles = Files.list(downloadsDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.toAbsolutePath().normalize() != outputPath }
                .filter { it.extensionLowercase() in supportedExtensions }
                .toList()
        }
        destination.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write("ArchiveTuneDesktopBackup\t1\nGenerated by ArchiveTune Desktop\n".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            dataFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("data/${file.fileName}"))
                if (file == preferencesPath && Files.isRegularFile(file)) {
                    val safePreferences = Properties()
                    Files.newInputStream(file).use(safePreferences::load)
                    // Never include personal credentials or account state in exported backups.
                    listOf("scrobblingEnabled", "listenBrainzEnabled", "lastFmUsername", "discordApplicationId", "spotifyClientId")
                        .forEach { key -> safePreferences.remove(key) }
                    val bytes = ByteArrayOutputStream()
                    safePreferences.store(bytes, "ArchiveTune backup preferences; account state omitted")
                    zip.write(bytes.toByteArray())
                } else if (Files.isRegularFile(file)) {
                    Files.newInputStream(file).use { it.copyTo(zip) }
                }
                zip.closeEntry()
            }
            downloadFiles.forEach { file ->
                val fileName = file.fileName.toString()
                if (isSafeBackupFileName(fileName)) {
                    zip.putNextEntry(ZipEntry("downloads/$fileName"))
                    Files.newInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun runAutomaticBackupIfDue() {
        val prefs = mutablePreferences.value
        if (!prefs.scheduledBackupEnabled || Thread.currentThread().isInterrupted) return
        val intervalHours = prefs.scheduledBackupIntervalHours.coerceIn(6, 168)
        val backupDirectory = dataRoot.resolve("backups")
        Files.createDirectories(backupDirectory)
        val latest = Files.list(backupDirectory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().matches(Regex("ArchiveTune-auto-\\d{8}-\\d{6}\\.zip")) }
                .max(Comparator.comparingLong<Path> { Files.getLastModifiedTime(it).toMillis() })
                .orElse(null)
        }
        val now = System.currentTimeMillis()
        if (latest != null && now - Files.getLastModifiedTime(latest).toMillis() < TimeUnit.HOURS.toMillis(intervalHours.toLong())) return
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val destination = backupDirectory.resolve("ArchiveTune-auto-$stamp.zip")
        val temporary = backupDirectory.resolve(".ArchiveTune-auto-$stamp.tmp")
        try {
            writeBackupArchive(temporary.toFile())
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
        }
        val archives = Files.list(backupDirectory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().matches(Regex("ArchiveTune-auto-\\d{8}-\\d{6}\\.zip")) }
                .sorted(Comparator.comparingLong<Path> { Files.getLastModifiedTime(it).toMillis() }.reversed())
                .toList()
        }
        archives.drop(10).forEach { runCatching { Files.deleteIfExists(it) } }
    }

    override suspend fun importBackup(): String = withContext(Dispatchers.IO) {
        val source = chooseBackupFile(save = false) ?: return@withContext "Restore cancelled."
        if (!confirmBackupRestore()) return@withContext "Restore cancelled."
        Files.createDirectories(dataRoot)
        val staging = dataRoot.resolve(".restore-staging-${UUID.randomUUID()}")
        val stageRoot = staging.toAbsolutePath().normalize()
        val allowedDataFiles = setOf(
            "desktop-library.txt", "desktop-playlists.db", "desktop-history.db",
            "desktop-queue.db", "desktop-podcasts.db", "desktop-player-state.db", "desktop-preferences.properties",
        )
        var sawValidManifest = false
        var totalBytes = 0L
        var entryCount = 0
        try {
            Files.createDirectories(stageRoot)
            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= 20_000) { "Backup contains too many entries." }
                    val normalizedName = entry.name.replace('\\', '/')
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val relativeName = when {
                        normalizedName == "manifest.txt" -> "manifest.txt"
                        normalizedName.startsWith("data/") && normalizedName.removePrefix("data/") in allowedDataFiles -> normalizedName
                        normalizedName.startsWith("downloads/") && isSafeBackupFileName(normalizedName.removePrefix("downloads/")) &&
                            normalizedName.substringAfterLast('.', "").lowercase() in supportedExtensions -> normalizedName
                        else -> null
                    }
                    if (relativeName == null) {
                        val discardBuffer = ByteArray(16 * 1024)
                        while (true) {
                            val skipped = zip.read(discardBuffer)
                            if (skipped < 0) break
                            totalBytes += skipped
                            require(totalBytes <= 2L * 1024L * 1024L * 1024L) { "Backup expands beyond the 2 GB safety limit." }
                        }
                        zip.closeEntry()
                        continue
                    }
                    val target = stageRoot.resolve(relativeName).normalize()
                    require(target.startsWith(stageRoot)) { "Unsafe path in backup: ${entry.name}" }
                    Files.createDirectories(target.parent)
                    var entryBytes = 0L
                    val maxEntryBytes = if (relativeName == "manifest.txt") 4096L else 768L * 1024L * 1024L
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= maxEntryBytes) { "A backup file exceeds its safety limit." }
                            require(totalBytes <= 2L * 1024L * 1024L * 1024L) { "Backup expands beyond the 2 GB safety limit." }
                            output.write(buffer, 0, read)
                        }
                    }
                    if (relativeName == "manifest.txt") {
                        val manifest = Files.readString(target, StandardCharsets.UTF_8)
                        sawValidManifest = manifest.startsWith("ArchiveTuneDesktopBackup\t1")
                    }
                    zip.closeEntry()
                }
            }
            require(sawValidManifest) { "This is not a recognized ArchiveTune desktop backup." }

            val stagedData = stageRoot.resolve("data")
            allowedDataFiles.forEach { fileName ->
                val stagedFile = stagedData.resolve(fileName)
                if (Files.isRegularFile(stagedFile)) {
                    val target = dataRoot.resolve(fileName)
                    try {
                        Files.move(stagedFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        Files.move(stagedFile, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            val stagedDownloads = stageRoot.resolve("downloads")
            if (Files.isDirectory(stagedDownloads)) {
                Files.list(stagedDownloads).use { stream ->
                    stream.filter { Files.isRegularFile(it) }.forEach { stagedFile ->
                        val target = uniqueDestination(downloadsDir.resolve(stagedFile.fileName.toString()))
                        Files.copy(stagedFile, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            restorePreferences()
            savePreferences(mutablePreferences.value)
            restorePlaylistsAndHistory()
            restoreQueue()
            restorePodcastSubscriptions()
            restorePlayerState()
            refreshDownloads()
            "Backup restored. Library references were restored without copying original local music; backed-up downloads were added to your current downloads."
        } finally {
            deleteRecursively(stageRoot)
        }
    }

    override suspend fun importAndroidBackup(): String = withContext(Dispatchers.IO) {
        val source = chooseAndroidBackupFile() ?: return@withContext "Android backup import cancelled."
        if (!confirmAndroidBackupImport()) return@withContext "Android backup import cancelled."
        Files.createDirectories(dataRoot)
        val stageRoot = Files.createTempDirectory(dataRoot, ".android-import-").toAbsolutePath().normalize()
        val allowed = setOf("settings.xml", "song.db", "song.db-wal")
        var totalBytes = 0L
        var entries = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 20_000) { "Android backup contains too many entries." }
                    val name = entry.name.replace('\\', '/')
                    val directName = name.takeIf { '/' !in it && it in allowed }
                    val target = directName?.let(stageRoot::resolve)
                    if (entry.isDirectory || target == null) {
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= 2L * 1024L * 1024L * 1024L) { "Android backup expands beyond the 2 GB safety limit." }
                        }
                        zip.closeEntry()
                        continue
                    }
                    var entryBytes = 0L
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= 1024L * 1024L * 1024L) { "An Android backup entry exceeds the 1 GB safety limit." }
                            require(totalBytes <= 2L * 1024L * 1024L * 1024L) { "Android backup expands beyond the 2 GB safety limit." }
                            output.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }

            val androidDb = stageRoot.resolve("song.db")
            var importedPlaylistCount = 0
            var importedTrackCount = 0
            var skippedLocalCount = 0
            val importedPlaylists = mutableListOf<MusicPlaylist>()
            if (Files.isRegularFile(androidDb)) {
                Class.forName("org.sqlite.JDBC")
                val trackById = LinkedHashMap<String, MusicTrack>()
                val artistBySong = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
                val libraryTracks = mutableListOf<MusicTrack>()
                val playlistRows = mutableListOf<Triple<String, String, Long>>()
                val playlistTrackRows = mutableListOf<Triple<String, String, Int>>()
                DriverManager.getConnection("jdbc:sqlite:${androidDb.toAbsolutePath()}").use { connection ->
                    fun hasTable(table: String): Boolean = connection.prepareStatement(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                    ).use { statement ->
                        statement.setString(1, table)
                        statement.executeQuery().use { it.next() }
                    }
                    fun resultSetValue(result: java.sql.ResultSet, key: String): String? {
                        val metadata = result.metaData
                        val index = (1..metadata.columnCount).firstOrNull { metadata.getColumnLabel(it).equals(key, true) } ?: return null
                        return result.getObject(index)?.toString()
                    }
                    fun resultSetBool(result: java.sql.ResultSet, key: String): Boolean =
                        resultSetValue(result, key)?.let { it.equals("true", true) || it == "1" } ?: false
                    fun resultSetLong(result: java.sql.ResultSet, key: String): Long? = resultSetValue(result, key)?.toLongOrNull()

                    require(hasTable("song")) { "The selected ZIP does not contain a readable ArchiveTune song.db database." }
                    if (hasTable("song_artist_map") && hasTable("artist")) runCatching {
                        connection.createStatement().executeQuery(
                            "SELECT sam.songId AS songId, sam.position AS position, a.name AS artistName " +
                                "FROM song_artist_map sam JOIN artist a ON a.id=sam.artistId ORDER BY sam.songId, sam.position",
                        ).use { result ->
                            while (result.next()) {
                                val songId = resultSetValue(result, "songId") ?: continue
                                val artistName = resultSetValue(result, "artistName")?.trim().orEmpty()
                                if (artistName.isNotBlank()) artistBySong.getOrPut(songId) { mutableListOf() }
                                    .add((resultSetLong(result, "position")?.toInt() ?: 0) to artistName)
                            }
                        }
                    }
                    connection.createStatement().executeQuery("SELECT * FROM song").use { result ->
                        while (result.next()) {
                            val id = resultSetValue(result, "id")?.trim().orEmpty()
                            if (id.isBlank()) continue
                            val local = resultSetBool(result, "isLocal")
                            val durationSeconds = resultSetLong(result, "duration") ?: -1L
                            val title = resultSetValue(result, "title")?.trim().orEmpty().ifBlank { id }
                            val artist = artistBySong[id].orEmpty().sortedBy { it.first }.map { it.second }.distinct().joinToString(", ")
                                .ifBlank { resultSetValue(result, "albumArtist")?.trim().orEmpty().ifBlank { "Unknown artist" } }
                            val isPodcast = resultSetBool(result, "isPodcast")
                            val isVideo = resultSetBool(result, "isMusicVideo")
                            val track = MusicTrack(
                                id = id,
                                title = title,
                                artist = artist,
                                album = resultSetValue(result, "albumName")?.trim()?.ifBlank { null },
                                durationMs = durationSeconds.takeIf { it > 0L }?.times(1000L),
                                artworkUrl = resultSetValue(result, "thumbnailUrl")?.trim()?.ifBlank { null },
                                source = TrackSource.YOUTUBE_MUSIC,
                                kind = when { isPodcast -> MusicKind.EPISODE; isVideo -> MusicKind.VIDEO; else -> MusicKind.SONG },
                                location = "https://music.youtube.com/watch?v=${URLEncoder.encode(id, StandardCharsets.UTF_8)}",
                            )
                            if (local) {
                                skippedLocalCount++
                                continue
                            }
                            if (!track.canPlay) continue
                            trackById[id] = track
                            if (resultSetBool(result, "liked") || (resultSetLong(result, "inLibrary") ?: 0L) > 0L) libraryTracks += track
                        }
                    }
                    if (hasTable("playlist") && hasTable("playlist_song_map")) {
                        connection.createStatement().executeQuery("SELECT * FROM playlist").use { result ->
                            while (result.next()) {
                                val id = resultSetValue(result, "id")?.trim().orEmpty()
                                val name = resultSetValue(result, "name")?.trim().orEmpty()
                                if (id.isBlank() || name.isBlank() || id == "LP_LIKED" || id == "LP_DOWNLOADED") continue
                                // Import all cached/created playlists with visible metadata, not auth or remote account state.
                                val createdRaw = resultSetValue(result, "createdAt")
                                val created = createdRaw?.toLongOrNull()?.let { stamp ->
                                    when {
                                        stamp >= 100_000_000_000L -> stamp // Room Long epoch milliseconds
                                        stamp > 0L -> stamp * 1000L // tolerate epoch seconds
                                        else -> null
                                    }
                                } ?: createdRaw?.let { runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
                                    ?: System.currentTimeMillis()
                                playlistRows += Triple(id, name, created)
                            }
                        }
                        connection.createStatement().executeQuery(
                            "SELECT playlistId, songId, position FROM playlist_song_map ORDER BY playlistId, position",
                        ).use { result ->
                            while (result.next()) {
                                val playlistId = resultSetValue(result, "playlistId") ?: continue
                                val songId = resultSetValue(result, "songId") ?: continue
                                val position = resultSetLong(result, "position")?.toInt() ?: 0
                                playlistTrackRows += Triple(playlistId, songId, position)
                            }
                        }
                    }
                }
                val tracksByPlaylist = playlistTrackRows.groupBy { it.first }
                playlistRows.forEach { (androidId, name, createdAt) ->
                    val tracks = tracksByPlaylist[androidId].orEmpty().sortedBy { it.third }
                        .mapNotNull { trackById[it.second] }.distinctBy { it.stableKey }
                    if (tracks.isEmpty() && tracksByPlaylist[androidId].isNullOrEmpty()) {
                        // Preserve empty user-created playlists; do not carry over unsupported remote-only entries.
                        importedPlaylists += MusicPlaylist("android-import-playlist-$androidId", "Android · $name", emptyList(), createdAt)
                    } else if (tracks.isNotEmpty()) {
                        importedPlaylists += MusicPlaylist("android-import-playlist-$androidId", "Android · $name", tracks, createdAt)
                    }
                }
                if (libraryTracks.isNotEmpty()) {
                    importedPlaylists += MusicPlaylist(
                        "android-import-library", "Android · Library", libraryTracks.distinctBy { it.stableKey }, System.currentTimeMillis(),
                    )
                }
                importedTrackCount = importedPlaylists.flatMap { it.tracks }.distinctBy { it.stableKey }.size
                importedPlaylistCount = importedPlaylists.size
                val retained = mutablePlaylists.value.filterNot { it.id.startsWith("android-import-") }
                mutablePlaylists.value = retained + importedPlaylists
                persistPlaylists()
            }
            val importedSettings = readAndroidSettings(stageRoot.resolve("settings.xml"))
            if (importedSettings.isNotEmpty()) {
                val old = mutablePreferences.value
                val importedTheme = when {
                    importedSettings["pureBlack"]?.equals("true", true) == true -> "Obsidian"
                    importedSettings["darkMode"]?.equals("on", true) == true -> "Dark"
                    importedSettings["darkMode"]?.equals("off", true) == true -> "Light"
                    else -> old.theme
                }
                val importedProvider = importedSettings["lyricsProvider"]?.let { if (it == "Paxsenix") "Paxsenix (Auto)" else it }
                val allowedProviders = setOf("Auto", "YouTube Music", "YouTube Subtitle", "SimpMusic", "Unison", "YouLyPlus", "BetterLyrics", "BetterLyrics Portato", "LRCLIB", "KuGou", "Megalobiz", "Paxsenix (Auto)", "Paxsenix: Apple Music", "Paxsenix: Spotify", "Paxsenix: Musixmatch")
                val newOrder = importedSettings["lyricsProviderOrder"]?.split(',', '|')?.mapNotNull { item ->
                    when (item.trim().uppercase(Locale.ROOT)) {
                        "BETTER_LYRICS" -> "BetterLyrics"
                        "BETTER_LYRICS_PORTATO" -> "BetterLyrics Portato"
                        "YOULY_PLUS" -> "YouLyPlus"
                        "LRCLIB" -> "LRCLIB"
                        "KUGOU" -> "KuGou"
                        "MEGALOBIZ" -> "Megalobiz"
                        "SIMPMUSIC" -> "SimpMusic"
                        "UNISON" -> "Unison"
                        "PAXSENIX", "PAXSENIX_AUTO" -> "Paxsenix (Auto)"
                        "PAXSENIX_APPLE_MUSIC" -> "Paxsenix: Apple Music"
                        "PAXSENIX_SPOTIFY" -> "Paxsenix: Spotify"
                        "PAXSENIX_MUSIXMATCH" -> "Paxsenix: Musixmatch"
                        else -> item.trim().takeIf { it in DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER }
                    }
                }
                val androidProviderFlags = linkedMapOf(
                    "enableBetterLyrics" to "BetterLyrics",
                    "enableBetterLyricsPortato" to "BetterLyrics Portato",
                    "enableYouLyPlusLyrics" to "YouLyPlus",
                    "enableSimpMusicLyrics" to "SimpMusic",
                    "enableMegalobizLyrics" to "Megalobiz",
                    "enablePaxsenixLyrics" to "Paxsenix (Auto)",
                    "enablePaxsenixAppleMusicLyrics" to "Paxsenix: Apple Music",
                    "enablePaxsenixSpotifyLyrics" to "Paxsenix: Spotify",
                    "enablePaxsenixMusixmatchLyrics" to "Paxsenix: Musixmatch",
                    "enableUnisonLyrics" to "Unison",
                    "enableLrclib" to "LRCLIB",
                    "enableKugou" to "KuGou",
                )
                val explicitProviderFlags = androidProviderFlags.mapNotNull { (key, provider) ->
                    importedSettings[key]?.toBooleanStrictOrNull()?.let { provider to it }
                }
                val importedEnabledProviders = old.enabledLyricsProviders.toMutableSet().apply {
                    explicitProviderFlags.forEach { (provider, enabled) -> if (enabled) add(provider) else remove(provider) }
                }
                savePreferences(old.copy(
                    theme = importedTheme,
                    lyricsProvider = importedProvider?.takeIf { it in allowedProviders } ?: old.lyricsProvider,
                    lyricsProviderOrder = newOrder?.takeIf { it.isNotEmpty() } ?: old.lyricsProviderOrder,
                    enabledLyricsProviders = if (explicitProviderFlags.isEmpty()) old.enabledLyricsProviders else importedEnabledProviders.toList(),
                    showLyricsTranslation = importedSettings["translateLyrics"]?.toBooleanStrictOrNull() ?: old.showLyricsTranslation,
                    showLyricsRomanization = listOf(
                        "lyricsRomanizeJapanese", "lyricsRomanizeKorean", "lyricsRomanizeChinese",
                        "lyricsRomanizeHindi", "lyricsRomanizeOtherLanguages",
                    ).mapNotNull { importedSettings[it]?.toBooleanStrictOrNull() }.let { values ->
                        if (values.isEmpty()) old.showLyricsRomanization else values.any { it }
                    },
                ))
            }
            require(Files.isRegularFile(stageRoot.resolve("song.db")) || Files.isRegularFile(stageRoot.resolve("settings.xml"))) {
                "No supported ArchiveTune Android backup data (song.db/settings.xml) was found in this ZIP."
            }
            "Android backup imported: $importedPlaylistCount playlists, $importedTrackCount saved track entries. ${if (skippedLocalCount > 0) "$skippedLocalCount Android-local metadata entries were skipped because the backup does not contain their original file paths. " else ""}Account tokens and private credentials were intentionally not imported."
        } finally {
            deleteRecursively(stageRoot)
        }
    }

    private fun readAndroidSettings(file: Path): Map<String, String> = runCatching {
        if (!Files.isRegularFile(file)) return@runCatching emptyMap()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        val document = Files.newInputStream(file).use { factory.newDocumentBuilder().parse(it) }
        val out = LinkedHashMap<String, String>()
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            if (!element.hasAttribute("name")) continue
            val name = element.getAttribute("name")
            if (element.hasAttribute("value")) out[name] = element.getAttribute("value")
            else if (element.tagName == "string") out[name] = element.textContent.orEmpty()
            else if (element.tagName == "string-set" || element.tagName == "set") {
                val items = element.getElementsByTagName("item")
                out[name] = (0 until items.length).map { items.item(it).textContent.orEmpty() }.joinToString(",")
            }
        }
        out
    }.getOrDefault(emptyMap())

    private fun chooseAndroidBackupFile(): File? {
        var chosen: File? = null
        val showPicker = {
            val chooser = JFileChooser().apply {
                dialogTitle = "Import original ArchiveTune Android backup"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter("ArchiveTune Android backup (*.zip)", "zip")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
        }
        if (SwingUtilities.isEventDispatchThread()) showPicker() else SwingUtilities.invokeAndWait { showPicker() }
        return chosen
    }

    private fun confirmAndroidBackupImport(): Boolean {
        var approved = false
        val ask = {
            approved = JOptionPane.showConfirmDialog(
                null,
                "Import library metadata, playlists and safe visual/lyrics preferences from an Android ArchiveTune backup? This adds imported playlists without replacing your current desktop data. Android-local tracks have no portable file paths in the database backup. Account tokens, passwords and session credentials will never be imported.",
                "Import Android ArchiveTune backup",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
            ) == JOptionPane.YES_OPTION
        }
        if (SwingUtilities.isEventDispatchThread()) ask() else SwingUtilities.invokeAndWait { ask() }
        return approved
    }

    private fun chooseBackupFile(save: Boolean): File? {
        var chosen: File? = null
        val showPicker = {
            val chooser = JFileChooser().apply {
                dialogTitle = if (save) "Back up ArchiveTune data" else "Restore ArchiveTune backup"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter("ArchiveTune backup (*.zip)", "zip")
                if (save) selectedFile = File(System.getProperty("user.home"), "ArchiveTune-backup.zip")
            }
            val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                var file = chooser.selectedFile
                if (save && !file.name.endsWith(".zip", ignoreCase = true)) file = File(file.parentFile, "${file.name}.zip")
                chosen = file
            }
        }
        if (SwingUtilities.isEventDispatchThread()) showPicker() else SwingUtilities.invokeAndWait { showPicker() }
        return chosen
    }

    private fun confirmBackupRestore(): Boolean {
        var approved = false
        val ask = {
            approved = JOptionPane.showConfirmDialog(
                null,
                "Restore ArchiveTune playlists, history, queue, preferences and indexed library? Existing downloads will be kept, and backed-up downloads will be added. Original local music files are not included.",
                "Restore ArchiveTune backup",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            ) == JOptionPane.YES_OPTION
        }
        if (SwingUtilities.isEventDispatchThread()) ask() else SwingUtilities.invokeAndWait { ask() }
        return approved
    }

    private fun isSafeBackupFileName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name &&
            name.none { it.code < 32 } && name.length <= 240

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) return
        runCatching {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { path -> runCatching { Files.deleteIfExists(path) } }
            }
        }
    }

    override fun close() {
        persistPlayerStateNow()
        runCatching { discordPresence.close() }
        runCatching { dlnaCast.close() }
        backupScheduler.shutdownNow()
        castPollScheduler.shutdownNow()
        castPositionTask?.cancel(true)
        ioScope.cancel()
        runCatching {
            Platform.runLater { runCatching { mediaPlayer?.stop(); mediaPlayer?.dispose() }; mediaPlayer = null; Platform.exit() }
        }
    }

    /** Returns the original local URI for JavaFX-friendly formats, otherwise a cached AAC/M4A
     * conversion. FFmpeg is optional and is discovered from ARCHIVETUNE_FFMPEG or PATH. */
    private suspend fun resolveLocalPlaybackUri(path: Path): String = withContext(Dispatchers.IO) {
        val normalized = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalized)) { "The local audio file no longer exists: $normalized" }
        if (normalized.extensionLowercase() in directJavaFxExtensions) return@withContext normalized.toUri().toString()
        transcodeLocalAudio(normalized).toUri().toString()
    }

    private fun transcodeLocalAudio(path: Path): Path {
        val normalized = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalized)) { "The local audio file no longer exists: $normalized" }
        val ffmpeg = findFfmpeg() ?: throw IllegalStateException(
            "This audio format (${normalized.extensionLowercase()}) is not decoded by every JavaFX runtime. Install FFmpeg and add it to PATH, or set ARCHIVETUNE_FFMPEG to its executable path, then retry."
        )
        Files.createDirectories(transcodedDir)
        val modified = Files.getLastModifiedTime(normalized).toMillis()
        val signature = "${normalized}\u0000${Files.size(normalized)}\u0000$modified"
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val destination = transcodedDir.resolve("$key.m4a")
        if (Files.isRegularFile(destination) && Files.size(destination) > 0L) return destination

        val temporary = transcodedDir.resolve("$key-${UUID.randomUUID()}.tmp.m4a")
        val logFile = transcodedDir.resolve("$key-${UUID.randomUUID()}.log")
        try {
            val process = ProcessBuilder(
                ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-i", normalized.toString(), "-map", "0:a:0", "-map_metadata", "0", "-vn",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", temporary.toString(),
            ).redirectErrorStream(true).redirectOutput(logFile.toFile()).start()
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("FFmpeg audio conversion timed out after 3 minutes.")
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(temporary) || Files.size(temporary) <= 0L) {
                val details = runCatching { Files.readString(logFile, StandardCharsets.UTF_8).trim().takeLast(800) }.getOrDefault("")
                throw IllegalStateException("FFmpeg could not convert this audio file.${if (details.isNotBlank()) " $details" else ""}")
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
            return destination
        } catch (failure: IllegalStateException) {
            throw failure
        } catch (failure: Throwable) {
            throw IllegalStateException("Could not convert the audio using FFmpeg: ${failure.message ?: "process error"}", failure)
        } finally {
            runCatching { Files.deleteIfExists(temporary) }
            runCatching { Files.deleteIfExists(logFile) }
        }
    }

    private fun findFfmpeg(): String? {
        val configured = System.getenv("ARCHIVETUNE_FFMPEG")?.trim().orEmpty()
        val candidates = buildList {
            if (configured.isNotBlank()) add(configured)
            add("ffmpeg")
        }
        return candidates.firstOrNull { command ->
            runCatching {
                val process = ProcessBuilder(command, "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else process.exitValue() == 0
            }.getOrDefault(false)
        }
    }

    private fun restorePlayerState() = runCatching {
        if (!Files.isRegularFile(playerStatePath)) return@runCatching
        val fields = Files.readString(playerStatePath, StandardCharsets.UTF_8).trimEnd('\r', '\n').split('\t')
        if (fields.firstOrNull() != "S" || fields.size < 11) return@runCatching
        val track = decodeTrack(fields.drop(2)) ?: return@runCatching
        if (track.source == TrackSource.LOCAL && track.location?.let { Files.isRegularFile(Paths.get(it)) } != true) {
            mutablePlayerState.value = PlayerSnapshot(volume = mutablePreferences.value.volume, statusMessage = "Last played local file was not found; your queue and history are preserved.")
            return@runCatching
        }
        val position = fields.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        mutablePlayerState.value = PlayerSnapshot(
            currentTrack = track,
            isPlaying = false,
            isLoading = false,
            positionMs = position.coerceAtMost(track.durationMs ?: Long.MAX_VALUE),
            durationMs = track.durationMs ?: 0L,
            volume = mutablePreferences.value.volume,
            statusMessage = "Ready to resume ${track.title}",
        )
        lastPersistedPositionMs = position
    }

    private fun playerStateLine(): String {
        val snapshot = mutablePlayerState.value
        val track = snapshot.currentTrack
        return if (track == null) "" else (listOf("S", snapshot.positionMs.coerceAtLeast(0L).toString()) + encodeTrack(track)).joinToString("\t")
    }

    private fun persistPlayerState() {
        val payload = synchronized(playerStatePersistLock) {
            (++playerStatePersistVersion) to playerStateLine()
        }
        val version = payload.first
        val line = payload.second
        ioScope.launch(Dispatchers.IO) {
            synchronized(playerStatePersistLock) {
                if (version != playerStatePersistVersion) return@synchronized
                runCatching {
                    Files.createDirectories(dataRoot)
                    Files.writeString(playerStatePath, line, StandardCharsets.UTF_8)
                }
            }
        }
    }

    private fun persistPlayerStateNow() {
        synchronized(playerStatePersistLock) {
            playerStatePersistVersion++
            val line = playerStateLine()
            runCatching {
                Files.createDirectories(dataRoot)
                Files.writeString(playerStatePath, line, StandardCharsets.UTF_8)
            }
        }
    }

    private suspend fun resolveDirectAudioUrl(track: MusicTrack): String = withContext(Dispatchers.IO) {
        val response = YouTube.player(videoId = track.id, client = YouTubeClient.WEB_REMIX, setLogin = false).getOrThrow()
        val formats = response.streamingData?.adaptiveFormats.orEmpty()
        val audioFormat = formats.asSequence()
            .filter { it.isAudio }
            .filter { it.mimeType.startsWith("audio/mp4", ignoreCase = true) }
            .filter { !it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank() }
            .maxByOrNull { it.bitrate }
            ?: throw IllegalStateException("YouTube did not return an AAC/M4A audio format that this desktop media backend can play.")
        NewPipeUtils.getStreamUrl(audioFormat, track.id, client = YouTubeClient.WEB_REMIX).getOrThrow()
    }

    /** Applies the platform JavaFX 10-band equalizer when the media backend exposes it. */
    private fun applyEqualizer(player: MediaPlayer?) {
        val equalizer = player?.audioEqualizer ?: return
        val prefs = mutablePreferences.value
        equalizer.isEnabled = prefs.equalizerEnabled
        val bands = equalizer.bands.toList()
        bands.forEachIndexed { index, band ->
            band.gain = (prefs.equalizerGainsDb.getOrElse(index) { 0f }).coerceIn(-12f, 12f).toDouble()
        }
    }

    private suspend fun openMedia(uri: String, track: MusicTrack) {
        FxRuntime.start()
        val ready = CompletableDeferred<Unit>()
        Platform.runLater {
            try {
                runCatching { mediaPlayer?.stop(); mediaPlayer?.dispose() }
                val media = Media(uri)
                val next = MediaPlayer(media)
                mediaPlayer = next
                next.volume = mutablePlayerState.value.volume.toDouble()
                next.rate = mutablePreferences.value.playbackSpeed.toDouble()
                applyEqualizer(next)
                next.setOnReady {
                    val duration = next.totalDuration.toMillis().takeIf { it.isFinite() && it > 0.0 }?.toLong() ?: (track.durationMs ?: 0L)
                    mutablePlayerState.value = mutablePlayerState.value.copy(currentTrack = track, isLoading = false, isPlaying = true, positionMs = 0L, durationMs = duration, statusMessage = "Playing")
                    trackStartedAtEpochSeconds = System.currentTimeMillis() / 1000L
                    lastFmScrobbledTrackKey = null
                    listenBrainzScrobbledTrackKey = null
                    notifyLastFmNowPlaying(track, duration)
                    notifyListenBrainzNowPlaying(track, duration)
                    lastPersistedPositionMs = 0L
                    persistPlayerState()
                    next.play()
                    if (!ready.isCompleted) ready.complete(Unit)
                }
                next.setOnError {
                    val reason = next.error?.message ?: media.error?.message ?: "Unsupported or unavailable audio stream"
                    mutablePlayerState.value = mutablePlayerState.value.copy(isLoading = false, isPlaying = false, statusMessage = "Playback error: $reason")
                    if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException(reason))
                }
                next.setOnEndOfMedia {
                    if (mutableQueueState.value.repeatMode == RepeatMode.ONE) {
                        next.seek(Duration.ZERO); next.play()
                        mutablePlayerState.value = mutablePlayerState.value.copy(isPlaying = true, positionMs = 0L)
                    } else {
                        mutablePlayerState.value = mutablePlayerState.value.copy(isPlaying = false, positionMs = mutablePlayerState.value.durationMs)
                        persistPlayerState()
                        ioScope.launch { playNext() }
                    }
                }
                next.currentTimeProperty().addListener { _, _, time ->
                    val position = time?.toMillis()?.toLong() ?: 0L
                    val previous = mutablePlayerState.value
                    if (kotlin.math.abs(previous.positionMs - position) >= 300L) mutablePlayerState.value = previous.copy(positionMs = position.coerceAtLeast(0L))
                    if (kotlin.math.abs(lastPersistedPositionMs - position) >= 5_000L) {
                        lastPersistedPositionMs = position
                        persistPlayerState()
                    }
                    maybeScrobble(track, position, previous.durationMs)
                }
            } catch (failure: Throwable) {
                if (!ready.isCompleted) ready.completeExceptionally(failure)
            }
        }
        try {
            withTimeout(25_000L) { ready.await() }
        } catch (failure: TimeoutCancellationException) {
            throw IllegalStateException("The media backend did not load the selected audio within 25 seconds.", failure)
        }
    }

    private fun chooseLocalMusic(): List<File>? {
        var chosen: List<File>? = null
        val showPicker = {
            val chooser = JFileChooser().apply {
                dialogTitle = "Add music to ArchiveTune"
                fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                isMultiSelectionEnabled = true
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter("Audio files (${supportedExtensions.joinToString(", ")})", *supportedExtensions.toTypedArray())
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFiles?.toList().orEmpty().ifEmpty { listOfNotNull(chooser.selectedFile) }
        }
        if (SwingUtilities.isEventDispatchThread()) showPicker() else SwingUtilities.invokeAndWait { showPicker() }
        return chosen
    }

    private fun tracksForPaths(paths: List<Path>): List<MusicTrack> = paths
        .filter { Files.isRegularFile(it) && it.extensionLowercase() in supportedExtensions }
        .distinctBy { it.toAbsolutePath().normalize().toString() }
        .sortedBy { it.fileName.toString().lowercase() }
        .map { path ->
            val filename = path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
            val separator = filename.indexOf(" - ")
            MusicTrack(
                id = path.toAbsolutePath().normalize().toString(),
                title = (if (separator > 0) filename.substring(separator + 3).trim() else filename).ifBlank { filename },
                artist = if (separator > 0) filename.substring(0, separator).trim() else "Local audio",
                source = TrackSource.LOCAL, kind = MusicKind.SONG, location = path.toAbsolutePath().normalize().toString(),
            )
        }

    private fun toMusicTrack(item: YTItem): MusicTrack? = when (item) {
        is SongItem -> MusicTrack(item.id, item.title, item.artists.joinToString(", ") { it.name }.ifBlank { "Unknown artist" }, item.album?.name, item.duration?.toLong()?.times(1000L), item.thumbnail, TrackSource.YOUTUBE_MUSIC, MusicKind.SONG, item.shareLink)
        is AlbumItem -> MusicTrack(item.id, item.title, item.artists.orEmpty().joinToString(", ") { it.name }.ifBlank { "Album" }, artworkUrl = item.thumbnail, source = TrackSource.YOUTUBE_MUSIC, kind = MusicKind.ALBUM, location = item.shareLink)
        is ArtistItem -> MusicTrack(item.id, item.title, item.subscriberCountText ?: "Artist", artworkUrl = item.thumbnail, source = TrackSource.YOUTUBE_MUSIC, kind = MusicKind.ARTIST, location = item.shareLink)
        is PlaylistItem -> MusicTrack(item.id, item.title, item.author?.name ?: "Playlist", artworkUrl = item.thumbnail, source = TrackSource.YOUTUBE_MUSIC, kind = MusicKind.PLAYLIST, location = item.shareLink)
        is PodcastItem -> MusicTrack(item.id, item.title, item.author?.name ?: "Podcast", artworkUrl = item.thumbnail, source = TrackSource.YOUTUBE_MUSIC, kind = MusicKind.PODCAST, location = item.shareLink)
        is EpisodeItem -> MusicTrack(item.id, item.title, item.podcast?.name ?: "Podcast episode", durationMs = item.duration?.toLong()?.times(1000L), artworkUrl = item.thumbnail, source = TrackSource.YOUTUBE_MUSIC, kind = MusicKind.EPISODE, location = item.shareLink)
    }

    private fun notifyLastFmNowPlaying(track: MusicTrack, durationMs: Long) {
        if (!mutablePreferences.value.scrobblingEnabled || !mutableLastFmStatus.value.connected || LastFM.sessionKey.isNullOrBlank()) return
        ioScope.launch {
            LastFM.updateNowPlaying(
                artist = track.artist,
                track = track.title,
                album = track.album,
                duration = (durationMs / 1000L).takeIf { it > 0 }?.toInt(),
            )
        }
    }

    private fun maybeScrobble(track: MusicTrack, positionMs: Long, durationMs: Long) {
        if (durationMs < 30_000L) return
        val threshold = minOf(240_000L, durationMs / 2L)
        if (positionMs < threshold) return

        if (mutablePreferences.value.scrobblingEnabled &&
            mutableLastFmStatus.value.connected &&
            !LastFM.sessionKey.isNullOrBlank() &&
            lastFmScrobbledTrackKey != track.stableKey
        ) {
            lastFmScrobbledTrackKey = track.stableKey
            ioScope.launch {
                LastFM.scrobble(
                    artist = track.artist,
                    track = track.title,
                    timestamp = trackStartedAtEpochSeconds,
                    album = track.album,
                    duration = (durationMs / 1000L).toInt().takeIf { it > 0 },
                ).onFailure { failure ->
                    mutablePlayerState.value = mutablePlayerState.value.copy(statusMessage = "Last.fm scrobble failed: ${failure.message ?: "request failed"}")
                }
            }
        }

        if (mutablePreferences.value.listenBrainzEnabled &&
            mutableListenBrainzStatus.value.connected &&
            listenBrainzToken.isNotBlank() &&
            listenBrainzScrobbledTrackKey != track.stableKey
        ) {
            listenBrainzScrobbledTrackKey = track.stableKey
            submitListenBrainz(track, "single", trackStartedAtEpochSeconds)
        }
    }

    private fun notifyListenBrainzNowPlaying(track: MusicTrack, durationMs: Long) {
        if (!mutablePreferences.value.listenBrainzEnabled || !mutableListenBrainzStatus.value.connected || listenBrainzToken.isBlank()) return
        submitListenBrainz(track, "playing_now", null, durationMs)
    }

    private fun submitListenBrainz(
        track: MusicTrack,
        listenType: String,
        listenedAtEpochSeconds: Long?,
        durationMs: Long = track.durationMs ?: 0L,
    ) {
        val token = listenBrainzToken
        if (token.isBlank()) return
        ioScope.launch {
            runCatching {
                val trackMetadata = buildJsonObject {
                    put("artist_name", track.artist)
                    put("track_name", track.title)
                    track.album?.takeIf(String::isNotBlank)?.let { put("release_name", it) }
                    put("additional_info", buildJsonObject {
                        if (durationMs > 0L) put("duration_ms", durationMs)
                        put("submission_client", "ArchiveTune Desktop")
                    })
                }
                val listenItem = buildJsonObject {
                    listenedAtEpochSeconds?.takeIf { listenType == "single" }?.let { put("listened_at", it) }
                    put("track_metadata", trackMetadata)
                }
                val requestBody = buildJsonObject {
                    put("listen_type", listenType)
                    put("payload", buildJsonArray { add(listenItem) })
                }.toString()
                val connection = URL("https://api.listenbrainz.org/1/submit-listens").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Token $token")
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("User-Agent", "ArchiveTune-Desktop/15.1")
                    connection.outputStream.use { it.write(requestBody.toByteArray(StandardCharsets.UTF_8)) }
                    val code = connection.responseCode
                    val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        mutableListenBrainzStatus.value = mutableListenBrainzStatus.value.copy(
                            message = "ListenBrainz submission failed (HTTP $code). Check the token in Settings.",
                        )
                        throw IllegalStateException("ListenBrainz returned HTTP $code: ${responseText.take(200)}")
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { failure ->
                mutablePlayerState.value = mutablePlayerState.value.copy(
                    statusMessage = "ListenBrainz submission failed: ${failure.message ?: "network error"}",
                )
            }
        }
    }

    private fun validateListenBrainzToken(token: String): String {
        val connection = URL("https://api.listenbrainz.org/1/validate-token").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Token $token")
            connection.setRequestProperty("User-Agent", "ArchiveTune-Desktop/15.1")
            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "ListenBrainz token validation failed (HTTP $code)." }
            val body = json.parseToJsonElement(responseText).jsonObject
            require(body["valid"]?.jsonPrimitive?.booleanOrNull == true) { "ListenBrainz rejected this token. Generate a valid personal access token and try again." }
            return body["user_name"]?.jsonPrimitive?.contentOrNull
                ?: body["username"]?.jsonPrimitive?.contentOrNull.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private fun initializeListenBrainz() {
        listenBrainzToken = runCatching {
            if (!Files.isRegularFile(listenBrainzTokenPath)) ""
            else Properties().also { props -> Files.newInputStream(listenBrainzTokenPath).use(props::load) }
                .getProperty("token")?.trim().orEmpty()
        }.getOrDefault("")
        if (listenBrainzToken.isBlank()) {
            mutableListenBrainzStatus.value = ListenBrainzStatus()
            return
        }
        mutableListenBrainzStatus.value = ListenBrainzStatus(connected = false, message = "Checking saved ListenBrainz token…")
        ioScope.launch {
            runCatching { validateListenBrainzToken(listenBrainzToken) }
                .onSuccess { username ->
                    mutableListenBrainzStatus.value = ListenBrainzStatus(
                        connected = true,
                        username = username,
                        message = if (username.isBlank()) "ListenBrainz token validated." else "Connected to ListenBrainz as $username.",
                    )
                }
                .onFailure { failure ->
                    mutableListenBrainzStatus.value = ListenBrainzStatus(
                        connected = false,
                        message = "Saved ListenBrainz token could not be validated: ${failure.message ?: "network error"}",
                    )
                }
        }
    }

    private fun persistListenBrainzToken(token: String) = runCatching {
        Files.createDirectories(dataRoot)
        val props = Properties().apply { setProperty("token", token) }
        Files.newOutputStream(listenBrainzTokenPath).use { props.store(it, "ArchiveTune ListenBrainz token; keep this file private") }
        runCatching {
            Files.setPosixFilePermissions(listenBrainzTokenPath, setOf(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
            ))
        }
    }

    private fun initializeLastFm() {
        val apiKey = System.getenv("LASTFM_API_KEY").orEmpty().trim()
        val secret = System.getenv("LASTFM_SECRET").orEmpty().trim()
        val stored = runCatching {
            if (!Files.isRegularFile(lastFmSessionPath)) null
            else Properties().also { props -> Files.newInputStream(lastFmSessionPath).use(props::load) }.getProperty("sessionKey")?.takeIf(String::isNotBlank)
        }.getOrNull()
        LastFM.configure(
            endpoint = LastFM.DEFAULT_API_ENDPOINT,
            apiKey = apiKey,
            secret = secret,
            sessionKey = stored,
        )
        val configured = LastFM.isInitialized()
        mutableLastFmStatus.value = LastFmStatus(
            configured = configured,
            connected = configured && !stored.isNullOrBlank(),
            username = mutablePreferences.value.lastFmUsername,
            message = when {
                !configured -> "Set LASTFM_API_KEY and LASTFM_SECRET to enable Last.fm integration."
                !stored.isNullOrBlank() -> "Connected to Last.fm as ${mutablePreferences.value.lastFmUsername.ifBlank { "your account" }}."
                else -> "Connect a Last.fm account to enable scrobbling."
            },
        )
    }

    private fun persistLastFmSession(sessionKey: String) = runCatching {
        Files.createDirectories(dataRoot)
        val props = Properties().apply { setProperty("sessionKey", sessionKey) }
        Files.newOutputStream(lastFmSessionPath).use { props.store(it, "ArchiveTune Last.fm session; keep this file private") }
        runCatching {
            Files.setPosixFilePermissions(lastFmSessionPath, setOf(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
            ))
        }
    }

    private fun persistLibrary(paths: List<String>) = runCatching { Files.createDirectories(libraryPath.parent); Files.write(libraryPath, paths) }

    private fun rememberPlayed(track: MusicTrack) {
        val next = (listOf(track) + mutableRecentlyPlayed.value.filterNot { it.stableKey == track.stableKey }).take(100)
        mutableRecentlyPlayed.value = next
        runCatching { Files.createDirectories(dataRoot); Files.write(historyPath, next.map { "H\t" + encodeTrack(it).joinToString("\t") }) }
    }

    private fun persistPlaylists() {
        runCatching {
            Files.createDirectories(dataRoot)
            val lines = buildList {
                mutablePlaylists.value.forEach { playlist ->
                    add(listOf("P", enc(playlist.id), enc(playlist.name), playlist.createdAtEpochMs.toString()).joinToString("\t"))
                    playlist.tracks.forEach { track -> add((listOf("T", enc(playlist.id)) + encodeTrack(track)).joinToString("\t")) }
                }
            }
            Files.write(playlistsPath, lines)
        }
    }

    private fun persistQueue() = runCatching {
        Files.createDirectories(dataRoot)
        val state = mutableQueueState.value
        val lines = buildList {
            add(listOf("Q", state.shuffleEnabled.toString(), state.repeatMode.name, state.currentIndex.toString()).joinToString("\t"))
            state.tracks.forEach { add((listOf("T") + encodeTrack(it)).joinToString("\t")) }
        }
        Files.write(queuePath, lines)
    }

    private fun persistPodcastSubscriptions() = runCatching {
        Files.createDirectories(dataRoot)
        val lines = mutableSubscribedPodcasts.value
            .filter { it.kind == MusicKind.PODCAST && it.source == TrackSource.YOUTUBE_MUSIC }
            .map { (listOf("P") + encodeTrack(it)).joinToString("\t") }
        Files.write(podcastsPath, lines)
    }

    private fun restorePodcastSubscriptions() = runCatching {
        if (!Files.isRegularFile(podcastsPath)) return@runCatching
        mutableSubscribedPodcasts.value = Files.readAllLines(podcastsPath).mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.firstOrNull() != "P") null else decodeTrack(fields.drop(1))
        }.filter { it.source == TrackSource.YOUTUBE_MUSIC && it.kind == MusicKind.PODCAST }
            .distinctBy { it.stableKey }
            .sortedBy { it.title.lowercase() }
    }

    private fun restoreQueue() = runCatching {
        if (!Files.isRegularFile(queuePath)) return@runCatching
        var shuffle = false
        var repeat = RepeatMode.OFF
        var restoredIndex = -1
        val tracks = mutableListOf<MusicTrack>()
        Files.readAllLines(queuePath).forEach { line ->
            val fields = line.split('\t')
            when (fields.firstOrNull()) {
                "Q" -> {
                    shuffle = fields.getOrNull(1)?.toBooleanStrictOrNull() ?: false
                    repeat = runCatching { RepeatMode.valueOf(fields.getOrNull(2) ?: "OFF") }.getOrDefault(RepeatMode.OFF)
                    // Older queue files do not persist the selected row; preserve compatibility.
                    restoredIndex = fields.getOrNull(3)?.toIntOrNull() ?: -1
                }
                "T" -> decodeTrack(fields.drop(1))?.takeIf { it.canPlay }?.let(tracks::add)
            }
        }
        mutableQueueState.value = PlaybackQueueSnapshot(
            tracks = tracks,
            currentIndex = restoredIndex.takeIf { it in tracks.indices } ?: -1,
            shuffleEnabled = shuffle,
            repeatMode = repeat,
        )
    }

    private fun restorePlaylistsAndHistory() {
        if (Files.isRegularFile(playlistsPath)) runCatching {
            val names = LinkedHashMap<String, MusicPlaylist>()
            val trackMap = mutableMapOf<String, MutableList<MusicTrack>>()
            Files.readAllLines(playlistsPath).forEach { line ->
                val p = line.split('\t')
                when (p.firstOrNull()) {
                    "P" -> if (p.size >= 4) {
                        val id = dec(p[1]); names[id] = MusicPlaylist(id, dec(p[2]), emptyList(), p[3].toLongOrNull() ?: 0L); trackMap[id] = mutableListOf()
                    }
                    "T" -> if (p.size >= 11) {
                        val id = dec(p[1]); decodeTrack(p.drop(2))?.let { trackMap.getOrPut(id) { mutableListOf() }.add(it) }
                    }
                }
            }
            mutablePlaylists.value = names.values.map { playlist -> playlist.copy(tracks = trackMap[playlist.id].orEmpty()) }
        }
        if (Files.isRegularFile(historyPath)) runCatching {
            mutableRecentlyPlayed.value = Files.readAllLines(historyPath).mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.firstOrNull() == "H") decodeTrack(fields.drop(1)) else null
            }.take(100)
        }
    }

    private fun normalizeLyricsProviderOrder(order: List<String>): List<String> {
        val supported = DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER.toSet()
        val clean = order.filter { it in supported }.distinct()
        val cleanSet = clean.toSet()
        return clean + DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER.filterNot { it in cleanSet }
    }

    private fun normalizeEnabledLyricsProviders(enabled: List<String>): List<String> {
        val enabledSet = enabled.toSet()
        return DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER.filter { it in enabledSet }
    }

    private fun restorePreferences() = runCatching {
        if (!Files.isRegularFile(preferencesPath)) return@runCatching
        val props = Properties(); Files.newInputStream(preferencesPath).use(props::load)
        mutablePreferences.value = DesktopPreferences(
            playbackSpeed = props.getProperty("playbackSpeed")?.toFloatOrNull()?.coerceIn(0.5f, 2f) ?: 1f,
            volume = props.getProperty("volume")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.72f,
            startMinimized = props.getProperty("startMinimized")?.toBooleanStrictOrNull() ?: false,
            closeToTray = props.getProperty("closeToTray")?.toBooleanStrictOrNull() ?: false,
            scrobblingEnabled = props.getProperty("scrobblingEnabled")?.toBooleanStrictOrNull() ?: false,
            listenBrainzEnabled = props.getProperty("listenBrainzEnabled")?.toBooleanStrictOrNull() ?: false,
            lastFmUsername = props.getProperty("lastFmUsername", ""),
            theme = props.getProperty("theme", "Dark"),
            lyricsProvider = props.getProperty("lyricsProvider", "Auto").let { if (it == "Paxsenix") "Paxsenix (Auto)" else it }.takeIf {
                it in setOf("Auto", "YouTube Music", "YouTube Subtitle", "SimpMusic", "Unison", "YouLyPlus", "BetterLyrics", "BetterLyrics Portato", "LRCLIB", "KuGou", "Megalobiz", "Paxsenix (Auto)", "Paxsenix: Apple Music", "Paxsenix: Spotify", "Paxsenix: Musixmatch")
            } ?: "Auto",
            lyricsProviderOrder = normalizeLyricsProviderOrder(props.getProperty("lyricsProviderOrder")?.split('|').orEmpty()),
            enabledLyricsProviders = props.getProperty("enabledLyricsProviders")?.split('|')?.let(::normalizeEnabledLyricsProviders)
                ?: DEFAULT_DESKTOP_LYRICS_PROVIDER_ORDER,
            showLyricsTranslation = props.getProperty("showLyricsTranslation")?.toBooleanStrictOrNull() ?: true,
            showLyricsRomanization = props.getProperty("showLyricsRomanization")?.toBooleanStrictOrNull() ?: true,
            equalizerEnabled = props.getProperty("equalizerEnabled")?.toBooleanStrictOrNull() ?: false,
            equalizerGainsDb = props.getProperty("equalizerGainsDb")?.split(',')?.mapNotNull(String::toFloatOrNull)?.take(10)
                ?.map { it.coerceIn(-12f, 12f) }?.let { it + List((10 - it.size).coerceAtLeast(0)) { 0f } } ?: List(10) { 0f },
            discordPresenceEnabled = props.getProperty("discordPresenceEnabled")?.toBooleanStrictOrNull() ?: false,
            discordApplicationId = props.getProperty("discordApplicationId", ""),
            spotifyClientId = props.getProperty("spotifyClientId", ""),
            scheduledBackupEnabled = props.getProperty("scheduledBackupEnabled")?.toBooleanStrictOrNull() ?: false,
            scheduledBackupIntervalHours = props.getProperty("scheduledBackupIntervalHours")?.toIntOrNull()?.coerceIn(6, 168) ?: 24,
        )
    }

    private fun refreshDownloads() {
        mutableDownloadedTracks.value = runCatching {
            Files.createDirectories(downloadsDir)
            Files.list(downloadsDir).use { stream -> tracksForPaths(stream.filter { Files.isRegularFile(it) }.toList()) }
        }.getOrDefault(emptyList())
    }

    private fun uniqueDestination(path: Path): Path {
        if (!Files.exists(path)) return path
        val stem = path.fileName.toString().substringBeforeLast('.')
        val ext = path.fileName.toString().substringAfterLast('.', "m4a")
        var i = 2
        while (true) {
            val candidate = path.parent.resolve("$stem ($i).$ext")
            if (!Files.exists(candidate)) return candidate
            i++
        }
    }

    private fun parseSyncedLyricsWithWords(content: String): List<LyricLine> {
        val lineTimestamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val wordTimestamp = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")
        val pendingLines = content.lineSequence().mapNotNull { raw ->
            val lineTimes = lineTimestamp.findAll(raw).map(::timestampToMs).toList()
            if (lineTimes.isEmpty()) return@mapNotNull null
            val withoutLineTime = lineTimestamp.replace(raw, "")
            val wordMatches = wordTimestamp.findAll(withoutLineTime).toList()
            if (wordMatches.isEmpty()) {
                PendingLyricsLine(lineTimes, withoutLineTime.trim(), emptyList())
            } else {
                val words = wordMatches.mapIndexedNotNull { index, match ->
                    val segmentStart = match.range.last + 1
                    val segmentEnd = wordMatches.getOrNull(index + 1)?.range?.first ?: withoutLineTime.length
                    val wordText = withoutLineTime.substring(segmentStart.coerceAtMost(segmentEnd), segmentEnd).takeIf(String::isNotBlank)
                    wordText?.let { PendingLyricsWord(timestampToMs(match), it) }
                }
                val wholeText = words.joinToString("") { it.text }.ifBlank { wordTimestamp.replace(withoutLineTime, "").trim() }
                PendingLyricsLine(lineTimes, wholeText, words)
            }
        }.toList()

        return pendingLines.flatMapIndexed { lineIndex, line ->
            val nextLineTime = pendingLines.drop(lineIndex + 1).firstOrNull()?.times?.minOrNull()
            val words = line.words.mapIndexed { wordIndex, word ->
                val nextWordTime = line.words.getOrNull(wordIndex + 1)?.timeMs
                val end = nextWordTime?.takeIf { it > word.timeMs }
                    ?: nextLineTime?.takeIf { it > word.timeMs }
                    ?: (word.timeMs + 800L)
                LyricWord(word.timeMs, end, word.text)
            }
            line.times.map { time -> LyricLine(time, line.text, words) }
        }.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    private fun timestampToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fractionString = match.groupValues.getOrNull(3).orEmpty()
        val fraction = when (fractionString.length) {
            1 -> (fractionString.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionString.toLongOrNull() ?: 0L) * 10L
            3 -> fractionString.toLongOrNull() ?: 0L
            else -> 0L
        }
        return (minutes * 60L + seconds) * 1000L + fraction
    }

    private fun parseSyncedLyrics(content: String): List<LyricLine> {
        val timePattern = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        return content.lines().flatMap { line ->
            val matches = timePattern.findAll(line).toList()
            val text = timePattern.replace(line, "").trim()
            if (matches.isEmpty()) listOf(LyricLine(null, text)).filter { it.text.isNotBlank() }
            else matches.map { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fracString = match.groupValues[3]
                val frac = when (fracString.length) { 1 -> (fracString.toLongOrNull() ?: 0L) * 100L; 2 -> (fracString.toLongOrNull() ?: 0L) * 10L; 3 -> fracString.toLongOrNull() ?: 0L; else -> 0L }
                LyricLine((minutes * 60L + seconds) * 1000L + frac, text)
            }
        }.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    private fun encodeTrack(track: MusicTrack): List<String> = listOf(
        track.id, track.title, track.artist, track.album.orEmpty(), track.durationMs?.toString().orEmpty(),
        track.artworkUrl.orEmpty(), track.source.name, track.kind.name, track.location.orEmpty(),
    ).map(::enc)

    private fun decodeTrack(fields: List<String>): MusicTrack? = runCatching {
        require(fields.size >= 9)
        MusicTrack(
            id = dec(fields[0]), title = dec(fields[1]), artist = dec(fields[2]),
            album = dec(fields[3]).ifBlank { null }, durationMs = dec(fields[4]).toLongOrNull(),
            artworkUrl = dec(fields[5]).ifBlank { null }, source = TrackSource.valueOf(dec(fields[6])),
            kind = MusicKind.valueOf(dec(fields[7])), location = dec(fields[8]).ifBlank { null },
        )
    }.getOrNull()

    private fun enc(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun dec(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun Path.extensionLowercase(): String = fileName.toString().substringAfterLast('.', "").lowercase()
}
