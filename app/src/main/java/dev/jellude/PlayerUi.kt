package dev.jellude

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.jellude.core.Lyric
import dev.jellude.core.Lyrics
import dev.jellude.data.Item
import dev.jellude.data.JellyfinApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun Artwork(model: MusicModel, item: Item, modifier: Modifier = Modifier) {
    val repo = model.repo ?: return
    val context = LocalContext.current
    val local = repo.artwork(item).takeIf { it.exists() }
    val cover = local ?: if (!model.offline) ImageRequest.Builder(context)
        .data(repo.api.url("Items/${item.albumId.ifBlank { item.id }}/Images/Primary", mapOf("MaxWidth" to "1000")))
        .addHeader("Authorization", JellyfinApi.auth(repo.api.session.token)).build() else null
    AsyncImage(cover, "Cover art: ${item.title}", modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Fit,
        error = rememberVectorPainter(Icons.Default.MusicNote), fallback = rememberVectorPainter(Icons.Default.MusicNote))
}

private fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun SeekBar(model: MusicModel, track: Item, showTimes: Boolean) {
    val duration = (model.duration.takeIf { it > 0 } ?: track.durationMs).coerceAtLeast(0)
    var dragging by remember(track.id) { mutableStateOf<Float?>(null) }
    val position = (dragging?.toLong() ?: model.position).coerceIn(0, duration)
    Slider(value = position.toFloat(), onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { model.controller?.seekTo(it.toLong()) }; dragging = null },
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f), enabled = duration > 0,
        modifier = Modifier.fillMaxWidth().height(32.dp))
    if (showTimes) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(timeLabel(position), style = MaterialTheme.typography.labelMedium)
        Text(timeLabel(duration) + " total", style = MaterialTheme.typography.labelMedium)
        Text("−" + timeLabel(duration - position), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayPause(model: MusicModel, large: Boolean = false) {
    FilledIconButton(onClick = { model.controller?.let { if (it.isPlaying) it.pause() else it.play() } },
        modifier = Modifier.size(if (large) 76.dp else 48.dp)) {
        Icon(if (model.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause",
            Modifier.size(if (large) 44.dp else 30.dp))
    }
}

@Composable
internal fun MiniPlayer(model: MusicModel, track: Item, expand: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            SeekBar(model, track, false)
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.semantics { contentDescription = "Expand player" }.clickable(onClick = expand)) { Artwork(model, track, Modifier.size(64.dp)) }
                Column(Modifier.weight(1f).clickable(onClick = expand).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { model.controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous") }
                PlayPause(model)
                IconButton(onClick = { model.controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next track") }
            }
        }
    }
}

@Composable
internal fun NowPlaying(model: MusicModel, track: Item?, dismiss: () -> Unit, openQueue: () -> Unit) {
    if (track == null) { LaunchedEffect(Unit) { dismiss() }; return }
    var lines by remember(track.id) { mutableStateOf<List<Lyric>>(emptyList()) }
    var showLyrics by remember { mutableStateOf(false) }
    var loaded by remember(track.id, model.offline) { mutableStateOf(false) }
    LaunchedEffect(track.id, model.offline) {
        lines = withContext(Dispatchers.IO) {
            val repo = model.repo!!
            val json = repo.dao.lyrics(track.id)?.json ?: if (!model.offline) {
                try { repo.refreshLyrics(track.id) } catch (_: Exception) { null }
            } else null
            runCatching {
                val rows = json?.let { JSONObject(it).optJSONArray("Lyrics") }
                (0 until (rows?.length() ?: 0)).map { i ->
                    val row = rows!!.getJSONObject(i)
                    Lyric(row.optString("Text"), if (row.has("Start") && !row.isNull("Start")) row.getLong("Start") / 10_000 else null)
                }.filter { it.text.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        loaded = true
    }
    val active = Lyrics.activeLine(lines, model.position)
    val lyricScroll = rememberLazyListState()
    LaunchedEffect(active, showLyrics, track.id) {
        if (showLyrics && active >= 0) lyricScroll.animateScrollToItem((active - 2).coerceAtLeast(0))
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(top = 8.dp), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val artHeight = (maxHeight * 0.48f).coerceAtMost(480.dp).coerceAtMost(maxWidth - 48.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = dismiss) { Icon(Icons.Default.KeyboardArrowDown, "Minimize player") }
                    Box(Modifier.fillMaxWidth().height(artHeight), contentAlignment = Alignment.Center) {
                        if (showLyrics && lines.isNotEmpty()) LazyColumn(state = lyricScroll, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 24.dp)) {
                            itemsIndexed(lines) { index, line ->
                                Text(line.text, Modifier.fillMaxWidth().clickable(enabled = line.startMs != null) {
                                    line.startMs?.let { model.controller?.seekTo(it) }
                                }.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge,
                                    color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal)
                            }
                        } else Artwork(model, track, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(track.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(track.artist, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    SeekBar(model, track, true)
                    val format = listOfNotNull(track.codec.takeIf { it.isNotBlank() }?.uppercase(),
                        track.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" },
                        track.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" }).joinToString(" · ")
                    if (format.isNotEmpty()) Surface(Modifier.padding(top = 12.dp), shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(format, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconToggleButton(model.shuffle, { model.toggleShuffle() }) { Icon(Icons.Default.Shuffle, if (model.shuffle) "Shuffle on" else "Shuffle off") }
                        IconButton(onClick = { model.controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                        PlayPause(model, true)
                        IconButton(onClick = { model.controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                        IconToggleButton(model.repeatMode != Player.REPEAT_MODE_OFF, { model.cycleRepeat() }) {
                            Icon(if (model.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, repeatLabel(model.repeatMode))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { showLyrics = !showLyrics }, enabled = lines.isNotEmpty()) {
                            Icon(Icons.Default.Lyrics, if (showLyrics) "Show cover art" else if (!loaded) "Loading lyrics" else if (lines.isEmpty()) "No lyrics available" else "Show lyrics",
                                tint = if (lines.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        }
                        IconButton(onClick = openQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Playing queue (${model.queue.size})") }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
