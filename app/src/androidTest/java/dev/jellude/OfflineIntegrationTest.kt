package dev.jellude

import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import dev.jellude.data.*
import dev.jellude.playback.MusicService
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import okhttp3.mockwebserver.*
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class OfflineIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var app: JelludeApp
    private lateinit var repo: MusicRepository
    private var scenario: ActivityScenario<MainActivity>? = null
    private val downloads = AtomicInteger()
    private val albumId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val playlistId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    private var remoteEntries = mutableListOf("entry1" to 1, "entry2" to 1, "entry3" to 2)
    private val mutations = AtomicInteger()
    private var loseMutationResponse = false
    private val tracks get() = (1..12).map { index ->
        JSONObject().put("Id", "%032x".format(index)).put("Name", "Test song $index").put("Type", "Audio")
            .put("AlbumId", albumId).put("Album", "Test album").put("Artists", JSONArray().put("Test artist"))
            .put("IndexNumber", index).put("RunTimeTicks", 20_000_000)
    }
    @Before fun setup() = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/Playlists/$playlistId/Items" && request.method == "GET" -> {
                        val rows = JSONArray()
                        remoteEntries.forEach { (entry, track) -> rows.put(JSONObject(tracks[track - 1].toString()).put("PlaylistItemId", entry)) }
                        MockResponse().setBody(JSONObject().put("Items", rows).put("TotalRecordCount", rows.length()).toString())
                    }
                    path == "/Playlists/$playlistId/Items" && request.method == "DELETE" -> {
                        mutations.incrementAndGet()
                        remoteEntries.removeAll { it.first == request.requestUrl!!.queryParameter("EntryIds") }
                        MockResponse().setResponseCode(204)
                    }
                    path == "/Playlists/$playlistId/Items" && request.method == "POST" -> {
                        mutations.incrementAndGet()
                        remoteEntries.add("entry-new" to 3)
                        if (loseMutationResponse) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                        else MockResponse().setResponseCode(204)
                    }
                    path == "/Users/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/Items/$playlistId" ->
                        MockResponse().setBody(JSONObject().put("Id", playlistId).put("Name", "Test playlist").put("Type", "Playlist").toString())
                    path.endsWith("/Download") -> {
                        downloads.incrementAndGet()
                        MockResponse().setHeader("Content-Type", "audio/wav").setHeader("ETag", "\"test-audio-v1\"")
                            .setBody(Buffer().write(wav()))
                    }
                    path.endsWith("/Lyrics") -> MockResponse().setBody(JSONObject().put("Lyrics", JSONArray()
                        .put(JSONObject().put("Text", "First line").put("Start", 0))
                        .put(JSONObject().put("Text", "Second line").put("Start", 10_000_000))).toString())
                    path.endsWith("/Items") -> {
                        val rows = JSONArray()
                        if (request.requestUrl!!.queryParameter("ParentId") == null) {
                            rows.put(JSONObject().put("Id", albumId).put("Name", "Test album").put("Type", "MusicAlbum"))
                            rows.put(JSONObject().put("Id", playlistId).put("Name", "Test playlist").put("Type", "Playlist"))
                        }
                        tracks.forEach(rows::put)
                        MockResponse().setBody(JSONObject().put("Items", rows).put("TotalRecordCount", rows.length()).toString())
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        app = ApplicationProvider.getApplicationContext()
        app.store.offline = false
        app.store.wifiOnly = false
        app.openSession(ServerSession(server.url("/").toString(), "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "test-token", "integration-server"))
        repo = app.repository!!
        // Each test starts with no selections or verified assets.
        repo.database.clearAllTables()
        repo.directory.listFiles()?.forEach { it.delete() }
        repo.api = JellyfinApi(app.http, ServerSession(server.url("/").toString(), "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "test-token", "integration-server"))
        repo.sync()
        if (android.os.Build.VERSION.SDK_INT >= 33) androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("pm grant dev.jellude android.permission.POST_NOTIFICATIONS").close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun teardown() = runBlocking {
        app.store.offline = false
        scenario?.close()
        withContext(Dispatchers.Main) { app.stopService(android.content.Intent(app, MusicService::class.java)) }
        server.shutdown()
    }
    @Test fun albumDownloadsCompletelyAndRepairReplacesCorruptTrack() = runBlocking<Unit> {
        assertEquals("dev.jellude", app.packageName)
        assertEquals("Jellude", app.applicationInfo.loadLabel(app.packageManager).toString())
        compose.waitUntil(timeoutMillis = 15_000) {
            try { compose.onAllNodesWithText("Your music").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
        compose.onNodeWithText("Your music").assertIsDisplayed()
        compose.onNodeWithText("Test album").assertIsDisplayed()
        repo.select(repo.dao.item(albumId)!!, false)
        awaitComplete()
        assertEquals(12, repo.dao.assets().count { it.state == "Ready" })
        val id = tracks.first().getString("Id")
        assertNotNull(repo.dao.lyrics(id))
        val before = downloads.get()
        repo.file(id).writeBytes(ByteArray(repo.file(id).length().toInt()))
        repo.repair(albumId, true)
        awaitComplete()
        assertEquals(before + 1, downloads.get())
        assertTrue(repo.file(id).readBytes().take(4).toByteArray().contentEquals("RIFF".toByteArray()))
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("12 / 12 tracks verified", substring = true).assertIsDisplayed()
    }
    @Test fun downloadedTrackPlaysOfflineAndAutoBrowserExposesIt() = runBlocking {
        repo.select(repo.dao.item(albumId)!!, false)
        awaitComplete()
        app.store.offline = true
        val before = downloads.get()
        val browser = withContext(Dispatchers.Main) {
            MediaBrowser.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync().await()
        }
        try {
            val root = withContext(Dispatchers.Main) { browser.getLibraryRoot(null).await() }
            assertEquals("root", root.value!!.mediaId)
            assertEquals("Jellude", root.value!!.mediaMetadata.title.toString())
            val downloaded = withContext(Dispatchers.Main) { browser.getChildren("downloaded", 0, 100, null).await() }
            assertEquals(12, downloaded.value!!.size)
            withContext(Dispatchers.Main) {
                browser.setMediaItem(MediaItem.Builder().setMediaId(tracks.first().getString("Id")).build())
                browser.prepare(); browser.play()
            }
            withTimeout(20_000) {
                while (!withContext(Dispatchers.Main) { browser.playbackState == Player.STATE_READY && browser.isPlaying }) delay(200)
            }
            assertEquals(before, downloads.get())
        } finally { withContext(Dispatchers.Main) { browser.release() } }
    }
    @Test fun offlinePlaylistEditPreservesDuplicateAndConflictingEditIsBlocked() = runBlocking {
        app.store.offline = true
        repo.queueEdit(playlistId, "Remove", "entry2")
        assertEquals(0, mutations.get())
        assertEquals("Pending", repo.dao.pendingEdits().single().status)
        app.store.offline = false
        repo.syncEdits()
        assertEquals(listOf("entry1", "entry3"), repo.dao.entries(playlistId).map { it.entryId })
        assertEquals(1, mutations.get())
        repo.queueEdit(playlistId, "Move", "entry1:1")
        remoteEntries.reverse()
        repo.syncEdits()
        assertEquals(1, mutations.get())
        assertEquals(1, repo.dao.outstanding(playlistId))
        assertTrue(repo.dao.pendingEdits().isEmpty())
    }
    @Test fun lostPlaylistMutationResponseIsNeverAutomaticallyReplayed() = runBlocking {
        loseMutationResponse = true
        repo.queueEdit(playlistId, "Add", tracks[2].getString("Id"))
        repo.syncEdits()
        assertEquals(1, mutations.get())
        assertEquals(1, repo.dao.outstanding(playlistId))
        repo.syncEdits()
        assertEquals(1, mutations.get())
        assertTrue(repo.dao.pendingEdits().isEmpty())
    }
    private suspend fun awaitComplete() = withTimeout(90_000) {
        while (repo.dao.assets().count { it.state == "Ready" } != 12) delay(300)
    }
    private fun wav(): ByteArray {
        val size = 16000 * 2 * 2
        return ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(size)
            repeat(size / 2) { putShort((kotlin.math.sin(it * 0.12) * 2000).toInt().toShort()) }
        }.array()
    }
}
