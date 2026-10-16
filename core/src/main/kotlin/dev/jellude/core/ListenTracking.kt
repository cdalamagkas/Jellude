package dev.jellude.core

import java.util.UUID

data class ListenTrack(val id: String, val title: String, val artist: String, val album: String, val durationMs: Long)
data class QualifiedListen(val id: String, val account: String, val track: ListenTrack, val startedAt: Long)

/** Counts elapsed playing time, never the seek position. One submission per playback occurrence. */
class ListenTracker {
    private var account: String? = null
    private var track: ListenTrack? = null
    private var occurrence = ""
    private var startedAt = 0L
    private var elapsed = 0L
    private var lastTick = 0L
    private var wasPlaying = false
    private var submitted = false

    fun sample(account: String?, track: ListenTrack?, playing: Boolean, monotonicMs: Long,
        unixSeconds: Long, newOccurrence: Boolean = false): List<QualifiedListen> {
        val result = mutableListOf<QualifiedListen>()
        // A disconnected/disabled account immediately stops tracking, including partial listens.
        if (this.account == account && account != null && wasPlaying) {
            elapsed += (monotonicMs - lastTick).coerceAtLeast(0)
            qualify()?.let(result::add)
        }
        if (this.account != account || this.track?.id != track?.id || newOccurrence) {
            this.account = account
            this.track = track
            occurrence = UUID.randomUUID().toString()
            startedAt = 0
            elapsed = 0
            submitted = false
        }
        // Fill in a duration that became known after playback preparation.
        if (track != null && this.track?.durationMs == 0L && track.durationMs > 0) this.track = track
        if (account != null && track != null && playing && startedAt == 0L) startedAt = unixSeconds
        lastTick = monotonicMs
        wasPlaying = account != null && track != null && playing
        return result
    }

    private fun qualify(): QualifiedListen? {
        val current = track ?: return null
        val threshold = if (current.durationMs > 0) (current.durationMs / 2).coerceIn(1, 240_000) else 240_000
        if (submitted || startedAt == 0L || elapsed < threshold) return null
        submitted = true
        return QualifiedListen(occurrence, requireNotNull(account), current, startedAt)
    }
}
