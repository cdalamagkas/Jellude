package dev.jellude.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import dev.jellude.core.VerifiedTransfer
import dev.jellude.core.PlaylistPolicy
import dev.jellude.core.RemoteEntry
import dev.jellude.download.DownloadWorker
import dev.jellude.download.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import dev.jellude.JelludeApp
import kotlinx.coroutines.guava.await

class MusicRepository(val context: Context, val session: ServerSession, val database: LibraryDatabase,
    var api: JellyfinApi, val directory: File) {
    val dao = database.library()
    private val synchronization = Mutex()
    private val editMutex = Mutex()
    private val selectionMutex = Mutex()
    private val work = WorkManager.getInstance(context)
    fun file(id: String) = File(directory, "${safeId(id)}.audio")
    fun partial(id: String) = File(directory, "${safeId(id)}.part")
    fun artwork(item: Item) = File(directory, "${safeId(item.albumId.ifBlank { item.id })}.jpg")
    private fun safeId(id: String): String { require(id.matches(Regex("[a-fA-F0-9-]+"))); return id }

    suspend fun sync() = synchronization.withLock { withContext(Dispatchers.IO) {
        val items = api.catalogue()
        dao.putItems(items)
        // Individual playlist refresh failures must not replace a previous complete snapshot.
        for (playlist in items.filter { it.kind == "Playlist" }) {
            val (entries, tracks) = api.playlist(playlist.id)
            dao.replaceEntries(playlist.id, entries, tracks)
        }
        for (selection in dao.selections().filter { it.keepSynced }) refreshSelection(selection)
        // Metadata refresh has no dependency on audio re-downloads.
        for (asset in dao.assets().filter { it.state == "Ready" }) {
            try { refreshLyrics(asset.trackId) } catch (_: Exception) { /* Preserve existing lyrics. */ }
            try { cacheArtwork(asset.trackId) } catch (_: Exception) { /* Preserve existing artwork. */ }
        }
        syncEdits()
        repair(null, deep = false)
    } }

    suspend fun select(item: Item, keepSynced: Boolean) = withContext(Dispatchers.IO) {
        val selection = Selection(item.id, item.title, item.kind, keepSynced)
        dao.putSelection(selection)
        try { refreshSelection(selection); repair(item.id, false) }
        catch (e: Exception) { dao.putSelection(selection.copy(error = e.message ?: "Could not discover tracks")); throw e }
    }
    suspend fun refreshSelection(selection: Selection) = selectionMutex.withLock { withContext(Dispatchers.IO) {
        if (dao.selection(selection.id) == null) return@withContext
        try { dao.replaceMembers(selection, api.collection(selection)) }
        catch (e: Exception) {
            dao.putSelection(selection.copy(error = e.message ?: "Refresh failed")); throw e
        }
    } }
    suspend fun repair(selectionId: String?, deep: Boolean) = withContext(Dispatchers.IO) {
        val ids = dao.members().filter { selectionId == null || it.selectionId == selectionId }.map { it.trackId }.toSet()
        for (id in ids) {
            val asset = dao.asset(id)
            val source = dao.item(id)?.sourceTag.orEmpty()
            val valid = asset?.state == "Ready" && file(id).exists() && file(id).length() == asset.bytes &&
                (!deep || VerifiedTransfer.sha256(file(id)) == asset.hash)
            val changed = source.isNotEmpty() && asset?.sourceTag?.isNotEmpty() == true && source != asset.sourceTag
            if (!valid || changed) {
                // A source replacement can download while the previous verified copy remains playable.
                if (asset?.transferState != "Downloading") dao.putAsset((asset ?: Asset(id)).copy(
                    state = if (valid) "Ready" else "Queued", transferState = "Queued", error = null))
                enqueue(id)
            } else if (deep) dao.putAsset(asset!!.copy(verifiedAt = System.currentTimeMillis()))
        }
    }
    fun enqueue(id: String) {
        work.enqueueUniqueWork("audio-${session.namespace}-$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("track" to id, "namespace" to session.namespace))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(
                    if ((context as JelludeApp).store.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    suspend fun rescheduleDownloads() {
        for (id in dao.members().map { it.trackId }.toSet()) {
            work.cancelUniqueWork("audio-${session.namespace}-$id").result.await()
        }
        repair(null, false)
    }
    suspend fun release(selectionId: String) = selectionMutex.withLock { database.withTransaction {
        dao.clearMembers(selectionId); dao.deleteSelection(selectionId)
        // Keep unreferenced verified files until the user explicitly cleans them up.
    } }
    suspend fun cleanUnreferenced() = withContext(Dispatchers.IO) {
        val pinned = dao.members().map { it.trackId }.toSet()
        for (asset in dao.assets().filter { it.trackId !in pinned && it.state != "Downloading" }) {
            file(asset.trackId).delete()
            partial(asset.trackId).delete()
            dao.putAsset(asset.copy(state = "Not downloaded", bytes = 0, hash = ""))
        }
    }
    suspend fun refreshLyrics(id: String): String? = withContext(Dispatchers.IO) {
        api.lyrics(id)?.also { dao.putLyrics(StoredLyrics(id, it, System.currentTimeMillis())) }
    }
    suspend fun cacheArtwork(id: String) = withContext(Dispatchers.IO) {
        val item = dao.item(id) ?: return@withContext
        val imageId = item.albumId.ifBlank { item.id }
        val destination = artwork(item)
        val temp = File(destination.path + ".tmp")
        api.client.newCall(api.request("Items/$imageId/Images/Primary", mapOf("MaxWidth" to "600", "Format" to "jpg")).build())
            .execute().use { response ->
                if (!response.isSuccessful) return@withContext
                response.body?.byteStream()?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temp.path, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    java.nio.file.Files.move(temp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } else temp.delete()
            }
    }
    fun scheduleMaintenance() {
        work.enqueueUniquePeriodicWork("sync-${session.namespace}", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setInputData(workDataOf("namespace" to session.namespace))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build())
    }
    fun requestSync() {
        work.enqueueUniqueWork("requested-sync-${session.namespace}", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setInputData(workDataOf("namespace" to session.namespace))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    suspend fun queueEdit(playlistId: String, operation: String, value: String) = editMutex.withLock {
        require(dao.outstanding(playlistId) == 0) { "Resolve or synchronize this playlist's pending edit first" }
        val entries = dao.entries(playlistId)
        val base = JSONObject().put("title", dao.item(playlistId)?.title.orEmpty()).put("entries", entryJson(entries))
        dao.putEdit(PlaylistEdit(UUID.randomUUID().toString(), playlistId, operation, value, base.toString()))
        if ((context as JelludeApp).store.offline) requestSync()
    }
    suspend fun syncEdits() = editMutex.withLock { withContext(Dispatchers.IO) {
        for (edit in dao.pendingEdits()) {
            val remote = api.playlist(edit.playlistId)
            val title = api.item(JSONObject(api.call("Users/${session.userId}/Items/${edit.playlistId}"))).title
            val base = JSONObject(edit.baseJson)
            val baseEntries = base.getJSONArray("entries").let { rows -> (0 until rows.length()).map {
                rows.getJSONObject(it).let { row -> RemoteEntry(row.getString("entry"), row.getString("track")) }
            } }
            if (!PlaylistPolicy.canApply(baseEntries, remote.first.map { RemoteEntry(it.entryId, it.trackId) }) || title != base.getString("title")) {
                dao.putEdit(edit.copy(status = "Conflict", error = "Playlist changed on the server. Discard this edit, refresh, and apply it again."))
                dao.replaceEntries(edit.playlistId, remote.first, remote.second)
                continue
            }
            // Mark before the request: interruption can mean the server applied it. Never blindly replay.
            dao.putEdit(edit.copy(status = "Sending", error = "Outcome unknown if interrupted. Refresh before editing again."))
            try {
                when (edit.operation) {
                    "Add" -> api.call("Playlists/${edit.playlistId}/Items", mapOf("Ids" to edit.value, "UserId" to session.userId), "POST")
                    "Remove" -> api.call("Playlists/${edit.playlistId}/Items", mapOf("EntryIds" to edit.value), "DELETE")
                    "Move" -> {
                        val parts = edit.value.split(":")
                        api.call("Playlists/${edit.playlistId}/Items/${parts[0]}/Move/${parts[1]}", method = "POST")
                    }
                    "Rename" -> {
                        val info = JSONObject(api.call("Playlists/${edit.playlistId}"))
                        info.put("Name", edit.value)
                        api.call("Playlists/${edit.playlistId}", method = "POST", body = info)
                    }
                    "Delete" -> api.call("Items/${edit.playlistId}", method = "DELETE")
                    else -> error("Unknown playlist operation")
                }
                dao.putEdit(edit.copy(status = "Done"))
                if (edit.operation != "Delete") {
                    val (entries, tracks) = api.playlist(edit.playlistId)
                    dao.replaceEntries(edit.playlistId, entries, tracks)
                    if (edit.operation == "Rename") dao.item(edit.playlistId)?.let { dao.putItems(listOf(it.copy(title = edit.value))) }
                    dao.selection(edit.playlistId)?.takeIf { it.keepSynced }?.let { refreshSelection(it); repair(it.id, false) }
                } else dao.deleteItem(edit.playlistId)
            } catch (e: Exception) {
                dao.putEdit(edit.copy(status = "Needs review", error = "The server may have applied this edit. Refresh and inspect before discarding it."))
            }
        }
    } }
    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank())
        api.call("Playlists", method = "POST", body = JSONObject().put("Name", name)
            .put("UserId", session.userId).put("MediaType", "Audio").put("Ids", JSONArray()))
        dao.putItems(api.catalogue())
    }
    private fun entryJson(entries: List<PlaylistEntry>) = JSONArray().apply {
        entries.forEach { put(JSONObject().put("entry", it.entryId).put("track", it.trackId)) }
    }
}
