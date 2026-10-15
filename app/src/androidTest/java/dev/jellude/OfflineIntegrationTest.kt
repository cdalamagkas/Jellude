package dev.jellude

import android.content.ComponentName
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
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
import okhttp3.RequestBody.Companion.toRequestBody
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
            .put("ParentIndexNumber", if (index <= 3) 1 else 2)
            .put("MediaStreams", JSONArray().put(JSONObject().put("Type", "Audio").put("Codec", "pcm_s16le")
                .put("BitRate", 128000).put("SampleRate", 8000)))
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
                            rows.put(JSONObject().put("Id", albumId).put("Name", "Test album").put("Type", "MusicAlbum")
                                .put("Artists", JSONArray().put("Test artist")))
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
            try { compose.onAllNodesWithText("Library").fetchSemanticsNodes().size == 2 }
            catch (_: IllegalStateException) { false }
        }
        compose.onAllNodesWithText("Library").assertCountEquals(2)
        compose.onNodeWithText("Test album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download for offline use: Test album").assertIsDisplayed()
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
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Downloaded: Test album").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("12 / 12 tracks verified", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop keeping offline").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Local copy", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Keep offline again").assertIsDisplayed()
        assertEquals(12, repo.dao.assets().count { it.state == "Ready" })
        repo.repair(null, true)
        assertEquals(12, repo.dao.assets().count { it.state == "Ready" })
    }
    @Test fun compactLayoutsKeepArtworkAndActionsSeparate() = runBlocking {
        repo.select(repo.dao.item(albumId)!!, false)
        awaitComplete()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Downloaded: Test album").fetchSemanticsNodes().size == 1 }
        compose.onAllNodesWithText("tracks ready", substring = true).assertCountEquals(0)
        screenshot("compact-library")
        compose.onNodeWithText("Test album").performClick()
        val artwork = compose.onNodeWithContentDescription("Cover art: Test album").fetchSemanticsNode().boundsInRoot
        val artist = compose.onAllNodesWithText("Test artist", substring = false).onFirst().fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithContentDescription("Play Test album").fetchSemanticsNode().boundsInRoot
        assertEquals(artwork.width, artwork.height, 1f)
        assertTrue("Artist must be below the artwork", artist.top > artwork.bottom)
        assertTrue("Playback controls must be below the artist", play.top > artist.bottom)
        compose.onAllNodesWithText("Ready", substring = true).assertCountEquals(0)
        screenshot("compact-album")
        pressSystemBack()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("12 tracks ready offline").assertIsDisplayed()
        val repair = compose.onNodeWithContentDescription("Repair").fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithContentDescription("Refresh tracks").fetchSemanticsNode().boundsInRoot
        val release = compose.onNodeWithContentDescription("Stop keeping offline").fetchSemanticsNode().boundsInRoot
        assertEquals(repair.top, refresh.top, 1f)
        assertEquals(repair.top, release.top, 1f)
        assertTrue(repair.right <= refresh.left && refresh.right <= release.left)
        screenshot("compact-downloads")
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
    @Test fun partialDownloadIndicatorsAndBackNavigateInsideTheApp() = runBlocking {
        val first = tracks.first().getString("Id")
        repo.dao.putAsset(Asset(first, "Ready"))
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Partially downloaded or pending: Test album").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Test album").performClick()
        compose.onNodeWithContentDescription("Play Test album").assertIsDisplayed()
        pressSystemBack()
        compose.onAllNodesWithText("Library").assertCountEquals(2)
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithContentDescription("Partially downloaded or pending: Test playlist").assertIsDisplayed()
        compose.onNodeWithText("Test playlist").performClick()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play Test playlist").assertIsDisplayed()
        pressSystemBack()
        compose.onAllNodesWithText("Playlists").assertCountEquals(2)
        // Failure to discover a full collection remains incomplete even if all known tracks are ready.
        repo.dao.putAssetsForTest(tracks.map { Asset(it.getString("Id"), "Ready") })
        repo.dao.putSelection(Selection(albumId, "Test album", "MusicAlbum", false, error = "Discovery failed"))
        val state = downloadStates(repo.dao.items(), repo.dao.assets(), repo.dao.selections(), repo.dao.members(), repo.dao.entries(playlistId))
        assertEquals(DownloadState.PARTIAL, state[albumId])
        assertEquals(DownloadState.COMPLETE, state[playlistId])
    }
    @Test fun queueRepeatAndShuffleControlsReflectThePlayerAndClearPersistently() = runBlocking {
        repo.select(repo.dao.item(albumId)!!, false)
        awaitComplete()
        lateinit var model: MusicModel
        scenario!!.onActivity { model = ViewModelProvider(it)[MusicModel::class.java] }
        compose.waitUntil(15_000) { model.controller != null }
        val songs = repo.dao.items().filter { it.kind == "Audio" }.sortedBy { it.number }
        compose.runOnIdle { model.play(songs); model.controller!!.pause() }
        compose.waitUntil(15_000) { model.queue.size == 12 }
        compose.onNodeWithContentDescription("Expand player").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Show lyrics").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Show lyrics").performScrollTo().performClick()
        compose.onNodeWithText("First line").assertExists()
        compose.onNodeWithContentDescription("Show cover art").performClick()
        compose.onNodeWithContentDescription("Repeat off").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Repeat queue").assertIsOn()
        compose.runOnIdle { assertEquals(Player.REPEAT_MODE_ALL, model.controller!!.repeatMode) }
        compose.onNodeWithContentDescription("Repeat queue").performClick()
        compose.onNodeWithContentDescription("Repeat song").assertIsOn()
        screenshot("now-playing")
        compose.runOnIdle { assertEquals(Player.REPEAT_MODE_ONE, model.controller!!.repeatMode) }
        compose.onNodeWithContentDescription("Repeat song").performClick()
        compose.onNodeWithContentDescription("Repeat off").assertIsOff()
        compose.onNodeWithContentDescription("Shuffle off").performClick()
        compose.onNodeWithContentDescription("Shuffle on").assertIsOn()
        compose.onNodeWithContentDescription("Shuffle on").performClick()
        compose.onNodeWithContentDescription("Playing queue (12)").performScrollTo().performClick()
        compose.onNodeWithText("Now playing", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Next", substring = false).assertIsDisplayed()
        screenshot("playing-queue")
        compose.onNodeWithContentDescription("Remove from queue: Test song 2").performClick()
        compose.waitUntil(10_000) { model.queue.size == 11 }
        compose.runOnIdle { assertFalse(model.queue.any { it.id == songs[1].id }) }
        compose.onNodeWithText("Test song 3", substring = false).performClick()
        compose.waitUntil(10_000) { model.playingId == songs[2].id }
        compose.runOnIdle { model.controller!!.pause() }
        compose.onNodeWithText("Clear queue", substring = false).performClick()
        compose.onNodeWithText("The queue is empty").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, model.controller!!.mediaItemCount) }
        val key = "queue-${repo.session.namespace}"
        compose.waitUntil(10_000) { !app.getSharedPreferences("playback", android.content.Context.MODE_PRIVATE).contains(key) }
        compose.onNodeWithText("Close", substring = false).performClick()
        scenario!!.close()
        withContext(Dispatchers.Main) { app.stopService(android.content.Intent(app, MusicService::class.java)) }
        delay(500)
        val browser = withContext(Dispatchers.Main) {
            MediaBrowser.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync().await()
        }
        try {
            delay(500)
            withContext(Dispatchers.Main) { assertEquals(0, browser.mediaItemCount) }
        } finally { withContext(Dispatchers.Main) { browser.release() } }
    }
    @Test fun serverFileDeletionIsBlockedIncludingLegacyEditsAndCleanupPreservesSharedTracks() = runBlocking {
        val first = tracks.first().getString("Id")
        try { repo.api.call("Items/$first", method = "DELETE"); fail("Deletion must be blocked") } catch (_: IllegalArgumentException) { }
        for (request in listOf(
            repo.api.request("Items/$first").delete().build(),
            repo.api.request("Items/Delete").post("{}".toRequestBody()).build()
        )) {
            val before = server.requestCount
            try { app.http.newCall(request).execute().close(); fail("Network guard must block deletion") } catch (_: java.io.IOException) { }
            assertEquals(before, server.requestCount)
        }
        try { repo.queueEdit(playlistId, "Delete", ""); fail("Playlist deletion is disabled") } catch (_: IllegalArgumentException) { }
        repo.dao.putEdit(PlaylistEdit("legacy-delete", playlistId, "Delete", "", "{}"))
        val before = server.requestCount
        repo.syncEdits()
        assertEquals(before, server.requestCount)
        assertEquals(1, repo.dao.outstanding(playlistId))
        assertTrue(repo.dao.pendingEdits().isEmpty())
        repo.select(repo.dao.item(albumId)!!, false)
        awaitComplete()
        repo.select(repo.dao.item(playlistId)!!, false)
        repo.release(albumId)
        assertFalse(repo.dao.selection(albumId)!!.retained)
        assertTrue(repo.dao.members().any { it.selectionId == albumId })
        assertTrue(repo.file(tracks[2].getString("Id")).exists())
        repo.cleanUnreferenced()
        assertTrue(repo.file(first).exists())
        assertTrue(repo.file(tracks[1].getString("Id")).exists())
        assertFalse(repo.file(tracks[2].getString("Id")).exists())
        assertEquals(0, mutations.get())
    }
    @Test fun offlineCatalogueIncludesOnlyPlayableTracksAndTheirCollections() {
        val items = listOf(
            Item("one", "Downloaded", "Audio", albumId = "album", albumArtistIds = "artist"),
            Item("two", "Online only", "Audio", albumId = "other"),
            Item("album", "Album", "MusicAlbum"), Item("other", "Other", "MusicAlbum"),
            Item("artist", "Artist", "MusicArtist"), Item("playlist", "Playlist", "Playlist"))
        assertEquals(setOf("one", "album", "artist", "playlist"), offlineItemIds(items, setOf("one"), emptyList(),
            listOf(PlaylistEntry("playlist", "entry", "one", 0))))
        assertTrue(offlineItemIds(items, emptySet(), emptyList(), emptyList()).isEmpty())
    }
    @Test fun migrationPreservesExistingLibraryAndOfflineSelections() = runBlocking {
        val name = "migration-test.db"
        app.deleteDatabase(name)
        val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name), null)
        try {
            val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
            val schema = JSONObject(testContext.assets.open("dev.jellude.data.LibraryDatabase/1.json").bufferedReader().use { it.readText() })
                .getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql")
                    .replace("\u0024{TABLE_NAME}", entity.getString("tableName")))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL("INSERT INTO items VALUES ('track', 'Preserved song', 'Audio', '', '', '', 1, 1, 1000, '', '', '')")
            old.execSQL("INSERT INTO selections VALUES ('track', 'Preserved song', 'Audio', 0, 1, NULL)")
            old.version = 1
        } finally { old.close() }
        val migrated = androidx.room.Room.databaseBuilder(app, LibraryDatabase::class.java, name)
            .addMigrations(LibraryDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Preserved song", migrated.library().item("track")!!.title)
            assertEquals("", migrated.library().item("track")!!.codec)
            assertTrue(migrated.library().selection("track")!!.retained)
        } finally { migrated.close(); app.deleteDatabase(name) }
    }
    private suspend fun LibraryDao.putAssetsForTest(assets: List<Asset>) { assets.forEach { putAsset(it) } }
    private fun pressSystemBack() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350) // Let the platform dialog window finish its entrance animation.
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = java.io.File(app.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        // Keep review images after Gradle uninstalls the test app and its external files.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp ${file.absolutePath} /data/local/tmp/jellude-$name.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { output -> output.readBytes() }
            }
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
