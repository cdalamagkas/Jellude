package dev.jellude.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import dev.jellude.MainActivity
import dev.jellude.R
import dev.jellude.JelludeApp
import dev.jellude.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import org.json.JSONArray
import org.json.JSONObject
import dev.jellude.core.ListenTrack
import dev.jellude.core.ListenTracker
import android.os.SystemClock

@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaLibraryService() {
    private lateinit var session: MediaLibrarySession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as JelludeApp
    private val repo get() = app.repository
    private val listenTracker = ListenTracker()

    private fun trackListen(player: Player, newOccurrence: Boolean = false) {
        val connection = app.listenBrainz.connection.value
        val current = player.currentMediaItem
        val metadata = current?.mediaMetadata
        val track = current?.let { ListenTrack(it.mediaId, metadata?.title?.toString().orEmpty(),
            metadata?.artist?.toString().orEmpty(), metadata?.albumTitle?.toString().orEmpty(), player.duration.coerceAtLeast(0)) }
        listenTracker.sample(connection.username.takeIf { connection.enabled }, track, player.isPlaying,
            SystemClock.elapsedRealtime(), System.currentTimeMillis() / 1000, newOccurrence).forEach(app::recordListen)
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_stat_jellude)
        })
        val http = OkHttpDataSource.Factory(app.http).setDefaultRequestProperties(
            mapOf("Authorization" to JellyfinApi.auth(repo?.api?.session?.token.orEmpty())))
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build()
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        val prefs = getSharedPreferences("playback", MODE_PRIVATE)
        val key = "queue-${repo?.session?.namespace}"
        fun saveQueue() {
            if (player.mediaItemCount == 0) { prefs.edit().remove(key).apply(); return }
            val ids = JSONArray().apply { (0 until player.mediaItemCount).forEach { put(player.getMediaItemAt(it).mediaId) } }
            prefs.edit().putString(key, JSONObject().put("ids", ids).put("index", player.currentMediaItemIndex)
                .put("position", player.currentPosition).put("shuffle", player.shuffleModeEnabled).put("repeat", player.repeatMode).toString()).apply()
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { saveQueue(); trackListen(player) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                trackListen(player, newOccurrence = true)
            }
        })
        scope.launch {
            app.listenBrainz.connection.collect { trackListen(player, newOccurrence = true) }
        }
        scope.launch { while (isActive) { delay(1_000); trackListen(player) } }
        scope.launch {
            try {
                val stored = prefs.getString(key, null)?.let(::JSONObject)
                if (stored != null && player.mediaItemCount == 0) {
                    val ids = stored.getJSONArray("ids")
                    val restored = (0 until ids.length()).mapNotNull { index ->
                        repo?.dao?.item(ids.getString(index))?.let { item ->
                            try { media(item, true) } catch (_: IllegalStateException) { null }
                        }
                    }
                    if (restored.isNotEmpty() && player.mediaItemCount == 0) {
                        val oldIndex = stored.optInt("index").coerceIn(0, ids.length() - 1)
                        val index = restored.indexOfFirst { it.mediaId == ids.getString(oldIndex) }.coerceAtLeast(0)
                        player.setMediaItems(restored, index, stored.optLong("position"))
                        player.shuffleModeEnabled = stored.optBoolean("shuffle")
                        player.repeatMode = stored.optInt("repeat").coerceIn(0, 2)
                        player.prepare()
                    }
                }
            } catch (_: Exception) { /* A previous queue must never prevent a fresh playback session. */ }
            while (isActive) { delay(5_000); saveQueue() }
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() {
        trackListen(session.player)
        session.player.release(); session.release(); scope.cancel(); super.onDestroy()
    }

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    private suspend fun media(item: Item, resolve: Boolean = false): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
            .setAlbumTitle(item.album).setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()
        return MediaItem.Builder().setMediaId(item.id).setMediaMetadata(metadata).apply {
            if (resolve) {
                val repository = requireNotNull(repo) { "Sign in on your phone" }
                val asset = repository.dao.asset(item.id)
                if (asset?.state == "Ready" && repository.file(item.id).exists() && repository.file(item.id).length() == asset.bytes)
                    setUri(Uri.fromFile(repository.file(item.id)))
                else {
                    check(!app.store.offline) { "This track is not downloaded" }
                    setUri(repository.api.url("Audio/${item.id}/stream", mapOf("Static" to "true")))
                }
            }
        }.build()
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            LibraryResult.ofItem(folder("root", getString(R.string.app_name)), params)
        }
        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val repository = repo ?: return@future LibraryResult.ofItemList(emptyList(), params)
            val all = repository.dao.items()
            val ready = repository.dao.assets().filter { it.state == "Ready" && repository.file(it.trackId).exists() &&
                repository.file(it.trackId).length() == it.bytes }.map { it.trackId }.toSet()
            val available = offlineItemIds(all, ready, repository.dao.members(),
                all.filter { it.kind == "Playlist" }.flatMap { repository.dao.entries(it.id) })
            val children = when (parentId) {
                "root" -> listOf(folder("downloaded", "Downloaded"), folder("albums", "Albums"), folder("artists", "Artists"), folder("playlists", "Playlists"), folder("songs", "Songs"))
                "downloaded" -> {
                    all.filter { it.id in ready }.map { media(it) }
                }
                "songs" -> all.filter { it.kind == "Audio" && (!app.store.offline || it.id in ready) }.map { media(it) }
                "albums", "artists", "playlists" -> {
                    val kind = when (parentId) { "albums" -> "MusicAlbum"; "artists" -> "MusicArtist"; else -> "Playlist" }
                    all.filter { it.kind == kind && (!app.store.offline || it.id in available) }.map { folder("collection:${it.id}", it.title) }
                }
                else -> {
                    val id = parentId.removePrefix("collection:")
                    val item = all.find { it.id == id }
                    val tracks = when (item?.kind) {
                        "Playlist" -> repository.dao.entries(id).mapNotNull { entry -> all.find { it.id == entry.trackId } }
                        "MusicAlbum" -> all.filter { it.albumId == id }.sortedWith(compareBy({ it.disc }, { it.number }))
                        "MusicArtist" -> all.filter { it.kind == "Audio" && id in it.albumArtistIds.split(',') }
                        else -> emptyList()
                    }
                    tracks.filter { !app.store.offline || it.id in ready }.map { media(it) }
                }
            }
            LibraryResult.ofItemList(children.drop((page.toLong() * pageSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize), params)
        }
        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val item = repo?.dao?.item(mediaId)
            if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(media(item), null)
        }
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.mapNotNull { requested ->
                val item = repo?.dao?.item(requested.mediaId) ?: requested.requestMetadata.searchQuery?.let { query ->
                    repo?.dao?.items()?.firstOrNull { it.kind == "Audio" &&
                        (it.title.contains(query, true) || it.artist.contains(query, true)) &&
                        (!app.store.offline || repo?.dao?.asset(it.id)?.state == "Ready") }
                }
                item?.let { media(it, true) }
            }.toMutableList()
        }
        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = repo?.dao?.items()?.count { it.kind == "Audio" && (it.title.contains(query, true) || it.artist.contains(query, true)) } ?: 0
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }
        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = repo?.dao?.items().orEmpty().filter { it.kind == "Audio" && (it.title.contains(query, true) || it.artist.contains(query, true)) }
            LibraryResult.ofItemList(items.drop(page * pageSize).take(pageSize).map { media(it) }, params)
        }
    }
}
