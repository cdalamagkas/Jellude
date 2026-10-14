package dev.jellude.core

/** Only playlist entry removal may use DELETE. Generic server item/file deletion is forbidden.
 * Path segments may include a reverse-proxy prefix; mutation routes must match an exact suffix.
 */
object ServerRequestPolicy {
    private val id = Regex("[a-fA-F0-9-]+")
    fun allows(method: String, segments: List<String>): Boolean {
        if (method == "GET" || method == "HEAD") return true
        val path = segments.filter { it.isNotEmpty() }
        fun matches(vararg suffix: String) = path.takeLast(suffix.size) == suffix.toList()
        val playlistId = path.getOrNull(path.size - 2)
        if (method == "DELETE") return playlistId?.matches(id) == true && matches("Playlists", playlistId, "Items")
        if (method != "POST") return false
        if (matches("Users", "AuthenticateByName") || matches("Playlists")) return true
        if (path.lastOrNull()?.matches(id) == true && path.getOrNull(path.size - 2) == "Playlists") return true
        if (playlistId?.matches(id) == true && matches("Playlists", playlistId, "Items")) return true
        val move = path.takeLast(6)
        return move.size == 6 && move[0] == "Playlists" && move[1].matches(id) && move[2] == "Items" &&
            move[3].isNotBlank() && move[4] == "Move" && move[5].toIntOrNull()?.let { it >= 0 } == true
    }
}
