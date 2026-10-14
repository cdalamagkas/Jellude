package dev.jellude.core

import org.junit.Assert.*
import org.junit.Test

class OfflinePolicyTest {
    @Test fun incompleteAlbumRetainsEveryMissingTrack() {
        val album = (1..12).map { "track-$it" }
        assertEquals(setOf("track-7", "track-12"), OfflinePolicy.missing(album, album.toSet() - setOf("track-7", "track-12")))
    }
    @Test fun overlappingSelectionsProtectSharedTracks() {
        assertEquals(setOf("a"), OfflinePolicy.removable(setOf("a", "b"), listOf(setOf("b", "c"))))
    }
    @Test fun repeatedPlaylistEntriesRequireOnlyOneAudioFile() {
        assertEquals(setOf("b"), OfflinePolicy.missing(listOf("a", "a", "b"), setOf("a")))
    }
    @Test fun duplicatePlaylistEntriesRemainDistinctForConflicts() {
        val base = listOf(RemoteEntry("entry-1", "song"), RemoteEntry("entry-2", "song"))
        assertFalse(PlaylistPolicy.canApply(base, base.reversed()))
        assertFalse(PlaylistPolicy.canApply(base, base.take(1)))
        assertTrue(PlaylistPolicy.canApply(base, base.toList()))
    }
    @Test fun synchronizedLyricsFollowSeekBackwards() {
        val lines = listOf(Lyric("one", 1000), Lyric("two", 3000), Lyric("plain", null))
        assertEquals(-1, Lyrics.activeLine(lines, 500))
        assertEquals(1, Lyrics.activeLine(lines, 3500))
        assertEquals(0, Lyrics.activeLine(lines, 1500))
    }
}
