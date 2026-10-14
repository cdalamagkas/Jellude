package dev.jellude.core

import org.junit.Assert.*
import org.junit.Test

class ServerRequestPolicyTest {
    private val playlist = "a".repeat(32)
    @Test fun serverDeletionAndUnknownWritesAreBlocked() {
        for (path in listOf("Items/$playlist", "Items", "Items/Delete", "Audio/$playlist", "Playlists/$playlist", "Library/Media/Updated")) {
            assertFalse(path, ServerRequestPolicy.allows("DELETE", path.split('/')))
        }
        assertFalse(ServerRequestPolicy.allows("POST", listOf("Items", "Delete")))
        assertFalse(ServerRequestPolicy.allows("PUT", listOf("Playlists", playlist)))
        assertFalse(ServerRequestPolicy.allows("DELETE", listOf("Playlists", "..", "Items")))
    }
    @Test fun onlySupportedPlaylistWritesAndLoginAreAllowedIncludingProxyPrefix() {
        for ((method, path) in listOf(
            "POST" to "Users/AuthenticateByName", "POST" to "Playlists",
            "POST" to "Playlists/$playlist", "POST" to "Playlists/$playlist/Items",
            "DELETE" to "Playlists/$playlist/Items", "POST" to "Playlists/$playlist/Items/entry2/Move/0"
        )) {
            assertTrue(path, ServerRequestPolicy.allows(method, path.split('/')))
            assertTrue(path, ServerRequestPolicy.allows(method, ("jellyfin/" + path).split('/')))
        }
        assertTrue(ServerRequestPolicy.allows("GET", listOf("Items", playlist, "Download")))
    }
}
