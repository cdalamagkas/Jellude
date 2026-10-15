package dev.jellude

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.jellude.core.Lyric
import dev.jellude.core.Lyrics
import dev.jellude.data.*
import dev.jellude.playback.MusicService
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF88CFFF),
                secondary = Color(0xFFB8A1FF), background = Color(0xFF101719), surface = Color(0xFF182225))) {
                Surface(Modifier.fillMaxSize()) { val model: MusicModel = viewModel(); MusicApp(model) }
            }
        }
    }
}

class MusicModel(application: android.app.Application) : AndroidViewModel(application) {
    val app = application as JelludeApp
    var repo by mutableStateOf(app.repository)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var offline by mutableStateOf(app.store.offline)
    var controller by mutableStateOf<MediaController?>(null)
    var playingId by mutableStateOf("")
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0)
    var duration by mutableLongStateOf(0)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffle by mutableStateOf(false)
    var queue by mutableStateOf<List<QueueTrack>>(emptyList())
    var queueIndex by mutableIntStateOf(-1)
    var nextIndex by mutableIntStateOf(-1)

    private fun updatePlayback(player: Player) {
        playingId = player.currentMediaItem?.mediaId.orEmpty()
        playing = player.isPlaying
        duration = player.duration.coerceAtLeast(0)
        repeatMode = player.repeatMode
        shuffle = player.shuffleModeEnabled
        queueIndex = player.currentMediaItemIndex
        nextIndex = if (repeatMode == Player.REPEAT_MODE_ONE) queueIndex else player.nextMediaItemIndex
        val timeline = player.currentTimeline
        val ordered = mutableListOf<QueueTrack>()
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != androidx.media3.common.C.INDEX_UNSET && ordered.size < player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            ordered += QueueTrack(index, item.mediaId, item.mediaMetadata.title?.toString().orEmpty())
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        queue = ordered
    }

    fun cycleRepeat() { controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        updatePlayback(it)
    } }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; updatePlayback(it) } }
    fun clearQueue() { controller?.let { it.stop(); it.clearMediaItems(); updatePlayback(it) } }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message?.take(240) ?: "Operation failed. Please retry." }
            finally { busy = false }
        }
    }
    fun login(url: String, username: String, password: String) = action {
        val session = JellyfinApi.login(app.http, url, username, password)
        withContext(Dispatchers.Main) { app.openSession(session); repo = app.repository }
        repo!!.sync()
    }
    suspend fun connect() {
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync()
        val connected = future.await()
        controller = connected
        connected.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updatePlayback(player)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                message = "Playback failed. Check this track's download or your server connection."
            }
        })
        updatePlayback(connected)
        try {
            while (currentCoroutineContext().isActive) {
                playingId = connected.currentMediaItem?.mediaId.orEmpty()
                playing = connected.isPlaying
                position = connected.currentPosition
                duration = connected.duration.coerceAtLeast(0)
                delay(500)
            }
        } finally { connected.release(); controller = null }
    }
    fun play(items: List<Item>, start: Int = 0) {
        if (items.isEmpty()) return
        val c = controller ?: return
        c.setMediaItems(items.map { MediaItem.Builder().setMediaId(it.id).build() }, start, 0)
        c.prepare(); c.play()
    }
    fun toggleOffline(value: Boolean) {
        app.store.offline = value; offline = value
        if (value) { app.http.dispatcher.cancelAll(); controller?.pause() }
        else { repo?.requestSync(); action { repo?.repair(null, false) } }
    }
    fun edit(id: String, operation: String, value: String) = action {
        repo!!.queueEdit(id, operation, value)
        if (!offline) repo!!.syncEdits()
    }
}

data class QueueTrack(val index: Int, val id: String, val title: String)

@Composable
fun MusicApp(model: MusicModel) {
    val repo = model.repo
    if (repo == null) { Login(model); return }
    LaunchedEffect(repo) { try { model.connect() } catch (e: Exception) { model.message = "Could not connect to playback service" } }
    val catalogue by repo.dao.observeItems().collectAsState(initial = emptyList())
    val assets by repo.dao.observeAssets().collectAsState(initial = emptyList())
    val selections by repo.dao.observeSelections().collectAsState(initial = emptyList())
    val members by repo.dao.observeMembers().collectAsState(initial = emptyList())
    val entries by repo.dao.observeEntries().collectAsState(initial = emptyList())
    val edits by repo.dao.observeEdits().collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("MusicAlbum") }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Item?>(null) }
    var selection by remember { mutableStateOf<Item?>(null) }
    var lyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    val ready = assets.filter { it.state == "Ready" }.map { it.trackId }.toSet()
    val assetMap = assets.associateBy { it.trackId }
    val available = remember(catalogue, ready, members, entries) { offlineItemIds(catalogue, ready, members, entries) }
    val downloads = selections.filter { !model.offline || it.id in available }
    val downloadStates = remember(catalogue, assets, selections, members, entries) {
        downloadStates(catalogue, assets, selections, members, entries)
    }
    BackHandler(enabled = detail != null && !lyrics && !showQueue && selection == null && !newPlaylist) { detail = null }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.message) { model.message?.let { snackbar.showSnackbar(it); model.message = null } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Column {
            catalogue.find { it.id == model.playingId }?.let { item ->
                MiniPlayer(model, item) { lyrics = true }
            }
            NavigationBar {
                val labels = listOf("Library", "Downloads", "Playlists", "Settings")
                val icons = listOf(Icons.Default.LibraryMusic, Icons.Default.Download, Icons.AutoMirrored.Filled.QueueMusic, Icons.Default.Settings)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index,
                    onClick = { tab = index; detail = null }, icon = { Icon(icons[index], label) }, label = { Text(label) }) }
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Image(painterResource(R.drawable.jellude_icon), null, Modifier.size(26.dp))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(detail?.title ?: listOf("Your music", "Available anywhere", "Your playlists", "Settings")[tab],
                        style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                    Text(if (model.offline) "Offline mode · ${ready.size} tracks ready" else "${ready.size} tracks ready offline",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (detail != null) IconButton(onClick = { detail = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                else IconButton(enabled = !model.busy && !model.offline, onClick = { model.action { repo.sync() } }) { Icon(Icons.Default.Sync, "Synchronize library") }
            }
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (tab == 3) {
                Settings(model, repo, assets)
            } else if (tab == 1 && detail == null) {
                Row {
                    TextButton(enabled = !model.busy, onClick = { model.action { repo.repair(null, true) } }) { Text("Verify & repair all") }
                }
                if (downloads.isEmpty()) Empty("Keep music offline", "Choose a song, album, artist, or playlist in your library and tap download.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(downloads, key = { it.id }) { s ->
                        val expected = members.filter { it.selectionId == s.id }.map { it.trackId }.toSet()
                        val count = expected.count { it in ready }
                        Card(Modifier.fillMaxWidth().clickable {
                            catalogue.find { it.id == s.id }?.let { item ->
                                if (item.kind == "Audio") model.play(listOf(item)) else detail = item
                            }
                        }) {
                            Column(Modifier.padding(14.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text(if (!s.retained) "$count tracks on this phone · Local copy · Not synchronized"
                                    else if (s.refreshedAt == 0L) "Discovering tracks · not complete" else "$count / ${expected.size} tracks verified · ${if (s.keepSynced) "Synchronized" else "Snapshot"}")
                                val updates = expected.count { assetMap[it]?.state == "Ready" && assetMap[it]?.transferState?.isNotBlank() == true }
                                if (updates > 0) Text("$updates audio updates pending or needing attention")
                                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (s.retained) Column {
                                    TextButton(enabled = !model.busy, onClick = { model.action { repo.repair(s.id, true) } }) { Text("Repair") }
                                    TextButton(enabled = !model.busy && !model.offline, onClick = { model.action { repo.refreshSelection(s); repo.repair(s.id, false) } }) { Text("Refresh tracks") }
                                    TextButton(onClick = { model.action { repo.release(s.id) }; model.message = "Automatic offline keeping stopped. Existing phone downloads remain until cleanup." }) { Text("Stop keeping offline") }
                                }
                                else TextButton(enabled = !model.busy && !model.offline, onClick = { catalogue.find { it.id == s.id }?.let { item -> model.action { repo.select(item, s.kind == "Playlist") } } }) { Text("Keep offline again") }
                                expected.filter { s.retained && !model.offline && it !in ready }.forEach { id ->
                                    Text("${catalogue.find { it.id == id }?.title ?: id}: ${assetMap[id]?.error ?: assetMap[id]?.state ?: "Queued"}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                if (detail == null) {
                    OutlinedTextField(query, { query = it }, label = { Text("Search your library") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (tab == 0) Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MusicAlbum" to "Albums", "MusicArtist" to "Artists", "Audio" to "Songs").forEach { (kind, label) ->
                            FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(label) })
                        }
                    } else TextButton(enabled = !model.offline, onClick = { newPlaylist = true }) { Text("Create playlist") }
                    val list = catalogue.filter { it.kind == if (tab == 2) "Playlist" else filter }
                        .filter { !model.offline || it.id in available }
                        .filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
                    if (list.isEmpty()) Empty(if (model.offline) "No downloaded music here" else "Nothing here yet",
                        if (model.offline) "Download music while online to make it available here." else "Synchronize your Jellyfin library using the refresh button.")
                    LazyColumn {
                        items(list, key = { it.id }) { item ->
                            MusicRow(item, assetMap[item.id], downloadStates[item.id] ?: DownloadState.NONE, { if (item.kind == "Audio") {
                                if (model.offline && item.id !in ready) model.message = "This track is not downloaded"
                                else {
                                    val playable = list.filter { it.kind == "Audio" && (!model.offline || it.id in ready) }
                                    model.play(playable, playable.indexOf(item).coerceAtLeast(0))
                                }
                            } else detail = item },
                                { selection = item })
                        }
                    }
                } else {
                    val item = detail!!
                    val tracks = when (item.kind) {
                        "Playlist" -> projectedEntries(entries.filter { it.playlistId == item.id }, edits.find { it.playlistId == item.id && it.status != "Done" })
                            .mapNotNull { e -> catalogue.find { it.id == e.trackId } }
                        "MusicAlbum" -> catalogue.filter { it.kind == "Audio" && it.albumId == item.id }.sortedWith(compareBy({ it.disc }, { it.number }))
                        else -> catalogue.filter { it.kind == "Audio" && item.id in it.albumArtistIds.split(',') }.sortedBy { it.album }
                    }.let { current ->
                        val stopped = selections.any { it.id == item.id && !it.retained }
                        if (model.offline || stopped) {
                            val localMembers = members.filter { it.selectionId == item.id && it.trackId in ready }.map { it.trackId }.toSet()
                            current + catalogue.filter { it.kind == "Audio" && it.id in localMembers && current.none { track -> track.id == it.id } }
                        } else current
                    }.filter { !model.offline || it.id in ready }
                    val multiDisc = item.kind == "MusicAlbum" && catalogue.filter { it.kind == "Audio" && it.albumId == item.id }
                        .map { it.disc.coerceAtLeast(1) }.distinct().size > 1
                    LazyColumn {
                        item {
                            Artwork(model, item, Modifier.fillMaxWidth().heightIn(max = 280.dp).aspectRatio(1f))
                            Text(item.artist, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                            Row {
                                Button(enabled = tracks.isNotEmpty(), onClick = { model.play(tracks) }) { Text("Play") }
                                TextButton(onClick = { selection = item }) { Text((downloadStates[item.id] ?: DownloadState.NONE).label) }
                            }
                            if (item.kind == "Playlist") PlaylistTools(model, item, catalogue, entries.filter { it.playlistId == item.id }, edits.filter { it.playlistId == item.id && it.status != "Done" })
                        }
                        itemsIndexed(tracks) { index, track ->
                            if (multiDisc &&
                                (index == 0 || tracks[index - 1].disc.coerceAtLeast(1) != track.disc.coerceAtLeast(1))) {
                                Text("Disc ${track.disc.coerceAtLeast(1)}", Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                            }
                            MusicRow(track, assetMap[track.id], downloadStates[track.id] ?: DownloadState.NONE,
                            { if (!model.offline || track.id in ready) model.play(tracks.filter { !model.offline || it.id in ready },
                                tracks.take(index).count { !model.offline || it.id in ready }) else model.message = "This track is not downloaded" }, { selection = track }) }
                    }
                }
            }
        }
    }
    selection?.let { item ->
        var synced by remember(item.id) { mutableStateOf(item.kind == "Playlist") }
        val existing = selections.find { it.id == item.id && it.retained }
        AlertDialog(onDismissRequest = { selection = null }, title = { Text("Offline download: ${item.title}") },
            text = { Column { Text("Original audio files will be stored on this phone.")
                Text((downloadStates[item.id] ?: DownloadState.NONE).label)
                if (existing != null) Text("Verify and repair checks your phone's copies and retries missing or damaged tracks. Use Downloads to stop keeping this item offline.")
                if (existing == null && item.kind != "Audio") Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(synced, { synced = it }); Text("Keep collection contents synchronized")
                }
                if (item.kind == "MusicArtist") Text("Includes music where this is the album artist.")
            } }, confirmButton = { TextButton(enabled = !model.offline && !model.busy, onClick = { model.action {
                if (existing != null) { repo.refreshSelection(existing); repo.repair(item.id, true) }
                else repo.select(item, synced)
            }; selection = null }) { Text(if (existing != null) "Verify & repair" else "Download") } },
            dismissButton = { TextButton(onClick = { selection = null }) { Text("Cancel") } })
    }
    if (newPlaylist) TextDialog("Create playlist", "", { newPlaylist = false }) { name -> model.action { repo.createPlaylist(name) }; newPlaylist = false }
    if (lyrics) NowPlaying(model, catalogue.find { it.id == model.playingId }, { lyrics = false }, { lyrics = false; showQueue = true })
    if (showQueue) PlayingQueue(model, catalogue) { showQueue = false }
}

@Composable private fun Login(model: MusicModel) {
    var url by remember { mutableStateOf("") }; var user by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF5F3FA)) {
            Image(painterResource(R.drawable.jellude_logo), stringResource(R.string.app_name),
                Modifier.size(156.dp).padding(8.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.height(20.dp)); Text("Your music.\nEverywhere.", style = MaterialTheme.typography.displaySmall)
        Text("Connect Jellyfin once. Keep your music ready offline.", Modifier.padding(vertical = 16.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, placeholder = { Text("https://music.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (url.startsWith("http://")) Text("HTTP sends credentials without encryption. Use only on a trusted network.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { model.login(url, user, password); password = "" }, enabled = !model.busy && url.isNotBlank() && user.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(if (model.busy) "Connecting…" else "Connect") }
        model.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun MusicRow(item: Item, asset: Asset?, downloadState: DownloadState, onClick: () -> Unit, download: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JelludeApp
    val repo = app.repository
    val local = repo?.artwork(item)?.takeIf { it.exists() }
    val cover = if (local != null) local else if (!app.store.offline && repo != null) ImageRequest.Builder(context)
        .data(repo.api.url("Items/${item.albumId.ifBlank { item.id }}/Images/Primary", mapOf("MaxWidth" to "150", "Tag" to item.imageTag)))
        .addHeader("Authorization", JellyfinApi.auth(repo.api.session.token)).build() else null
    ListItem(modifier = Modifier.clickable(onClick = onClick), headlineContent = { Text(item.title, maxLines = 2) },
        supportingContent = { Text(listOf(item.artist, if (item.kind == "Audio") asset?.state ?: "Stream available" else item.kind.removePrefix("Music")).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2) },
        leadingContent = { Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.MusicNote), fallback = rememberVectorPainter(Icons.Default.MusicNote))
        } }, trailingContent = { IconButton(onClick = download) {
            Icon(when (downloadState) {
                DownloadState.COMPLETE -> Icons.Default.DownloadDone
                DownloadState.PARTIAL -> Icons.Default.Downloading
                DownloadState.NONE -> Icons.Default.Download
            }, "${downloadState.label}: ${item.title}", tint = when (downloadState) {
                DownloadState.COMPLETE -> MaterialTheme.colorScheme.primary
                DownloadState.PARTIAL -> MaterialTheme.colorScheme.tertiary
                DownloadState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            })
        } })
}

@Composable private fun Empty(title: String, description: String) {
    Column(Modifier.padding(vertical = 28.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(description, Modifier.padding(top = 8.dp)) }
}

@Composable private fun Settings(model: MusicModel, repo: MusicRepository, assets: List<Asset>) {
    var wifiOnly by remember { mutableStateOf(model.app.store.wifiOnly) }
    var signIn by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListItem(headlineContent = { Text("Offline mode") }, supportingContent = { Text("Blocks streaming and server requests") },
            trailingContent = { Switch(model.offline, model::toggleOffline) })
        ListItem(headlineContent = { Text("Download on unmetered networks only") }, supportingContent = { Text("Usually Wi-Fi; streaming is allowed while online") },
            trailingContent = { Switch(wifiOnly, { value -> wifiOnly = value; model.app.store.wifiOnly = value; model.action { repo.rescheduleDownloads() } }) })
        Text(repo.session.url, style = MaterialTheme.typography.bodyMedium)
        TextButton(enabled = !model.offline && !model.busy, onClick = { signIn = true }) { Text("Sign in again") }
        Text("${assets.filter { it.state == "Ready" }.sumOf { it.bytes } / (1024 * 1024)} MB downloaded")
        Button(enabled = !model.busy && !model.offline, onClick = { model.action { repo.sync() } }) { Text("Sync metadata and playlists") }
        OutlinedButton(enabled = !model.busy, onClick = { model.action { repo.repair(null, true) } }) { Text("Verify and repair downloads") }
        var clean by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { clean = true }) { Text("Clean up unused phone downloads") }
        Text("Downloads keeps the items you chose available offline and repairable. Stop keeping offline removes that requirement but leaves existing phone copies. Cleanup removes only copies no longer needed by any offline item. Shared tracks are kept. Uninstalling the app removes its downloads.", style = MaterialTheme.typography.bodySmall)
        Text("Cleanup affects this phone only. Jellude cannot delete music files from your Jellyfin server.", style = MaterialTheme.typography.bodySmall)
        Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} · Background downloads may wait for Android scheduling. Open the app and use Repair to continue interrupted work.", style = MaterialTheme.typography.bodySmall)
        if (clean) AlertDialog(onDismissRequest = { clean = false }, title = { Text("Clean up phone downloads?") }, text = { Text("Remove downloaded audio from this phone only when no offline item needs it. Tracks still needed by any downloaded song, album, artist, or playlist are kept. Nothing is deleted from Jellyfin.") },
            confirmButton = { TextButton(onClick = { model.action { repo.cleanUnreferenced() }; clean = false }) { Text("Clean up phone") } },
            dismissButton = { TextButton(onClick = { clean = false }) { Text("Cancel") } })
        if (signIn) {
            var user by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { signIn = false }, title = { Text("Sign in to the same account") },
                text = { Column {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                } }, confirmButton = { TextButton(enabled = user.isNotBlank(), onClick = { model.login(repo.session.url, user, password); signIn = false }) { Text("Sign in") } },
                dismissButton = { TextButton(onClick = { signIn = false }) { Text("Cancel") } })
        }
    }
}

@Composable private fun TextDialog(title: String, initial: String, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { confirm(value.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

private fun projectedEntries(entries: List<PlaylistEntry>, edit: PlaylistEdit?): List<PlaylistEntry> {
    if (edit == null || edit.status != "Pending") return entries
    return when (edit.operation) {
        "Add" -> entries + PlaylistEntry(edit.playlistId, "pending", edit.value, entries.size)
        "Remove" -> entries.filter { it.entryId != edit.value }
        "Move" -> entries.toMutableList().apply {
            val (id, position) = edit.value.split(":")
            find { it.entryId == id }?.let { remove(it); add(position.toInt().coerceIn(0, size), it) }
        }
        else -> entries
    }
}

@Composable private fun PlaylistTools(model: MusicModel, playlist: Item, catalogue: List<Item>, entries: List<PlaylistEntry>, edits: List<PlaylistEdit>) {
    var dialog by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }
    Row {
        TextButton(enabled = edits.isEmpty(), onClick = { dialog = "Add" }) { Text("Add") }
        TextButton(enabled = edits.isEmpty(), onClick = { dialog = "Edit" }) { Text("Edit entries") }
        TextButton(enabled = edits.isEmpty(), onClick = { dialog = "Rename" }) { Text("Rename") }
    }
    TextButton(enabled = !model.offline && !model.busy, onClick = { model.action {
        val repo = model.repo!!
        val (fresh, tracks) = repo.api.playlist(playlist.id)
        repo.dao.replaceEntries(playlist.id, fresh, tracks)
        repo.dao.putItems(repo.api.catalogue())
    } }) { Text("Refresh from server") }
    edits.forEach { edit ->
        Text("${edit.operation}: ${edit.status}", color = MaterialTheme.colorScheme.secondary)
        edit.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row {
            if (edit.status == "Pending") TextButton(enabled = !model.offline, onClick = { model.action { model.repo!!.syncEdits() } }) { Text("Sync edit") }
            if (edit.status != "Sending" || !model.busy) TextButton(onClick = { model.action { model.repo!!.dao.discardEdit(edit.id) } }) { Text("Discard local edit") }
        }
    }
    if (dialog == "Rename") TextDialog("Rename playlist", playlist.title, { dialog = "" }) { model.edit(playlist.id, "Rename", it); dialog = "" }
    if (dialog == "Add" || dialog == "Edit") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(if (dialog == "Add") "Add a song" else "Edit playlist entries") },
        text = { Column {
            if (dialog == "Add") OutlinedTextField(search, { search = it }, label = { Text("Search songs") })
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (dialog == "Add") items(catalogue.filter { it.kind == "Audio" && (search.isBlank() || it.title.contains(search, true)) }) { track ->
                    TextButton(onClick = { model.edit(playlist.id, "Add", track.id); dialog = "" }) { Text(track.title) }
                } else itemsIndexed(entries) { index, entry ->
                    Column {
                        Text(catalogue.find { it.id == entry.trackId }?.title ?: entry.trackId)
                        Row {
                            if (index > 0) TextButton(onClick = { model.edit(playlist.id, "Move", "${entry.entryId}:${index - 1}"); dialog = "" }) { Text("Up") }
                            if (index < entries.lastIndex) TextButton(onClick = { model.edit(playlist.id, "Move", "${entry.entryId}:${index + 1}"); dialog = "" }) { Text("Down") }
                            TextButton(onClick = { model.edit(playlist.id, "Remove", entry.entryId); dialog = "" }) { Text("Remove") }
                        }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Close") } })
}

internal fun repeatLabel(mode: Int) = when (mode) {
    Player.REPEAT_MODE_ALL -> "Repeat queue"
    Player.REPEAT_MODE_ONE -> "Repeat song"
    else -> "Repeat off"
}

@Composable private fun PlayingQueue(model: MusicModel, catalogue: List<Item>, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Playing queue (${model.queue.size})") }, text = {
        Column {
            Text(if (model.shuffle) "Playback order · Shuffle on" else "Playback order")
            Text(repeatLabel(model.repeatMode), style = MaterialTheme.typography.labelMedium)
            Text("Removing tracks here changes playback only; playlists and downloads are preserved.", style = MaterialTheme.typography.bodySmall)
            if (model.queue.isEmpty()) Text("The queue is empty")
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(model.queue, key = { it.index }) { entry ->
                    val item = catalogue.find { it.id == entry.id }
                    ListItem(headlineContent = { Text(item?.title ?: entry.id) },
                        supportingContent = { Text(when (entry.index) {
                            model.queueIndex -> "Now playing"
                            model.nextIndex -> "Next"
                            else -> item?.artist.orEmpty()
                        }) },
                        modifier = Modifier.clickable { model.controller?.let { it.seekToDefaultPosition(entry.index); it.prepare(); it.play() } },
                        trailingContent = { IconButton(onClick = { model.controller?.removeMediaItem(entry.index) }) {
                            Icon(Icons.Default.Close, "Remove from queue: ${item?.title ?: entry.id}")
                        } })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
        dismissButton = { TextButton(enabled = model.queue.isNotEmpty(), onClick = { model.clearQueue() }) { Text("Clear queue") } })
}
