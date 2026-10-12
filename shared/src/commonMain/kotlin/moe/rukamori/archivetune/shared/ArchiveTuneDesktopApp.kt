/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private data class ArchivePalette(
    val background: Color,
    val sidebar: Color,
    val panel: Color,
    val panelRaised: Color,
    val accent: Color,
    val accentDeep: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val colorScheme: ColorScheme,
)

private val DarkArchivePalette = ArchivePalette(
    background = Color(0xFF0D0D12), sidebar = Color(0xFF121219), panel = Color(0xFF1A1923),
    panelRaised = Color(0xFF24222F), accent = Color(0xFFED5564), accentDeep = Color(0xFFB6334D),
    text = Color(0xFFF6F2FF), muted = Color(0xFFA6A1B5), border = Color(0xFF302D3B),
    colorScheme = darkColorScheme(
        primary = Color(0xFFED5564), onPrimary = Color(0xFF23133F), secondary = Color(0xFF9ADFD0),
        background = Color(0xFF0D0D12), surface = Color(0xFF1A1923), surfaceVariant = Color(0xFF24222F),
        onBackground = Color(0xFFF6F2FF), onSurface = Color(0xFFF6F2FF),
        onSurfaceVariant = Color(0xFFA6A1B5), outline = Color(0xFF302D3B),
    ),
)

private val ObsidianArchivePalette = ArchivePalette(
    background = Color(0xFF050506), sidebar = Color(0xFF09090B), panel = Color(0xFF101014),
    panelRaised = Color(0xFF19191F), accent = Color(0xFFFF6474), accentDeep = Color(0xFFC13B54),
    text = Color(0xFFF8F6FA), muted = Color(0xFFA8A5B0), border = Color(0xFF27262D),
    colorScheme = darkColorScheme(
        primary = Color(0xFFFF6474), onPrimary = Color(0xFF201016), secondary = Color(0xFF9ADFD0),
        background = Color(0xFF050506), surface = Color(0xFF101014), surfaceVariant = Color(0xFF19191F),
        onBackground = Color(0xFFF8F6FA), onSurface = Color(0xFFF8F6FA),
        onSurfaceVariant = Color(0xFFA8A5B0), outline = Color(0xFF27262D),
    ),
)

private val LightArchivePalette = ArchivePalette(
    background = Color(0xFFF5F4F8), sidebar = Color(0xFFECEAF1), panel = Color(0xFFFFFFFF),
    panelRaised = Color(0xFFF0EDF4), accent = Color(0xFFD63850), accentDeep = Color(0xFFAC2942),
    text = Color(0xFF211D2A), muted = Color(0xFF6F687A), border = Color(0xFFDAD5E2),
    colorScheme = lightColorScheme(
        primary = Color(0xFFD63850), onPrimary = Color.White, secondary = Color(0xFF216E63),
        background = Color(0xFFF5F4F8), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF0EDF4),
        onBackground = Color(0xFF211D2A), onSurface = Color(0xFF211D2A),
        onSurfaceVariant = Color(0xFF6F687A), outline = Color(0xFFDAD5E2),
    ),
)

private val LocalArchivePalette = staticCompositionLocalOf { DarkArchivePalette }
private val ArchiveBackground: Color @Composable get() = LocalArchivePalette.current.background
private val ArchiveSidebar: Color @Composable get() = LocalArchivePalette.current.sidebar
private val ArchivePanel: Color @Composable get() = LocalArchivePalette.current.panel
private val ArchivePanelRaised: Color @Composable get() = LocalArchivePalette.current.panelRaised
private val ArchiveAccent: Color @Composable get() = LocalArchivePalette.current.accent
private val ArchiveAccentDeep: Color @Composable get() = LocalArchivePalette.current.accentDeep
private val ArchiveText: Color @Composable get() = LocalArchivePalette.current.text
private val ArchiveMuted: Color @Composable get() = LocalArchivePalette.current.muted
private val ArchiveBorder: Color @Composable get() = LocalArchivePalette.current.border

private val navigation = listOf(
    "Home" to "⌂",
    "Search" to "⌕",
    "Library" to "▤",
    "Playlists" to "≡",
    "Downloads" to "↓",
    "History" to "◷",
    "Queue" to "☷",
    "Lyrics" to "♫",
    "Identify" to "⌁",
    "Podcasts" to "◉",
    "Settings" to "⚙",
)

@Composable
fun ArchiveTuneDesktopApp(repository: ArchiveTuneRepository) {
    val player by repository.playerState.collectAsState()
    val queue by repository.queueState.collectAsState()
    val playlists by repository.playlists.collectAsState()
    val recentlyPlayed by repository.recentlyPlayed.collectAsState()
    val downloadedTracks by repository.downloadedTracks.collectAsState()
    val subscribedPodcasts by repository.subscribedPodcasts.collectAsState()
    val lyrics by repository.lyricsState.collectAsState()
    val preferences by repository.preferences.collectAsState()
    val lastFmStatus by repository.lastFmStatus.collectAsState()
    val listenBrainzStatus by repository.listenBrainzStatus.collectAsState()
    val spotifyStatus by repository.spotifyStatus.collectAsState()
    val spotifyPlayback by repository.spotifyPlayback.collectAsState()
    val dlnaRenderers by repository.dlnaRenderers.collectAsState()
    val recognitionResult by repository.recognitionResult.collectAsState()
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.SONGS) }
    var localTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var homeFeed by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var homeFeedBusy by remember { mutableStateOf(false) }
    var activeCollection by remember { mutableStateOf<BrowseCollection?>(null) }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renamePlaylistValue by remember { mutableStateOf("") }

    suspend fun doSearch(requestedCategory: SearchCategory = category) {
        val requestedQuery = query.trim()
        if (requestedQuery.isBlank()) {
            notice = "Enter a song, artist, album, or playlist to search YouTube Music."
            return
        }
        route = "Search"
        searchBusy = true
        notice = null
        try {
            searchResults = repository.search(requestedQuery, requestedCategory)
            if (searchResults.isEmpty()) notice = "No results found. Try a shorter or different query."
        } catch (failure: Throwable) {
            notice = failure.message?.takeIf(String::isNotBlank)
                ?: "Search failed. Check your internet connection and try again."
        } finally {
            searchBusy = false
        }
    }

    fun startTrack(track: MusicTrack) {
        if (track.kind == MusicKind.ALBUM || track.kind == MusicKind.ARTIST || track.kind == MusicKind.PLAYLIST || track.kind == MusicKind.PODCAST) {
            scope.launch {
                route = "Collection"
                activeCollection = null
                notice = "Opening ${track.title}…"
                try {
                    activeCollection = repository.loadCollection(track)
                    notice = null
                } catch (failure: Throwable) {
                    notice = failure.message ?: "Could not load ${track.title}. It may be unavailable or private."
                }
            }
            return
        }
        val alreadySelected = player.currentTrack?.let { it.id == track.id && it.source == track.source } == true
        scope.launch {
            notice = null
            if (alreadySelected && track.canPlay) {
                repository.togglePlayback()
                return@launch
            }
            repository.play(track)
        }
    }

    LaunchedEffect(repository) {
        localTracks = runCatching { repository.loadLocalLibrary() }.getOrDefault(emptyList())
        homeFeedBusy = true
        homeFeed = runCatching { repository.loadHomeFeed() }.getOrDefault(emptyList())
        homeFeedBusy = false
    }

    LaunchedEffect(player.currentTrack?.stableKey) {
        player.currentTrack?.let { track -> runCatching { repository.loadLyrics(track) } }
    }

    fun addToSelectedPlaylist(track: MusicTrack) {
        scope.launch {
            try {
                val target = selectedPlaylistId?.let { id -> playlists.firstOrNull { it.id == id } }
                    ?: playlists.firstOrNull()
                    ?: repository.createPlaylist("Favorites").also { selectedPlaylistId = it.id }
                repository.addToPlaylist(target.id, track)
                notice = "Added ${track.title} to ${target.name}."
            } catch (failure: Throwable) {
                notice = failure.message ?: "Could not add this track to a playlist."
            }
        }
    }

    fun queueTrack(track: MusicTrack, next: Boolean = false) {
        scope.launch {
            try { repository.queueTrack(track, playNext = next); notice = player.statusMessage }
            catch (failure: Throwable) { notice = failure.message ?: "Could not update the queue." }
        }
    }

    fun downloadTrack(track: MusicTrack) {
        scope.launch {
            notice = "Preparing download: ${track.title}…"
            try {
                val saved = repository.download(track)
                notice = "Downloaded ${saved.title} to ArchiveTune\'s downloads folder."
            } catch (failure: Throwable) {
                notice = failure.message ?: "Download failed. Check the connection and audio format."
            }
        }
    }

    val selectedPalette = when (preferences.theme) {
        "Light" -> LightArchivePalette
        "Obsidian" -> ObsidianArchivePalette
        else -> DarkArchivePalette
    }
    CompositionLocalProvider(LocalArchivePalette provides selectedPalette) {
      MaterialTheme(colorScheme = selectedPalette.colorScheme) {
        Row(
            modifier = Modifier.fillMaxSize().background(ArchiveBackground).focusable().onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.Spacebar && !event.isCtrlPressed && !event.isAltPressed -> {
                        scope.launch { repository.togglePlayback() }; true
                    }
                    event.isCtrlPressed && event.key == Key.K -> { route = "Search"; true }
                    event.isCtrlPressed && event.key == Key.L -> { route = "Lyrics"; true }
                    event.isCtrlPressed && event.key == Key.DirectionRight -> { scope.launch { repository.playNext() }; true }
                    event.isCtrlPressed && event.key == Key.DirectionLeft -> { scope.launch { repository.playPrevious() }; true }
                    else -> false
                }
            },
        ) {
            Sidebar(
                selected = route,
                onSelect = { selected ->
                    route = selected
                    if (selected == "Podcasts") category = SearchCategory.PODCASTS
                    else if (selected == "Search") category = SearchCategory.SONGS
                    notice = null
                },
                onImport = {
                    scope.launch {
                        try {
                            localTracks = repository.importLocalMusic()
                            route = "Library"
                            notice = if (localTracks.isEmpty()) "No supported audio files were selected." else "Library updated: ${localTracks.size} tracks available."
                        } catch (failure: Throwable) {
                            notice = failure.message ?: "Could not import local music."
                        }
                    }
                },
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.widthIn(min = 120.dp)) {
                        Text(route, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = ArchiveText)
                        Text("Your music, organized around you", color = ArchiveMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.widthIn(min = 210.dp, max = 460.dp).weight(1f, fill = false),
                        placeholder = { Text("Search songs, artists, albums…", color = ArchiveMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { scope.launch { doSearch() } }),
                        shape = RoundedCornerShape(15.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ArchivePanel,
                            unfocusedContainerColor = ArchivePanel,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ArchiveAccent,
                        ),
                    )
                    Button(
                        onClick = { scope.launch { doSearch() } },
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color(0xFF23133F)),
                    ) { Text("Search", fontWeight = FontWeight.SemiBold) }
                }
                Divider(color = ArchiveBorder.copy(alpha = 0.7f))

                if (notice != null) {
                    Surface(
                        color = ArchivePanelRaised,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 14.dp),
                    ) {
                        Text(notice.orEmpty(), color = ArchiveText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp), fontSize = 13.sp)
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (route) {
                        "Home" -> HomeContent(
                            localTracks = localTracks,
                            history = recentlyPlayed,
                            recommendations = homeFeed,
                            feedBusy = homeFeedBusy,
                            onBrowse = { route = "Search" },
                            onLibrary = { route = "Library" },
                            onImport = {
                                scope.launch {
                                    try {
                                        localTracks = repository.importLocalMusic()
                                        route = "Library"
                                        notice = "Library updated: ${localTracks.size} tracks available."
                                    } catch (failure: Throwable) {
                                        notice = failure.message ?: "Could not import local music."
                                    }
                                }
                            },
                            onPlay = ::startTrack,
                            onQueue = { queueTrack(it) },
                            onDownload = ::downloadTrack,
                            onAddToPlaylist = ::addToSelectedPlaylist,
                            currentTrack = player.currentTrack,
                        )
                        "Search" -> SearchContent(
                            query = query,
                            category = category,
                            onCategory = { selected ->
                                category = selected
                                route = "Search"
                                if (query.isNotBlank()) scope.launch { doSearch(selected) }
                            },
                            results = searchResults,
                            isBusy = searchBusy,
                            onPlay = ::startTrack,
                            currentTrack = player.currentTrack,
                            onQueue = { queueTrack(it) },
                            onDownload = ::downloadTrack,
                            onAddToPlaylist = ::addToSelectedPlaylist,
                        )
                        "Podcasts" -> PodcastSubscriptionsContent(
                            podcasts = subscribedPodcasts,
                            currentTrack = player.currentTrack,
                            onOpen = ::startTrack,
                            onUnsubscribe = { podcast ->
                                scope.launch {
                                    runCatching { repository.unsubscribePodcast(podcast.stableKey) }
                                        .onSuccess { notice = "Unfollowed ${podcast.title}." }
                                        .onFailure { notice = it.message ?: "Could not unfollow this podcast." }
                                }
                            },
                            onSearch = {
                                route = "Search"
                                category = SearchCategory.PODCASTS
                                notice = "Search for a podcast, then open it and choose Follow podcast."
                            },
                        )
                        "Library" -> LibraryContent(
                            tracks = localTracks,
                            onImport = {
                                scope.launch {
                                    try {
                                        localTracks = repository.importLocalMusic()
                                        notice = "Library updated: ${localTracks.size} tracks available."
                                    } catch (failure: Throwable) {
                                        notice = failure.message ?: "Could not import local music."
                                    }
                                }
                            },
                            onPlay = ::startTrack,
                            currentTrack = player.currentTrack,
                            onQueue = { queueTrack(it) },
                            onDownload = ::downloadTrack,
                            onAddToPlaylist = ::addToSelectedPlaylist,
                            onRemove = { track ->
                                scope.launch {
                                    try {
                                        repository.removeFromLocalLibrary(track.stableKey)
                                        localTracks = repository.loadLocalLibrary()
                                        notice = "Removed ${track.title} from the library. The original audio file remains untouched."
                                    } catch (failure: Throwable) {
                                        notice = failure.message ?: "Could not remove this track from the library."
                                    }
                                }
                            },
                        )
                        "History" -> SimpleTrackContent(
                            title = "Recently played",
                            subtitle = "Persisted listening history on this computer",
                            tracks = recentlyPlayed,
                            emptyTitle = "Nothing played yet",
                            emptyDescription = "Play a song from search or your local library to see it here.",
                            onPlay = ::startTrack,
                            currentTrack = player.currentTrack,
                            onQueue = { queueTrack(it) },
                            onDownload = ::downloadTrack,
                            onAddToPlaylist = ::addToSelectedPlaylist,
                        )
                        "Playlists" -> PlaylistContent(
                            playlists = playlists,
                            selectedPlaylistId = selectedPlaylistId,
                            newPlaylistName = newPlaylistName,
                            renamePlaylistValue = renamePlaylistValue,
                            onNewNameChange = { newPlaylistName = it },
                            onRenameChange = { renamePlaylistValue = it },
                            onCreate = {
                                scope.launch {
                                    try {
                                        val created = repository.createPlaylist(newPlaylistName)
                                        selectedPlaylistId = created.id
                                        renamePlaylistValue = created.name
                                        newPlaylistName = ""
                                        notice = "Created playlist: ${created.name}"
                                    } catch (failure: Throwable) { notice = failure.message ?: "Could not create playlist." }
                                }
                            },
                            onSelect = { id ->
                                selectedPlaylistId = id
                                renamePlaylistValue = playlists.firstOrNull { it.id == id }?.name.orEmpty()
                            },
                            onRename = {
                                val id = selectedPlaylistId
                                if (id != null) scope.launch {
                                    try { repository.renamePlaylist(id, renamePlaylistValue); notice = "Playlist renamed." }
                                    catch (failure: Throwable) { notice = failure.message ?: "Could not rename playlist." }
                                }
                            },
                            onDelete = {
                                val id = selectedPlaylistId
                                if (id != null) scope.launch {
                                    repository.deletePlaylist(id)
                                    selectedPlaylistId = null
                                    renamePlaylistValue = ""
                                    notice = "Playlist deleted."
                                }
                            },
                            onPlay = ::startTrack,
                            onPlayAll = { selected -> scope.launch { repository.playQueue(selected, 0) } },
                            onRemoveTrack = { track ->
                                val id = selectedPlaylistId
                                if (id != null) scope.launch { repository.removeFromPlaylist(id, track.stableKey); notice = "Removed ${track.title} from playlist." }
                            },
                            onMoveTrackUp = { track -> selectedPlaylistId?.let { id -> scope.launch { repository.moveTrackInPlaylist(id, track.stableKey, -1) } } },
                            onMoveTrackDown = { track -> selectedPlaylistId?.let { id -> scope.launch { repository.moveTrackInPlaylist(id, track.stableKey, 1) } } },
                            onQueue = { queueTrack(it) },
                            onDownload = ::downloadTrack,
                            onAddToPlaylist = ::addToSelectedPlaylist,
                            currentTrack = player.currentTrack,
                        )
                        "Collection" -> activeCollection?.let { collection ->
                            val podcastTrack = if (collection.kind == MusicKind.PODCAST) MusicTrack(
                                id = collection.id,
                                title = collection.title,
                                artist = collection.subtitle,
                                artworkUrl = collection.artworkUrl,
                                source = TrackSource.YOUTUBE_MUSIC,
                                kind = MusicKind.PODCAST,
                                location = "https://music.youtube.com/podcast/${collection.id}",
                            ) else null
                            CollectionContent(
                                collection = collection,
                                currentTrack = player.currentTrack,
                                onPlay = ::startTrack,
                                onPlayAll = { scope.launch { repository.playQueue(collection.tracks, 0) } },
                                onQueue = { queueTrack(it) },
                                onDownload = ::downloadTrack,
                                onAddToPlaylist = ::addToSelectedPlaylist,
                                isPodcastSubscribed = podcastTrack?.let { wanted -> subscribedPodcasts.any { it.stableKey == wanted.stableKey } } == true,
                                onTogglePodcastSubscription = podcastTrack?.let { wanted ->
                                    {
                                        val wasSubscribed = subscribedPodcasts.any { it.stableKey == wanted.stableKey }
                                        scope.launch {
                                            runCatching {
                                                if (wasSubscribed) repository.unsubscribePodcast(wanted.stableKey)
                                                else repository.subscribePodcast(wanted)
                                            }.onSuccess {
                                                notice = if (wasSubscribed) "Unfollowed ${wanted.title}." else "Following ${wanted.title}."
                                            }.onFailure { notice = it.message ?: "Could not update podcast follow." }
                                        }
                                    }
                                },
                            )
                        } ?: UtilityContent(
                            title = "Loading collection",
                            subtitle = "YouTube Music",
                            body = notice ?: "Loading album, artist, playlist or podcast details…",
                            actionLabel = "Back to search",
                            onAction = { route = "Search" },
                        )
                        "Downloads" -> SimpleTrackContent(
                            title = "Downloads",
                            subtitle = "Offline audio stored under ~/.archivetune/downloads · ${downloadedTracks.size} tracks",
                            tracks = downloadedTracks,
                            emptyTitle = "No downloads yet",
                            emptyDescription = "Use the download action beside a playable track to save it for offline playback.",
                            onPlay = ::startTrack,
                            currentTrack = player.currentTrack,
                            onQueue = { queueTrack(it) },
                            onAddToPlaylist = ::addToSelectedPlaylist,
                            onRemove = { track -> scope.launch { runCatching { repository.deleteDownload(track.stableKey) }.onFailure { notice = it.message ?: "Could not delete this download." } } },
                        )
                        "Queue" -> QueueContent(
                            queue = queue,
                            currentTrack = player.currentTrack,
                            onPlay = ::startTrack,
                            onPrevious = { scope.launch { repository.playPrevious() } },
                            onNext = { scope.launch { repository.playNext() } },
                            onShuffle = { scope.launch { repository.toggleShuffle() } },
                            onRepeat = { scope.launch { repository.cycleRepeatMode() } },
                            onRemove = { track -> scope.launch { repository.removeFromQueue(track.stableKey) } },
                            onMoveUp = { track -> scope.launch { repository.moveQueueItem(track.stableKey, -1) } },
                            onMoveDown = { track -> scope.launch { repository.moveQueueItem(track.stableKey, 1) } },
                            onClear = { scope.launch { repository.clearQueue() } },
                        )
                        "Lyrics" -> LyricsContent(
                            track = player.currentTrack,
                            lyrics = lyrics,
                            positionMs = player.positionMs,
                            showTranslation = preferences.showLyricsTranslation,
                            showRomanization = preferences.showLyricsRomanization,
                            onReload = { player.currentTrack?.let { track -> scope.launch { repository.loadLyrics(track) } } },
                        )
                        "Identify" -> MusicRecognitionContent(
                            current = recognitionResult,
                            onIdentify = { scope.launch { runCatching { repository.recognizeLocalAudio() }.onFailure { notice = it.message ?: "Audio recognition failed" } } },
                            onIdentifyAmbient = { scope.launch { runCatching { repository.recognizeAmbientAudio() }.onFailure { notice = it.message ?: "Ambient recognition failed" } } },
                        )
                        "Settings" -> SettingsContent(
                            volume = player.volume,
                            preferences = preferences,
                            lastFmStatus = lastFmStatus,
                            listenBrainzStatus = listenBrainzStatus,
                            spotifyStatus = spotifyStatus,
                            spotifyPlayback = spotifyPlayback,
                            dlnaRenderers = dlnaRenderers,
                            currentTrack = player.currentTrack,
                            onVolumeChange = { value -> scope.launch { repository.setVolume(value) } },
                            onEqualizerEnabledChange = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(equalizerEnabled = enabled)) } },
                            onEqualizerGainChange = { index, gain ->
                                scope.launch {
                                    val current = repository.preferences.value
                                    val updated = current.equalizerGainsDb.toMutableList().apply {
                                        while (size < 10) add(0f)
                                        this[index] = gain
                                    }
                                    repository.savePreferences(current.copy(equalizerGainsDb = updated))
                                }
                            },
                            onSpeedChange = { value -> scope.launch { repository.savePreferences(repository.preferences.value.copy(playbackSpeed = value)) } },
                            onToggleScrobbling = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(scrobblingEnabled = enabled)) } },
                            onStartLastFm = { scope.launch { runCatching { repository.beginLastFmAuthorization() }.onFailure { notice = it.message ?: "Could not start Last.fm authorization." } } },
                            onFinishLastFm = { scope.launch { runCatching { repository.completeLastFmAuthorization() }.onSuccess { notice = "Last.fm connected." }.onFailure { notice = it.message ?: "Last.fm authorization failed. Authorize the app in the opened browser first." } } },
                            onDisconnectLastFm = { scope.launch { runCatching { repository.disconnectLastFm() }.onFailure { notice = it.message ?: "Could not disconnect Last.fm." } } },
                            onConnectListenBrainz = { token -> scope.launch { runCatching { repository.connectListenBrainz(token) }.onSuccess { notice = "ListenBrainz connected and token validated." }.onFailure { notice = it.message ?: "Could not connect ListenBrainz." } } },
                            onDisconnectListenBrainz = { scope.launch { runCatching { repository.disconnectListenBrainz() }.onFailure { notice = it.message ?: "Could not disconnect ListenBrainz." } } },
                            onToggleListenBrainz = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(listenBrainzEnabled = enabled)) } },
                            onStop = { scope.launch { repository.stop() } },
                            onExportBackup = { scope.launch { runCatching { repository.exportBackup() }.onSuccess { notice = it }.onFailure { notice = it.message ?: "Could not create the backup." } } },
                            onImportBackup = { scope.launch { runCatching { repository.importBackup() }.onSuccess { notice = it }.onFailure { notice = it.message ?: "Could not restore the backup." } } },
                            onImportAndroidBackup = { scope.launch { runCatching { repository.importAndroidBackup() }.onSuccess { notice = it }.onFailure { notice = it.message ?: "Could not import the Android backup." } } },
                            onScheduledBackupEnabledChange = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(scheduledBackupEnabled = enabled)) } },
                            onScheduledBackupIntervalChange = { hours -> scope.launch { repository.savePreferences(repository.preferences.value.copy(scheduledBackupIntervalHours = hours)) } },
                            onSaveVolume = { value -> scope.launch { repository.savePreferences(repository.preferences.value.copy(volume = value)) } },
                            onThemeChange = { value -> scope.launch { repository.savePreferences(repository.preferences.value.copy(theme = value)) } },
                            onLyricsProviderChange = { value ->
                                scope.launch {
                                    repository.savePreferences(repository.preferences.value.copy(lyricsProvider = value))
                                    player.currentTrack?.let { repository.loadLyrics(it) }
                                }
                            },
                            onLyricsProviderOrderChange = { value ->
                                scope.launch {
                                    repository.savePreferences(repository.preferences.value.copy(lyricsProviderOrder = value))
                                    if (preferences.lyricsProvider == "Auto") player.currentTrack?.let { repository.loadLyrics(it) }
                                }
                            },
                            onEnabledLyricsProvidersChange = { value ->
                                scope.launch {
                                    repository.savePreferences(repository.preferences.value.copy(enabledLyricsProviders = value))
                                    if (preferences.lyricsProvider == "Auto") player.currentTrack?.let { repository.loadLyrics(it) }
                                }
                            },
                            onToggleLyricsTranslation = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(showLyricsTranslation = enabled)) } },
                            onToggleLyricsRomanization = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(showLyricsRomanization = enabled)) } },
                            onStartMinimizedChange = { value -> scope.launch { repository.savePreferences(repository.preferences.value.copy(startMinimized = value)) } },
                            onCloseToTrayChange = { value -> scope.launch { repository.savePreferences(repository.preferences.value.copy(closeToTray = value)) } },
                            onDiscordPresenceEnabledChange = { enabled -> scope.launch { repository.savePreferences(repository.preferences.value.copy(discordPresenceEnabled = enabled)) } },
                            onDiscordApplicationIdChange = { value -> scope.launch { repository.savePreferences(repository.preferences.value.copy(discordApplicationId = value)) } },
                            onSpotifyConnect = { clientId -> scope.launch { runCatching { repository.connectSpotify(clientId) }.onFailure { notice = it.message ?: "Spotify connection failed" } } },
                            onSpotifyDisconnect = { scope.launch { repository.disconnectSpotify() } },
                            onRefreshSpotifyPlayback = { scope.launch { runCatching { repository.refreshSpotifyPlayback() }.onFailure { notice = it.message ?: "Could not read Spotify playback" } } },
                            onSpotifyPlaybackCommand = { command -> scope.launch { runCatching { repository.controlSpotifyPlayback(command) }.onFailure { notice = it.message ?: "Spotify playback control failed" } } },
                            onDiscoverDlna = { scope.launch { runCatching { repository.discoverDlnaDevices() }.onFailure { notice = it.message ?: "DLNA discovery failed" } } },
                            onCastCurrentTrack = { rendererId -> scope.launch { runCatching { repository.castCurrentTrack(rendererId) }.onFailure { notice = it.message ?: "Could not cast current track" } } },
                            onStopCasting = { scope.launch { runCatching { repository.stopCasting() }.onFailure { notice = it.message ?: "Could not stop casting" } } },
                            onRecognizeLocalAudio = { scope.launch { runCatching { repository.recognizeLocalAudio() }.onFailure { notice = it.message ?: "Audio recognition failed" } } },
                        )
                        else -> HomeContent(
                            localTracks = localTracks,
                            history = recentlyPlayed,
                            recommendations = homeFeed,
                            feedBusy = homeFeedBusy,
                            onBrowse = { route = "Search" },
                            onLibrary = { route = "Library" },
                            onImport = { scope.launch { localTracks = repository.importLocalMusic(); route = "Library" } },
                            onPlay = ::startTrack,
                            onQueue = { queueTrack(it) },
                            onDownload = ::downloadTrack,
                            onAddToPlaylist = ::addToSelectedPlaylist,
                            currentTrack = player.currentTrack,
                        )
                    }
                }

                PlayerBar(
                    snapshot = player,
                    onToggle = { scope.launch { repository.togglePlayback() } },
                    onSeek = { scope.launch { repository.seekTo(it) } },
                    onPrevious = { scope.launch { repository.playPrevious() } },
                    onNext = { scope.launch { repository.playNext() } },
                    onQueue = { route = "Queue" },
                    onLyrics = { route = "Lyrics" },
                    shuffleEnabled = queue.shuffleEnabled,
                    repeatMode = queue.repeatMode,
                    onShuffle = { scope.launch { repository.toggleShuffle() } },
                    onRepeat = { scope.launch { repository.cycleRepeatMode() } },
                )
            }
        }
      }
    }
}

@Composable
private fun Sidebar(
    selected: String,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier.width(224.dp).fillMaxHeight().background(ArchiveSidebar).padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Box(
                modifier = Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(ArchiveAccent, ArchiveAccentDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", fontWeight = FontWeight.Black, fontSize = 25.sp, color = Color(0xFF211332))
            }
            Column {
                Text("ArchiveTune", fontWeight = FontWeight.Bold, color = ArchiveText, fontSize = 17.sp)
                Text("DESKTOP", color = ArchiveMuted, fontSize = 9.sp, letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("YOUR SPACE", color = ArchiveMuted, fontSize = 10.sp, letterSpacing = 1.7.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        navigation.forEach { (name, glyph) ->
            val active = selected == name
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (active) ArchiveAccent.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(name) }.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text(glyph, color = if (active) ArchiveAccent else ArchiveMuted, fontSize = 20.sp)
                Text(name, color = if (active) ArchiveText else ArchiveMuted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(color = ArchivePanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("YOUR COLLECTION", color = ArchiveMuted, fontSize = 9.sp, letterSpacing = 1.1.sp)
                Text("Bring your music with you.", color = ArchiveText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text("＋  Add music", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Kotlin Multiplatform · Desktop", color = ArchiveMuted.copy(alpha = 0.7f), fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun HomeContent(
    localTracks: List<MusicTrack>,
    history: List<MusicTrack>,
    recommendations: List<MusicTrack>,
    feedBusy: Boolean,
    onBrowse: () -> Unit,
    onLibrary: () -> Unit,
    onImport: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
    onQueue: (MusicTrack) -> Unit,
    onDownload: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF393050), Color(0xFF211E30), Color(0xFF16312F)))),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = ArchiveAccent.copy(alpha = 0.15f), shape = CircleShape) {
                        Text("YOUR SOUND. YOUR SPACE.", color = ArchiveAccent, fontSize = 10.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Text("Every track has\na place here.", fontSize = 37.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = ArchiveText)
                    Text("Explore YouTube Music or bring in audio from your desktop.", color = ArchiveMuted, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onBrowse, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color(0xFF23133F))) { Text("Explore music  →") }
                        OutlinedButton(onClick = onImport, shape = RoundedCornerShape(12.dp)) { Text("Import library") }
                    }
                }
                Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(listOf(ArchiveAccentDeep, Color(0xFF413456)))))
                    Box(Modifier.size(112.dp).clip(CircleShape).background(ArchivePanel))
                    Box(Modifier.size(75.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFB8E4D9), ArchiveAccent))))
                    Text("♫", color = Color(0xFF27183F), fontSize = 49.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            QuickCard("Local library", "${localTracks.size} tracks imported", "▤", Modifier.weight(1f), onLibrary)
            QuickCard("YouTube Music", "Search millions of tracks", "⌕", Modifier.weight(1f), onBrowse)
            QuickCard("Recently played", if (history.isEmpty()) "Nothing played yet" else "${history.size} recent tracks", "◷", Modifier.weight(1f), onBrowse)
        }
        if (history.isNotEmpty()) {
            SectionHeading("Pick up where you left off", "Recent session")
            InlineTrackRows(history.take(5), onPlay, null, onQueue, onDownload, onAddToPlaylist)
        } else if (localTracks.isNotEmpty()) {
            SectionHeading("From your collection", "Local library")
            InlineTrackRows(localTracks.take(5), onPlay, null, onQueue, onDownload, onAddToPlaylist)
        } else {
            Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("♫", fontSize = 31.sp, color = ArchiveAccent)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your library starts here", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                        Text("Import a folder of audio files or search YouTube Music to get started.", color = ArchiveMuted, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onImport, shape = RoundedCornerShape(11.dp)) { Text("Add files") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SectionHeading("From YouTube Music", "Home feed")
        when {
            feedBusy -> Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ArchiveAccent) }
            recommendations.isEmpty() -> Text("The online home feed is unavailable right now. Search still works when YouTube Music is reachable.", color = ArchiveMuted, fontSize = 12.sp)
            else -> InlineTrackRows(recommendations.take(8), onPlay, currentTrack, onQueue, onDownload, onAddToPlaylist)
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, glyph: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 116.dp).clickable(onClick = onClick),
        color = ArchivePanel,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ArchiveBorder),
    ) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(glyph, color = ArchiveAccent, fontSize = 24.sp)
            Text(title, color = ArchiveText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = ArchiveMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    category: SearchCategory,
    onCategory: (SearchCategory) -> Unit,
    results: List<MusicTrack>,
    isBusy: Boolean,
    onPlay: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
    onQueue: ((MusicTrack) -> Unit)? = null,
    onDownload: ((MusicTrack) -> Unit)? = null,
    onAddToPlaylist: ((MusicTrack) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (query.isBlank()) "Find your next favorite" else "Results for “$query”", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("Search is powered by ArchiveTune's existing YouTube Music core.", color = ArchiveMuted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchCategory.entries.forEach { item ->
                val label = when (item) {
                    SearchCategory.SONGS -> "Songs"
                    SearchCategory.VIDEOS -> "Videos"
                    SearchCategory.ALBUMS -> "Albums"
                    SearchCategory.ARTISTS -> "Artists"
                    SearchCategory.PLAYLISTS -> "Playlists"
                    SearchCategory.PODCASTS -> "Podcasts"
                }
                val selected = category == item
                Button(
                    onClick = { onCategory(item) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) ArchiveAccent else ArchivePanel,
                        contentColor = if (selected) Color(0xFF23133F) else ArchiveMuted,
                    ),
                ) { Text(label, fontSize = 12.sp) }
            }
        }
        when {
            isBusy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ArchiveAccent) }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⌕", color = ArchiveAccent, fontSize = 36.sp)
                    Text("Search for music", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                    Text("Enter a title, artist, album, or playlist in the bar above.", color = ArchiveMuted, fontSize = 12.sp)
                }
            }
            else -> TrackList(results, onPlay, currentTrack, onQueue, onDownload, onAddToPlaylist)
        }
    }
}

@Composable
private fun LibraryContent(
    tracks: List<MusicTrack>,
    onImport: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
    onQueue: ((MusicTrack) -> Unit)? = null,
    onDownload: ((MusicTrack) -> Unit)? = null,
    onAddToPlaylist: ((MusicTrack) -> Unit)? = null,
    onRemove: ((MusicTrack) -> Unit)? = null,
) {
    var filter by remember { mutableStateOf("") }
    val normalizedFilter = filter.trim().lowercase()
    val visibleTracks = if (normalizedFilter.isBlank()) tracks else tracks.filter { track ->
        listOf(track.title, track.artist, track.album.orEmpty(), track.location.orEmpty())
            .any { value -> value.lowercase().contains(normalizedFilter) }
    }
    Column(modifier = Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your library", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Local audio indexed on this computer · ${tracks.size} tracks", color = ArchiveMuted, fontSize = 12.sp)
            }
            Button(onClick = onImport, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color(0xFF23133F))) { Text("＋  Add music") }
        }
        if (tracks.isNotEmpty()) {
            TextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter by title, artist, album, or file path…", color = ArchiveMuted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ArchivePanel,
                    unfocusedContainerColor = ArchivePanel,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("▤", color = ArchiveAccent, fontSize = 38.sp)
                    Text("No local music yet", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                    Text("Add audio files or choose a music folder. Files stay on your computer.", color = ArchiveMuted, fontSize = 12.sp)
                    Button(onClick = onImport, shape = RoundedCornerShape(12.dp)) { Text("Import music") }
                }
            }
        } else if (visibleTracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("No matching local tracks", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                    Text("Try a different title, artist, album, or file path.", color = ArchiveMuted, fontSize = 12.sp)
                }
            }
        } else {
            TrackList(visibleTracks, onPlay, currentTrack, onQueue, onDownload, onAddToPlaylist, onRemove)
        }
    }
}

@Composable
private fun SimpleTrackContent(
    title: String,
    subtitle: String,
    tracks: List<MusicTrack>,
    emptyTitle: String,
    emptyDescription: String,
    onPlay: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
    onQueue: ((MusicTrack) -> Unit)? = null,
    onDownload: ((MusicTrack) -> Unit)? = null,
    onAddToPlaylist: ((MusicTrack) -> Unit)? = null,
    onRemove: ((MusicTrack) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = ArchiveMuted, fontSize = 12.sp)
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("◷", color = ArchiveAccent, fontSize = 36.sp)
                    Text(emptyTitle, color = ArchiveText, fontWeight = FontWeight.SemiBold)
                    Text(emptyDescription, color = ArchiveMuted, fontSize = 12.sp)
                }
            }
        } else TrackList(tracks, onPlay, currentTrack, onQueue, onDownload, onAddToPlaylist, onRemove)
    }
}

@Composable
private fun InlineTrackRows(
    tracks: List<MusicTrack>,
    onPlay: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
    onQueue: ((MusicTrack) -> Unit)? = null,
    onDownload: ((MusicTrack) -> Unit)? = null,
    onAddToPlaylist: ((MusicTrack) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tracks.forEach { track ->
            TrackRow(
                track = track,
                isCurrent = currentTrack?.let { it.stableKey == track.stableKey } == true,
                onPlay = { onPlay(track) },
                onQueue = onQueue?.let { callback -> { callback(track) } },
                onDownload = onDownload?.let { callback -> { callback(track) } },
                onAddToPlaylist = onAddToPlaylist?.let { callback -> { callback(track) } },
                onRemove = null,
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<MusicTrack>,
    onPlay: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
    onQueue: ((MusicTrack) -> Unit)? = null,
    onDownload: ((MusicTrack) -> Unit)? = null,
    onAddToPlaylist: ((MusicTrack) -> Unit)? = null,
    onRemove: ((MusicTrack) -> Unit)? = null,
    onMoveUp: ((MusicTrack) -> Unit)? = null,
    onMoveDown: ((MusicTrack) -> Unit)? = null,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(tracks, key = { "${it.source}:${it.id}" }) { track ->
            TrackRow(
                track = track,
                isCurrent = currentTrack?.let { it.id == track.id && it.source == track.source } == true,
                onPlay = { onPlay(track) },
                onQueue = onQueue?.let { callback -> { callback(track) } },
                onDownload = onDownload?.let { callback -> { callback(track) } },
                onAddToPlaylist = onAddToPlaylist?.let { callback -> { callback(track) } },
                onRemove = onRemove?.let { remover -> { remover(track) } },
                onMoveUp = onMoveUp?.let { mover -> { mover(track) } },
                onMoveDown = onMoveDown?.let { mover -> { mover(track) } },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: MusicTrack,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onQueue: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (isCurrent) ArchiveAccent.copy(alpha = 0.10f) else ArchivePanel)
            .clickable(onClick = onPlay).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(49.dp).clip(RoundedCornerShape(10.dp)).background(ArchivePanelRaised), contentAlignment = Alignment.Center) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(model = track.artworkUrl, contentDescription = "Artwork for ${track.title}", modifier = Modifier.fillMaxSize())
            } else Text(if (track.source == TrackSource.LOCAL) "♫" else "♪", fontSize = 24.sp, color = ArchiveAccent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = if (isCurrent) ArchiveAccent else ArchiveText, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Text(
                listOfNotNull(track.artist.takeIf(String::isNotBlank), track.album?.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "${track.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ArchiveTune" },
                color = ArchiveMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
        Text(track.durationMs?.takeIf { it > 0L }?.let(::formatTime) ?: if (track.source == TrackSource.LOCAL) "LOCAL" else track.kind.name, color = ArchiveMuted, fontSize = 10.sp)
        ActionPill("▶", enabled = track.canPlay || track.location != null, description = "Play ${track.title}", onClick = onPlay, primary = isCurrent)
        if (track.canPlay) {
            onQueue?.let { ActionPill("＋Q", description = "Add ${track.title} to queue", onClick = it) }
            onDownload?.let { ActionPill("↓", description = "Download ${track.title}", onClick = it) }
            onAddToPlaylist?.let { ActionPill("＋♫", description = "Add ${track.title} to playlist", onClick = it) }
        }
        onMoveUp?.let { ActionPill("↑", description = "Move ${track.title} up", onClick = it) }
        onMoveDown?.let { ActionPill("↓", description = "Move ${track.title} down", onClick = it) }
        if (onRemove != null) ActionPill("×", description = "Remove ${track.title}", onClick = onRemove)
    }
}

@Composable
private fun ActionPill(
    label: String,
    enabled: Boolean = true,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(9.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (primary) ArchiveAccent else ArchiveMuted,
            disabledContentColor = ArchiveMuted.copy(alpha = 0.4f),
        ),
    ) { Text(label, fontSize = 10.sp, maxLines = 1) }
}

@Composable
private fun PlaylistContent(
    playlists: List<MusicPlaylist>,
    selectedPlaylistId: String?,
    newPlaylistName: String,
    renamePlaylistValue: String,
    onNewNameChange: (String) -> Unit,
    onRenameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
    onPlayAll: (List<MusicTrack>) -> Unit,
    onRemoveTrack: (MusicTrack) -> Unit,
    onMoveTrackUp: (MusicTrack) -> Unit,
    onMoveTrackDown: (MusicTrack) -> Unit,
    onQueue: (MusicTrack) -> Unit,
    onDownload: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    currentTrack: MusicTrack?,
) {
    val selected = playlists.firstOrNull { it.id == selectedPlaylistId }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Your playlists", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Create and manage persistent local playlists", color = ArchiveMuted, fontSize = 12.sp)
            }
            TextField(
                value = newPlaylistName,
                onValueChange = onNewNameChange,
                placeholder = { Text("New playlist name") },
                singleLine = true,
                modifier = Modifier.widthIn(min = 160.dp, max = 250.dp),
                shape = RoundedCornerShape(11.dp),
            )
            Button(onClick = onCreate, enabled = newPlaylistName.isNotBlank(), shape = RoundedCornerShape(11.dp)) { Text("Create") }
        }
        if (playlists.isEmpty()) {
            Surface(color = ArchivePanel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("No playlists yet", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                    Text("Create a playlist here, then use the +♫ action beside songs to add them.", color = ArchiveMuted, fontSize = 12.sp)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                playlists.forEach { playlist ->
                    Surface(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(playlist.id) },
                        color = if (playlist.id == selectedPlaylistId) ArchiveAccent.copy(alpha = 0.18f) else ArchivePanel,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (playlist.id == selectedPlaylistId) ArchiveAccent else ArchiveBorder),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(playlist.name, color = ArchiveText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.tracks.size} tracks", color = ArchiveMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                TextField(value = renamePlaylistValue, onValueChange = onRenameChange, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                OutlinedButton(onClick = onRename, enabled = renamePlaylistValue.isNotBlank()) { Text("Rename") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
                Button(onClick = { onPlayAll(selected.tracks) }, enabled = selected.tracks.isNotEmpty()) { Text("Play all") }
            }
            if (selected.tracks.isEmpty()) {
                Text("This playlist is empty. Search for music and use +♫ to add the selected playlist.", color = ArchiveMuted, fontSize = 12.sp)
            } else {
                TrackList(selected.tracks, onPlay, currentTrack, onQueue, onDownload, onAddToPlaylist, onRemoveTrack, onMoveTrackUp, onMoveTrackDown)
            }
        }
    }
}

@Composable
private fun PodcastSubscriptionsContent(
    podcasts: List<MusicTrack>,
    currentTrack: MusicTrack?,
    onOpen: (MusicTrack) -> Unit,
    onUnsubscribe: (MusicTrack) -> Unit,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your podcasts", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Followed podcasts are saved locally on this computer.", color = ArchiveMuted, fontSize = 12.sp)
            }
            Button(onClick = onSearch, shape = RoundedCornerShape(12.dp)) { Text("Find podcasts") }
        }
        if (podcasts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("◉", color = ArchiveAccent, fontSize = 38.sp)
                    Text("No followed podcasts", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                    Text("Search YouTube Music for a podcast, open it, then choose Follow podcast.", color = ArchiveMuted, fontSize = 12.sp)
                    Button(onClick = onSearch, shape = RoundedCornerShape(12.dp)) { Text("Search podcasts") }
                }
            }
        } else {
            TrackList(
                tracks = podcasts,
                onPlay = onOpen,
                currentTrack = currentTrack,
                onRemove = onUnsubscribe,
            )
        }
    }
}

@Composable
private fun CollectionContent(
    collection: BrowseCollection,
    currentTrack: MusicTrack?,
    onPlay: (MusicTrack) -> Unit,
    onPlayAll: () -> Unit,
    onQueue: (MusicTrack) -> Unit,
    onDownload: (MusicTrack) -> Unit,
    onAddToPlaylist: (MusicTrack) -> Unit,
    isPodcastSubscribed: Boolean = false,
    onTogglePodcastSubscription: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(126.dp).clip(RoundedCornerShape(18.dp)).background(ArchivePanelRaised), contentAlignment = Alignment.Center) {
                if (!collection.artworkUrl.isNullOrBlank()) AsyncImage(model = collection.artworkUrl, contentDescription = "${collection.title} artwork", modifier = Modifier.fillMaxSize())
                else Text("♫", color = ArchiveAccent, fontSize = 42.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(collection.kind.name.lowercase().replaceFirstChar { it.uppercase() }, color = ArchiveAccent, fontSize = 11.sp, letterSpacing = 1.2.sp)
                Text(collection.title, color = ArchiveText, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(collection.subtitle, color = ArchiveMuted, fontSize = 13.sp)
                Text("${collection.tracks.size} playable items", color = ArchiveMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPlayAll, enabled = collection.tracks.isNotEmpty(), shape = RoundedCornerShape(11.dp)) { Text("Play all") }
                    if (collection.kind == MusicKind.PODCAST && onTogglePodcastSubscription != null) {
                        if (isPodcastSubscribed) {
                            OutlinedButton(onClick = onTogglePodcastSubscription, shape = RoundedCornerShape(11.dp)) { Text("Unfollow podcast") }
                        } else {
                            Button(onClick = onTogglePodcastSubscription, shape = RoundedCornerShape(11.dp)) { Text("Follow podcast") }
                        }
                    }
                }
            }
        }
        Divider(color = ArchiveBorder)
        if (collection.tracks.isEmpty()) {
            Text("No playable tracks were found in this collection.", color = ArchiveMuted)
        } else TrackList(collection.tracks, onPlay, currentTrack, onQueue, onDownload, onAddToPlaylist)
    }
}

@Composable
private fun QueueContent(
    queue: PlaybackQueueSnapshot,
    currentTrack: MusicTrack?,
    onPlay: (MusicTrack) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onRemove: (MusicTrack) -> Unit,
    onMoveUp: (MusicTrack) -> Unit,
    onMoveDown: (MusicTrack) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Playback queue", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("${queue.tracks.size} tracks · Current position ${if (queue.currentIndex >= 0) queue.currentIndex + 1 else 0}", color = ArchiveMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrevious) { Text("Previous") }
            OutlinedButton(onClick = onNext) { Text("Next") }
            OutlinedButton(onClick = onShuffle) { Text(if (queue.shuffleEnabled) "Shuffle: On" else "Shuffle: Off") }
            OutlinedButton(onClick = onRepeat) { Text("Repeat: ${queue.repeatMode.name.lowercase()}") }
            OutlinedButton(onClick = onClear, enabled = queue.tracks.isNotEmpty()) { Text("Clear queue") }
        }
        if (queue.tracks.isEmpty()) {
            Text("Your queue is empty. Use +Q next to a track to add it, or play a playlist.", color = ArchiveMuted)
        } else TrackList(
            queue.tracks, onPlay, currentTrack,
            onRemove = onRemove,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }
}

@Composable
private fun LyricsContent(
    track: MusicTrack?,
    lyrics: LyricsSheet?,
    positionMs: Long,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onReload: () -> Unit,
) {
    val activeLine = lyrics?.lines?.indexOfLast { it.timeMs != null && it.timeMs <= positionMs } ?: -1
    val lyricsListState = rememberLazyListState()
    LaunchedEffect(lyrics?.trackKey, activeLine) {
        if (lyrics?.synchronized == true && activeLine >= 0) {
            runCatching { lyricsListState.animateScrollToItem(activeLine) }
        }
    }
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Lyrics", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(track?.let { "${it.title} · ${it.artist}" } ?: "Start playback to find lyrics", color = ArchiveMuted, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onReload, enabled = track != null) { Text("Refresh lyrics") }
        }
        when {
            track == null -> Text("Choose a track first. Lyrics are looked up automatically when playback changes.", color = ArchiveMuted)
            lyrics == null || lyrics.trackKey != track.stableKey -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ArchiveAccent) }
            lyrics.lines.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No lyrics found", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("None of the selected lyrics providers returned a match. Choose a different provider in Settings or refresh to retry.", color = ArchiveMuted, fontSize = 12.sp)
            }
            else -> {
                Text("Source: ${lyrics.source} · ${if (lyrics.synchronized) "time-synchronized lyrics" else "plain lyrics"}", color = ArchiveMuted, fontSize = 11.sp)
                LazyColumn(state = lyricsListState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(lyrics.lines.indices.toList(), key = { it }) { index ->
                        val line = lyrics.lines[index]
                        val activeLineColor = if (index == activeLine && lyrics.synchronized) ArchiveAccent else if (line.text.isBlank()) ArchiveMuted.copy(alpha = 0.5f) else ArchiveText
                        val lineAnnotated = buildAnnotatedString {
                            if (line.words.isNotEmpty()) {
                                line.words.forEach { word ->
                                    val activeWord = index == activeLine && lyrics.synchronized && positionMs >= word.timeMs && positionMs < word.endTimeMs
                                    withStyle(
                                        SpanStyle(
                                            color = if (activeWord) ArchiveAccent else activeLineColor,
                                            fontWeight = if (activeWord) FontWeight.Bold else if (index == activeLine) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                    ) {
                                        append(word.text)
                                    }
                                }
                            } else {
                                append(line.text.ifBlank { "♪" })
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (index == activeLine && lyrics.synchronized) ArchiveAccent.copy(alpha = 0.18f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                lineAnnotated,
                                color = activeLineColor,
                                fontSize = if (index == activeLine) 18.sp else 15.sp,
                                fontWeight = if (index == activeLine) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (showRomanization && !line.romanizedText.isNullOrBlank()) {
                                Text(line.romanizedText, color = ArchiveMuted, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                            if (showTranslation && !line.translation.isNullOrBlank()) {
                                Text(line.translation, color = ArchiveMuted, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicRecognitionContent(current: MusicRecognitionResult?, onIdentify: () -> Unit, onIdentifyAmbient: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Identify music", "Chromaprint + AcoustID")
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Identify a local audio file", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Choose a recording to calculate its acoustic fingerprint. Only the fingerprint and duration are sent to AcoustID, not the whole audio file.", color = ArchiveMuted, fontSize = 13.sp, lineHeight = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onIdentify, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text("Choose file") }
                    OutlinedButton(onClick = onIdentifyAmbient, shape = RoundedCornerShape(11.dp)) { Text("Listen for 12 seconds") }
                }
                Text("Microphone recognition records only after you press the button. The sample is fingerprinted locally then deleted; only the fingerprint and duration are sent to AcoustID. A working microphone, fpcalc and ACOUSTID_API_KEY are required.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        if (current != null) {
            Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(current.title, color = ArchiveText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    if (current.artist.isNotBlank()) Text(current.artist, color = ArchiveMuted, fontSize = 15.sp)
                    if (current.album.isNotBlank()) Text(current.album, color = ArchiveMuted, fontSize = 13.sp)
                    if (current.confidence > 0f) Text("Fingerprint confidence: ${(current.confidence * 100).toInt()}%", color = ArchiveAccent, fontWeight = FontWeight.Medium)
                    Text(current.message, color = ArchiveMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** Compact secondary window; a desktop-native equivalent to Android playback widgets. */
@Composable
fun ArchiveTuneMiniPlayer(repository: ArchiveTuneRepository, onClose: () -> Unit = {}) {
    val player by repository.playerState.collectAsState()
    val scope = rememberCoroutineScope()
    Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(player.currentTrack?.title ?: "ArchiveTune", color = ArchiveText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(player.currentTrack?.artist ?: "Nothing playing", color = ArchiveMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onClose, shape = RoundedCornerShape(8.dp)) { Text("×") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { scope.launch { repository.playPrevious() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp)) { Text("‹") }
                Button(onClick = { scope.launch { repository.togglePlayback() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text(if (player.isPlaying) "Pause" else "Play") }
                OutlinedButton(onClick = { scope.launch { repository.playNext() } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp)) { Text("›") }
            }
        }
    }
}

@Composable
private fun UtilityContent(title: String, subtitle: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = ArchiveMuted, fontSize = 12.sp)
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, color = ArchiveText, fontSize = 14.sp, lineHeight = 22.sp)
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    volume: Float,
    preferences: DesktopPreferences,
    lastFmStatus: LastFmStatus,
    listenBrainzStatus: ListenBrainzStatus,
    onVolumeChange: (Float) -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerGainChange: (Int, Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleScrobbling: (Boolean) -> Unit,
    onStartLastFm: () -> Unit,
    onFinishLastFm: () -> Unit,
    onDisconnectLastFm: () -> Unit,
    onConnectListenBrainz: (String) -> Unit,
    onDisconnectListenBrainz: () -> Unit,
    onToggleListenBrainz: (Boolean) -> Unit,
    onStop: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportAndroidBackup: () -> Unit,
    onScheduledBackupEnabledChange: (Boolean) -> Unit,
    onScheduledBackupIntervalChange: (Int) -> Unit,
    onSaveVolume: (Float) -> Unit,
    onThemeChange: (String) -> Unit,
    onLyricsProviderChange: (String) -> Unit,
    onLyricsProviderOrderChange: (List<String>) -> Unit,
    onEnabledLyricsProvidersChange: (List<String>) -> Unit,
    onToggleLyricsTranslation: (Boolean) -> Unit,
    onToggleLyricsRomanization: (Boolean) -> Unit,
    onStartMinimizedChange: (Boolean) -> Unit,
    onCloseToTrayChange: (Boolean) -> Unit,
    spotifyStatus: SpotifyAccountStatus,
    spotifyPlayback: SpotifyPlaybackStatus,
    dlnaRenderers: List<DlnaRenderer>,
    currentTrack: MusicTrack?,
    onDiscordPresenceEnabledChange: (Boolean) -> Unit,
    onDiscordApplicationIdChange: (String) -> Unit,
    onSpotifyConnect: (String) -> Unit,
    onSpotifyDisconnect: () -> Unit,
    onRefreshSpotifyPlayback: () -> Unit,
    onSpotifyPlaybackCommand: (SpotifyPlaybackCommand) -> Unit,
    onDiscoverDlna: () -> Unit,
    onCastCurrentTrack: (String) -> Unit,
    onStopCasting: () -> Unit,
    onRecognizeLocalAudio: () -> Unit,
) {
    var selectedSpeed by remember(preferences.playbackSpeed) { mutableStateOf(preferences.playbackSpeed) }
    var selectedVolume by remember(volume) { mutableStateOf(volume) }
    var listenBrainzToken by remember { mutableStateOf("") }
    var spotifyClientIdText by remember(preferences.spotifyClientId) { mutableStateOf(preferences.spotifyClientId) }
    var discordApplicationIdText by remember(preferences.discordApplicationId) { mutableStateOf(preferences.discordApplicationId) }
    LaunchedEffect(listenBrainzStatus.connected) {
        if (listenBrainzStatus.connected) listenBrainzToken = ""
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", color = ArchiveText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Desktop playback preferences", color = ArchiveMuted, fontSize = 12.sp)
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Playback", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume", color = ArchiveMuted, modifier = Modifier.width(90.dp), fontSize = 13.sp)
                    Slider(
                        value = selectedVolume.coerceIn(0f, 1f),
                        onValueChange = { selectedVolume = it; onVolumeChange(it) },
                        onValueChangeFinished = { onSaveVolume(selectedVolume) },
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(selectedVolume * 100).toInt()}%", color = ArchiveText, modifier = Modifier.width(50.dp), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Playback speed", color = ArchiveMuted, modifier = Modifier.width(110.dp), fontSize = 13.sp)
                    Slider(
                        value = selectedSpeed.coerceIn(0.5f, 2f),
                        onValueChange = { selectedSpeed = it },
                        onValueChangeFinished = { onSpeedChange(selectedSpeed) },
                        valueRange = 0.5f..2f,
                        steps = 5,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${selectedSpeed.toString().take(4)}×", color = ArchiveText, modifier = Modifier.width(55.dp), fontSize = 12.sp)
                }
                Divider(color = ArchiveBorder)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Equalizer", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text("10-band desktop audio equalizer · ±12 dB", color = ArchiveMuted, fontSize = 11.sp)
                    }
                    Switch(checked = preferences.equalizerEnabled, onCheckedChange = onEqualizerEnabledChange)
                }
                val bandLabels = listOf("32 Hz", "64 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")
                bandLabels.forEachIndexed { index, label ->
                    val storedGain = preferences.equalizerGainsDb.getOrElse(index) { 0f }
                    var sliderGain by remember(index, storedGain) { mutableStateOf(storedGain.coerceIn(-12f, 12f)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = ArchiveMuted, modifier = Modifier.width(70.dp), fontSize = 12.sp)
                        Slider(
                            value = sliderGain,
                            onValueChange = { sliderGain = it },
                            onValueChangeFinished = { onEqualizerGainChange(index, sliderGain) },
                            valueRange = -12f..12f,
                            enabled = preferences.equalizerEnabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${if (sliderGain > 0) "+" else ""}${sliderGain.toInt()} dB", color = ArchiveText, modifier = Modifier.width(55.dp), fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = { (0..9).forEach { onEqualizerGainChange(it, 0f) } }, enabled = preferences.equalizerEnabled, shape = RoundedCornerShape(11.dp)) { Text("Reset equalizer") }
                OutlinedButton(onClick = onStop, shape = RoundedCornerShape(11.dp)) { Text("Stop playback") }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Last.fm scrobbling", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text(lastFmStatus.message, color = ArchiveMuted, fontSize = 13.sp, lineHeight = 20.sp)
                if (lastFmStatus.connected) {
                    Text("Account: ${lastFmStatus.username.ifBlank { "Connected" }}", color = ArchiveText, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onToggleScrobbling(!preferences.scrobblingEnabled) },
                            shape = RoundedCornerShape(11.dp),
                        ) { Text(if (preferences.scrobblingEnabled) "Scrobbling: On" else "Scrobbling: Off") }
                        OutlinedButton(onClick = onDisconnectLastFm, shape = RoundedCornerShape(11.dp)) { Text("Disconnect") }
                    }
                    Text("Now Playing is sent on track start. A scrobble is submitted after at least half the track or four minutes, whichever comes first (tracks under 30 seconds are skipped).", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 17.sp)
                } else {
                    if (lastFmStatus.authorizationPending) {
                        Button(onClick = onFinishLastFm, shape = RoundedCornerShape(11.dp)) { Text("Finish sign-in") }
                        Text("After approving ArchiveTune on last.fm, return here and finish sign-in.", color = ArchiveMuted, fontSize = 11.sp)
                    } else {
                        Button(onClick = onStartLastFm, enabled = lastFmStatus.configured, shape = RoundedCornerShape(11.dp)) { Text("Connect Last.fm") }
                        if (!lastFmStatus.configured) {
                            Text("For this desktop build, set LASTFM_API_KEY and LASTFM_SECRET in your environment before launching. They are not bundled in the source archive.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("ListenBrainz scrobbling", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text(listenBrainzStatus.message, color = ArchiveMuted, fontSize = 12.sp, lineHeight = 19.sp)
                if (listenBrainzStatus.connected) {
                    if (listenBrainzStatus.username.isNotBlank()) Text("Account: ${listenBrainzStatus.username}", color = ArchiveText, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onToggleListenBrainz(!preferences.listenBrainzEnabled) }, shape = RoundedCornerShape(11.dp)) {
                            Text(if (preferences.listenBrainzEnabled) "Scrobbling: On" else "Scrobbling: Off")
                        }
                        OutlinedButton(onClick = onDisconnectListenBrainz, shape = RoundedCornerShape(11.dp)) { Text("Disconnect") }
                    }
                    Text("ArchiveTune sends a playing-now update at track start and submits the listen after the same completion threshold used for Last.fm. The personal access token stays separate from backups.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 17.sp)
                } else {
                    TextField(
                        value = listenBrainzToken,
                        onValueChange = { listenBrainzToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ListenBrainz personal access token", color = ArchiveMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ArchivePanelRaised,
                            unfocusedContainerColor = ArchivePanelRaised,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ArchiveAccent,
                        ),
                    )
                    Button(
                        onClick = { onConnectListenBrainz(listenBrainzToken) },
                        enabled = listenBrainzToken.isNotBlank(),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White),
                    ) { Text("Validate and connect") }
                    Text("Create a personal access token in your ListenBrainz account settings. The token is validated against ListenBrainz before it is stored locally.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Lyrics providers", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Choose a preferred source or let ArchiveTune search its integrated providers concurrently. Changing this setting refreshes the current track's lyrics.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
                val lyricProviders = listOf(
                    "Auto", "YouTube Music", "YouTube Subtitle", "SimpMusic", "Unison",
                    "YouLyPlus", "BetterLyrics", "BetterLyrics Portato", "LRCLIB", "KuGou",
                    "Megalobiz", "Paxsenix: Apple Music", "Paxsenix: Spotify", "Paxsenix: Musixmatch", "Paxsenix (Auto)",
                )
                lyricProviders.chunked(3).forEach { rowProviders ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        rowProviders.forEach { providerName ->
                            if (preferences.lyricsProvider == providerName) {
                                Button(onClick = { onLyricsProviderChange(providerName) }, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text(providerName) }
                            } else {
                                OutlinedButton(onClick = { onLyricsProviderChange(providerName) }, shape = RoundedCornerShape(11.dp)) { Text(providerName) }
                            }
                        }
                    }
                }
                Divider(color = ArchiveBorder)
                Text("Automatic lookup priority", color = ArchiveText, fontWeight = FontWeight.Medium)
                Text("When Auto is selected, enabled providers run concurrently. Results with equal timing quality prefer providers higher in this list.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 16.sp)
                preferences.lyricsProviderOrder.forEachIndexed { index, providerName ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}.", color = ArchiveMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                        Text(providerName, color = ArchiveText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = providerName in preferences.enabledLyricsProviders,
                            onCheckedChange = { enabled ->
                                val updated = if (enabled) preferences.enabledLyricsProviders + providerName
                                    else preferences.enabledLyricsProviders - providerName
                                onEnabledLyricsProvidersChange(updated)
                            },
                        )
                        OutlinedButton(
                            onClick = {
                                val reordered = preferences.lyricsProviderOrder.toMutableList()
                                val previous = reordered[index - 1]
                                reordered[index - 1] = reordered[index]
                                reordered[index] = previous
                                onLyricsProviderOrderChange(reordered)
                            },
                            enabled = index > 0,
                            shape = RoundedCornerShape(9.dp),
                        ) { Text("↑") }
                        OutlinedButton(
                            onClick = {
                                val reordered = preferences.lyricsProviderOrder.toMutableList()
                                val next = reordered[index + 1]
                                reordered[index + 1] = reordered[index]
                                reordered[index] = next
                                onLyricsProviderOrderChange(reordered)
                            },
                            enabled = index < preferences.lyricsProviderOrder.lastIndex,
                            shape = RoundedCornerShape(9.dp),
                        ) { Text("↓") }
                    }
                }
                Divider(color = ArchiveBorder)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Show translations", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text("Use provider translations; missing lines use LibreTranslate only when ARCHIVETUNE_TRANSLATION_URL is configured. In that case, lyric text is sent to that endpoint.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Switch(checked = preferences.showLyricsTranslation, onCheckedChange = onToggleLyricsTranslation)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Show romanization", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text("Prefer provider romanization, with offline ICU transliteration as a best-effort fallback for supported scripts.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Switch(checked = preferences.showLyricsRomanization, onCheckedChange = onToggleLyricsRomanization)
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Choose the desktop color palette.", color = ArchiveMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Dark", "Obsidian", "Light").forEach { themeName ->
                        val selected = preferences.theme == themeName || (preferences.theme == "System" && themeName == "Dark")
                        if (selected) {
                            Button(onClick = { onThemeChange(themeName) }, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text(themeName) }
                        } else {
                            OutlinedButton(onClick = { onThemeChange(themeName) }, shape = RoundedCornerShape(11.dp)) { Text(themeName) }
                        }
                    }
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Window behavior", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Start minimized", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text("Start hidden in the system tray when the tray is available.", color = ArchiveMuted, fontSize = 12.sp)
                    }
                    Switch(checked = preferences.startMinimized, onCheckedChange = onStartMinimizedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Close to system tray", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text("Closing the window hides it instead of quitting. Use Quit from the tray menu to exit.", color = ArchiveMuted, fontSize = 12.sp)
                    }
                    Switch(checked = preferences.closeToTray, onCheckedChange = onCloseToTrayChange)
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Backup and restore", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Desktop backups include playlists, queue, history, preferences, library metadata and downloaded audio. Original local music files and account credentials are excluded.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onExportBackup, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text("Export backup") }
                    OutlinedButton(onClick = onImportBackup, shape = RoundedCornerShape(11.dp)) { Text("Restore desktop") }
                    OutlinedButton(onClick = onImportAndroidBackup, shape = RoundedCornerShape(11.dp)) { Text("Import Android ZIP") }
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Automatic backups", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Desktop replacement for Android WorkManager backups. When enabled, ArchiveTune creates an initial snapshot on its next check, then checks every 15 minutes and creates a new ZIP when the selected interval has elapsed. It keeps the newest 10 automatic archives under ~/.archivetune/backups.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 19.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic backup schedule", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text(if (preferences.scheduledBackupEnabled) "Enabled · every ${preferences.scheduledBackupIntervalHours} hours" else "Disabled · no background archives are created", color = ArchiveMuted, fontSize = 11.sp)
                    }
                    Switch(checked = preferences.scheduledBackupEnabled, onCheckedChange = onScheduledBackupEnabledChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(6, 12, 24, 72).forEach { hours ->
                        if (preferences.scheduledBackupIntervalHours == hours) {
                            Button(onClick = { onScheduledBackupIntervalChange(hours) }, enabled = preferences.scheduledBackupEnabled, shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text("${hours}h") }
                        } else {
                            OutlinedButton(onClick = { onScheduledBackupIntervalChange(hours) }, enabled = preferences.scheduledBackupEnabled, shape = RoundedCornerShape(9.dp)) { Text("${hours}h") }
                        }
                    }
                }
                Text("Automatic backups are saved locally; no cloud upload is performed. The computer and ArchiveTune must be running at the time of the scheduled check.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Desktop data", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Library index, playlists, listening history, settings and downloads are stored locally under ~/.archivetune. Audio files are copied only when you use Download; Import library only indexes files in place.", color = ArchiveMuted, fontSize = 13.sp, lineHeight = 20.sp)
                Text("Version 15.1.0 · Compose Multiplatform desktop", color = ArchiveMuted, fontSize = 11.sp)
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Discord Rich Presence", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Uses Discord's local desktop IPC channel. No Discord user token is requested or stored. Create an application in the Discord Developer Portal and enter its Application ID below.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
                TextField(
                    value = discordApplicationIdText,
                    onValueChange = { value -> discordApplicationIdText = value.filter(Char::isDigit).take(24); onDiscordApplicationIdChange(discordApplicationIdText) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Discord Application ID") },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Publish current track as Listening to ArchiveTune", color = ArchiveText, fontWeight = FontWeight.Medium)
                        Text("Requires the Discord desktop client to be running on this computer.", color = ArchiveMuted, fontSize = 11.sp)
                    }
                    Switch(checked = preferences.discordPresenceEnabled, onCheckedChange = onDiscordPresenceEnabledChange)
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Spotify account", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Browser login with Authorization Code + PKCE. Register a Spotify Developer app and add Redirect URI http://127.0.0.1:43821/callback. No client secret is embedded. This controls playback on a Spotify Connect device; it does not decode or redistribute Spotify audio.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
                TextField(value = spotifyClientIdText, onValueChange = { spotifyClientIdText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Spotify Developer Client ID") })
                Text(spotifyStatus.message, color = if (spotifyStatus.connected) ArchiveAccent else ArchiveMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = { onSpotifyConnect(spotifyClientIdText.trim()) }, enabled = spotifyClientIdText.isNotBlank(), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text(if (spotifyStatus.connected) "Reconnect" else "Connect Spotify") }
                    if (spotifyStatus.connected) OutlinedButton(onClick = onSpotifyDisconnect, shape = RoundedCornerShape(11.dp)) { Text("Disconnect") }
                    if (spotifyStatus.connected) OutlinedButton(onClick = onRefreshSpotifyPlayback, shape = RoundedCornerShape(11.dp)) { Text("Refresh playback") }
                }
                if (spotifyStatus.connected) {
                    Divider(color = ArchiveStroke)
                    Text("Spotify Connect playback", color = ArchiveText, fontWeight = FontWeight.Medium)
                    if (spotifyPlayback.title.isNotBlank()) {
                        Text(spotifyPlayback.title, color = ArchiveText, fontWeight = FontWeight.SemiBold)
                        if (spotifyPlayback.artist.isNotBlank()) Text(spotifyPlayback.artist, color = ArchiveMuted, fontSize = 12.sp)
                        if (spotifyPlayback.album.isNotBlank()) Text(spotifyPlayback.album, color = ArchiveMuted, fontSize = 11.sp)
                    }
                    Text(spotifyPlayback.message, color = ArchiveMuted, fontSize = 11.sp)
                    if (spotifyPlayback.activeDevice) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(onClick = { onSpotifyPlaybackCommand(SpotifyPlaybackCommand.PREVIOUS) }, shape = RoundedCornerShape(10.dp)) { Text("Previous") }
                            Button(onClick = { onSpotifyPlaybackCommand(SpotifyPlaybackCommand.TOGGLE_PLAY_PAUSE) }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text(if (spotifyPlayback.isPlaying) "Pause" else "Resume") }
                            OutlinedButton(onClick = { onSpotifyPlaybackCommand(SpotifyPlaybackCommand.NEXT) }, shape = RoundedCornerShape(10.dp)) { Text("Next") }
                        }
                        if (spotifyPlayback.durationMs > 0) {
                            val progress = "${spotifyPlayback.progressMs / 60_000}:${(spotifyPlayback.progressMs / 1_000 % 60).toString().padStart(2, '0')} / ${spotifyPlayback.durationMs / 60_000}:${(spotifyPlayback.durationMs / 1_000 % 60).toString().padStart(2, '0')}"
                            Text(progress, color = ArchiveMuted, fontSize = 11.sp)
                        }
                    }
                    Text("Playback control endpoints require Spotify Connect and may require Premium; Spotify's protected audio continues to play inside Spotify, not through ArchiveTune's local audio engine.", color = ArchiveMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Network playback (DLNA / UPnP)", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Discover compatible network speakers and TVs on the same LAN. Local files are served temporarily with byte-range support; YouTube Music items are resolved to direct streams when supported.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = onDiscoverDlna, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color.White)) { Text("Discover devices") }
                    OutlinedButton(onClick = onStopCasting, shape = RoundedCornerShape(11.dp)) { Text("Stop casting") }
                }
                if (dlnaRenderers.isEmpty()) Text("No renderer discovered yet. Ensure this computer and the speaker/TV are on the same LAN and multicast is allowed.", color = ArchiveMuted, fontSize = 12.sp)
                dlnaRenderers.forEach { renderer ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(renderer.name, modifier = Modifier.weight(1f), color = ArchiveText, fontSize = 13.sp)
                        OutlinedButton(onClick = { onCastCurrentTrack(renderer.id) }, enabled = currentTrack?.canPlay == true, shape = RoundedCornerShape(9.dp)) { Text("Cast current") }
                    }
                }
                if (currentTrack == null) Text("Start playback in ArchiveTune, then choose Cast current.", color = ArchiveMuted, fontSize = 11.sp)
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Music identification", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Desktop replacement for ShazamKit: fingerprint a local audio file with Chromaprint fpcalc and query AcoustID / MusicBrainz. Install fpcalc and set ACOUSTID_API_KEY to your registered AcoustID application key.", color = ArchiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
                OutlinedButton(onClick = onRecognizeLocalAudio, shape = RoundedCornerShape(11.dp)) { Text("Identify audio file") }
            }
        }
        Surface(color = ArchivePanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Desktop integration status", color = ArchiveText, fontWeight = FontWeight.SemiBold)
                Text("Desktop alternatives include global media keys, tray notifications, a floating mini-player, Discord IPC presence, browser-based Spotify PKCE login, DLNA/UPnP playback and Chromaprint/AcoustID recognition. Android Auto, home-screen widgets and Media3 notifications are represented by native desktop controls rather than Android framework stubs.", color = ArchiveMuted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun PlayerBar(
    snapshot: PlayerSnapshot,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    Surface(color = Color(0xFF14131B), modifier = Modifier.fillMaxWidth(), shadowElevation = 10.dp) {
        Column {
            Divider(color = ArchiveBorder)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(ArchivePanelRaised), contentAlignment = Alignment.Center) {
                    if (!snapshot.currentTrack?.artworkUrl.isNullOrBlank()) AsyncImage(model = snapshot.currentTrack?.artworkUrl, contentDescription = "Now playing artwork", modifier = Modifier.fillMaxSize())
                    else Text("♫", color = ArchiveAccent, fontSize = 24.sp)
                }
                Column(modifier = Modifier.widthIn(min = 100.dp, max = 190.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(snapshot.currentTrack?.title ?: "Choose something to play", color = ArchiveText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(snapshot.currentTrack?.artist ?: snapshot.statusMessage, color = ArchiveMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ActionPill("|◀", enabled = snapshot.currentTrack != null, description = "Previous track", onClick = onPrevious)
                Button(
                    onClick = onToggle,
                    enabled = snapshot.currentTrack != null && !snapshot.isLoading,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArchiveAccent, contentColor = Color(0xFF23133F)),
                ) { Text(if (snapshot.isLoading) "…" else if (snapshot.isPlaying) "Ⅱ" else "▶", fontSize = 15.sp) }
                ActionPill("▶|", enabled = snapshot.currentTrack != null, description = "Next track", onClick = onNext)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = snapshot.positionMs.coerceAtLeast(0L).toFloat().coerceAtMost(snapshot.durationMs.coerceAtLeast(1L).toFloat()),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..snapshot.durationMs.coerceAtLeast(1L).toFloat(),
                        enabled = snapshot.durationMs > 0L,
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(formatTime(snapshot.positionMs), color = ArchiveMuted, fontSize = 9.sp)
                        Text(formatTime(snapshot.durationMs), color = ArchiveMuted, fontSize = 9.sp)
                    }
                }
                ActionPill(if (shuffleEnabled) "⤨ ON" else "⤨", description = "Toggle shuffle", onClick = onShuffle, primary = shuffleEnabled)
                ActionPill(when (repeatMode) { RepeatMode.OFF -> "↻"; RepeatMode.ALL -> "↻ ALL"; RepeatMode.ONE -> "↻ 1" }, description = "Cycle repeat mode", onClick = onRepeat, primary = repeatMode != RepeatMode.OFF)
                ActionPill("♫", enabled = snapshot.currentTrack != null, description = "Open lyrics", onClick = onLyrics)
                ActionPill("☷", description = "Open queue", onClick = onQueue)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ArchiveText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(trailing.uppercase(), color = ArchiveMuted, fontSize = 9.sp, letterSpacing = 1.3.sp)
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hours = minutes / 60L
    return if (hours > 0) "$hours:${(minutes % 60L).toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    else "$minutes:${seconds.toString().padStart(2, '0')}"
}
