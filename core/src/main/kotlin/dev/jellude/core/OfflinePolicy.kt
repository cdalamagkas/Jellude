package dev.jellude.core

/** Membership is a set for downloads; playlist ordering/duplicates belong to playlist entries. */
object OfflinePolicy {
    fun missing(expected: Collection<String>, verified: Set<String>): Set<String> = expected.toSet() - verified
    fun removable(removedSelection: Set<String>, remainingSelections: Collection<Set<String>>): Set<String> =
        removedSelection - remainingSelections.flatten().toSet()
}

data class RemoteEntry(val entryId: String, val trackId: String)

/** No blind replay: edits are based on the exact remote entry order, including duplicates. */
object PlaylistPolicy {
    fun canApply(base: List<RemoteEntry>, remote: List<RemoteEntry>): Boolean = base == remote
}

data class Lyric(val text: String, val startMs: Long?)
object Lyrics {
    fun activeLine(lines: List<Lyric>, positionMs: Long): Int =
        lines.indexOfLast { it.startMs != null && it.startMs <= positionMs }
}
