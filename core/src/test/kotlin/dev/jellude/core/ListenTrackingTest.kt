package dev.jellude.core

import org.junit.Assert.*
import org.junit.Test

class ListenTrackingTest {
    private val track = ListenTrack("id", "Always on My Mind - Piano Cover", "Cover performer", "", 200_000)
    @Test fun pausesAndBufferingDoNotCountAndMetadataIsPreserved() {
        val tracker = ListenTracker()
        tracker.sample("user", track, true, 0, 100)
        assertTrue(tracker.sample("user", track, false, 40_000, 140).isEmpty())
        assertTrue(tracker.sample("user", track, true, 400_000, 500).isEmpty())
        val listen = tracker.sample("user", track, true, 460_000, 560).single()
        assertEquals(track, listen.track)
        assertEquals(100L, listen.startedAt)
        assertTrue(tracker.sample("user", track, true, 600_000, 700).isEmpty())
    }
    @Test fun skipsDoNotCompleteATrackButTransitionPreservesQualifiedPreviousListen() {
        val tracker = ListenTracker()
        tracker.sample("user", track, true, 0, 100)
        val next = track.copy(id = "next")
        assertTrue(tracker.sample("user", next, true, 10_000, 110, true).isEmpty())
        val result = tracker.sample("user", null, false, 110_000, 210)
        assertEquals("next", result.single().track.id)
        assertEquals(110L, result.single().startedAt)
    }
    @Test fun repeatCreatesAnotherListenWithANewOccurrenceAndTimestamp() {
        val tracker = ListenTracker()
        tracker.sample("user", track, true, 0, 100)
        val first = tracker.sample("user", track, true, 100_000, 200).single()
        tracker.sample("user", track, true, 200_000, 300, true)
        val second = tracker.sample("user", track, true, 300_000, 400).single()
        assertNotEquals(first.id, second.id)
        assertEquals(300L, second.startedAt)
    }
    @Test fun disabledAndChangedAccountsDiscardPartialProgress() {
        val tracker = ListenTracker()
        tracker.sample("user", track, true, 0, 100)
        assertTrue(tracker.sample(null, track, true, 100_000, 200).isEmpty())
        tracker.sample("user", track, true, 200_000, 300)
        assertTrue(tracker.sample("other", track, true, 300_000, 400).isEmpty())
        assertEquals("other", tracker.sample("other", track, true, 400_000, 500).single().account)
    }
    @Test fun longTracksAndUnknownDurationsUseFourMinutesOfActualPlayback() {
        for (duration in listOf(900_000L, 0L)) {
            val tracker = ListenTracker()
            val current = track.copy(durationMs = duration)
            tracker.sample("user", current, true, 0, 100)
            assertTrue(tracker.sample("user", current, true, 239_999, 339).isEmpty())
            assertEquals(1, tracker.sample("user", current, true, 240_000, 340).size)
        }
    }
    @Test fun preparingBeforePlaybackDoesNotSetTimestampOrAccumulateTime() {
        val tracker = ListenTracker()
        tracker.sample("user", track, false, 0, 100)
        tracker.sample("user", track, true, 100_000, 200)
        assertEquals(200L, tracker.sample("user", track, false, 200_000, 300).single().startedAt)
    }
}
