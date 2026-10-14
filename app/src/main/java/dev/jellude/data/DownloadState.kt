package dev.jellude.data

enum class DownloadState(val label: String) {
    NONE("Download for offline use"),
    PARTIAL("Partially downloaded or pending"),
    COMPLETE("Downloaded")
}

/** A failed discovery must never appear complete, even if its previous snapshot is ready. */
fun downloadStates(items: List<Item>, assets: List<Asset>, selections: List<Selection>,
    members: List<Member>, entries: List<PlaylistEntry>): Map<String, DownloadState> {
    val assetMap = assets.associateBy { it.trackId }
    val selectionMap = selections.associateBy { it.id }
    val memberMap = members.groupBy { it.selectionId }
    val albums = items.filter { it.kind == "Audio" }.groupBy { it.albumId }
    val artists = mutableMapOf<String, MutableSet<String>>()
    items.filter { it.kind == "Audio" }.forEach { track ->
        track.albumArtistIds.split(',').filter { it.isNotBlank() }.forEach { artists.getOrPut(it) { mutableSetOf() }.add(track.id) }
    }
    val playlists = entries.groupBy { it.playlistId }
    return items.associate { item ->
        val selection = selectionMap[item.id]
        val known = when (item.kind) {
            "Audio" -> setOf(item.id)
            "MusicAlbum" -> albums[item.id].orEmpty().map { it.id }.toSet()
            "MusicArtist" -> artists[item.id].orEmpty()
            "Playlist" -> playlists[item.id].orEmpty().map { it.trackId }.toSet()
            else -> emptySet()
        }
        val expected = known + memberMap[item.id].orEmpty().map { it.trackId }
        val complete = expected.all { assetMap[it]?.let { asset -> asset.state == "Ready" && asset.transferState.isBlank() } == true }
        val state = when {
            selection != null && (selection.refreshedAt == 0L || selection.error != null) -> DownloadState.PARTIAL
            complete && (expected.isNotEmpty() || selection != null) -> DownloadState.COMPLETE
            selection != null || expected.any { assetMap[it]?.state != null && assetMap[it]?.state != "Not downloaded" } -> DownloadState.PARTIAL
            else -> DownloadState.NONE
        }
        item.id to state
    }
}
