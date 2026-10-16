package dev.jellude

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellude.core.ListenTrack
import dev.jellude.core.QualifiedListen
import dev.jellude.data.SessionStore
import dev.jellude.listenbrainz.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ListenBrainzIntegrationTest {
    private val token = "11111111-1111-1111-1111-111111111111"
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var store: SessionStore
    private lateinit var database: ListenDatabase
    private lateinit var manager: ListenBrainzManager
    private var now = 1_800_000_000_000L
    private fun queued(id: String = UUID.randomUUID().toString(), account: String = "listener") = QualifiedListen(id, account,
        ListenTrack("local", "Always on My Mind - Piano Cover", "Custom performer", "Personal recordings", 180_000), 1_799_999_000)
    private fun manager() = ListenBrainzManager(context, store, database, server.url("/1/"), scheduleWork = false, clock = { now })

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("listenbrainz-test-session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("listenbrainz-status", Context.MODE_PRIVATE).edit().clear().commit()
        store = SessionStore(context, "listenbrainz-test-session")
        database = Room.databaseBuilder(context, ListenDatabase::class.java, "listenbrainz-test.db").build()
        server = MockWebServer().apply { start() }
        manager = manager()
    }
    @After fun teardown() = runBlocking {
        database.clearAllTables(); database.close(); context.deleteDatabase("listenbrainz-test.db")
        store.forgetListenBrainz()
        server.shutdown()
    }
    private suspend fun connect(username: String = "listener") {
        server.enqueue(MockResponse().setBody(JSONObject().put("valid", true).put("user_name", username).toString()))
        manager.connect(token)
        val request = server.takeRequest()
        assertEquals("/1/validate-token", request.path)
        assertEquals("Token $token", request.getHeader("Authorization"))
    }

    @Test fun disabledByDefaultAndValidationDoesNotSubmitHistory() = runBlocking {
        assertFalse(manager.connection.value.enabled)
        manager.record(queued())
        assertEquals(0, database.listens().observeSummary().first().total)
        assertEquals(0, server.requestCount)
        connect()
        assertEquals("listener", manager.connection.value.username)
        assertEquals(1, server.requestCount)
        val disk = context.getSharedPreferences("listenbrainz-test-session", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(disk.contains(token))
        assertEquals(token to "listener", store.loadListenBrainz())
    }

    @Test fun offlineQueueSurvivesReopenAndPreservesCustomMetadataAndTimestamp() = runBlocking {
        connect()
        store.offline = true
        val listen = queued()
        manager.record(listen)
        manager.record(listen) // The same playback occurrence is durable and unique.
        assertTrue(manager.upload())
        assertEquals(1, server.requestCount)
        database.close()
        database = Room.databaseBuilder(context, ListenDatabase::class.java, "listenbrainz-test.db").build()
        manager = manager()
        assertEquals(1, database.listens().observeSummary().first().total)
        store.offline = false
        server.enqueue(MockResponse().setBody("{\"status\":\"ok\"}"))
        assertFalse(manager.upload())
        val request = server.takeRequest()
        assertEquals("/1/submit-listens", request.path)
        val json = JSONObject(request.body.readUtf8())
        assertEquals("import", json.getString("listen_type"))
        val payload = json.getJSONArray("payload").getJSONObject(0)
        assertEquals(listen.startedAt, payload.getLong("listened_at"))
        val metadata = payload.getJSONObject("track_metadata")
        assertEquals(listen.track.title, metadata.getString("track_name"))
        assertEquals(listen.track.artist, metadata.getString("artist_name"))
        assertEquals(listen.track.album, metadata.getString("release_name"))
        val info = metadata.getJSONObject("additional_info")
        assertEquals("Jellude", info.getString("submission_client"))
        assertFalse(info.has("recording_mbid"))
        assertFalse(info.has("music_service"))
        assertEquals(0, database.listens().observeSummary().first().total)
    }

    @Test fun pausedSubmissionKeepsQueueAndDisconnectPreventsCrossAccountUploads() = runBlocking {
        connect()
        manager.record(queued())
        manager.setEnabled(false)
        assertFalse(manager.upload())
        manager.record(queued())
        assertEquals(1, database.listens().observeSummary().first().total)
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody("{\"valid\":true,\"user_name\":\"other\"}"))
        try { manager.connect(token); fail("Must explicitly disconnect before changing accounts") }
        catch (_: IllegalStateException) { }
        server.takeRequest()
        assertEquals("listener", manager.connection.value.username)
        manager.disconnect()
        assertNull(store.loadListenBrainz())
        assertEquals(0, database.listens().observeSummary().first().total)
        connect("other")
        manager.record(queued(account = "listener"))
        assertEquals(0, database.listens().observeSummary().first().total)
    }

    @Test fun rateLimitIsRespectedAndRetryUsesOriginalPayload() = runBlocking {
        connect()
        manager.record(queued())
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        assertTrue(manager.upload())
        val first = server.takeRequest().body.readUtf8()
        assertEquals(1, database.listens().observeSummary().first().failed)
        manager.retry()
        assertTrue(manager.upload())
        assertEquals(2, server.requestCount)
        now += 120_000
        server.enqueue(MockResponse())
        assertFalse(manager.upload())
        assertEquals(first, server.takeRequest().body.readUtf8())
        assertEquals(0, database.listens().observeSummary().first().total)
    }

    @Test fun rejectedTokenRetainsHistoryAndRequiresReconnect() = runBlocking {
        connect()
        manager.record(queued())
        server.enqueue(MockResponse().setResponseCode(401))
        assertFalse(manager.upload())
        server.takeRequest()
        manager.record(queued())
        assertFalse(manager.upload())
        assertEquals(2, server.requestCount)
        assertEquals(2, database.listens().observeSummary().first().total)
        connect()
        server.enqueue(MockResponse()); server.enqueue(MockResponse())
        assertFalse(manager.upload())
        server.takeRequest(); server.takeRequest()
        assertEquals(0, database.listens().observeSummary().first().total)
    }

    @Test fun invalidMetadataIsVisibleAndServerErrorsKeepQueuedListens() = runBlocking {
        connect()
        val missing = queued().let { it.copy(track = it.track.copy(artist = "")) }
        manager.record(missing)
        assertEquals(1, database.listens().observeSummary().first().failed)
        assertFalse(manager.upload())
        assertEquals(1, server.requestCount)
        manager.clearQueue()
        manager.record(queued())
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(manager.upload())
        server.takeRequest()
        assertEquals(1, database.listens().observeSummary().first().total)
    }

    @Test fun slowUploadDoesNotDelayDurableRecordingOfTheNextListen() = runBlocking {
        connect()
        manager.record(queued())
        server.enqueue(MockResponse().setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        val uploading = async(Dispatchers.IO) { manager.upload() }
        server.takeRequest()
        withTimeout(1_000) { manager.record(queued()) }
        assertEquals(2, database.listens().observeSummary().first().total)
        assertFalse(uploading.await())
        assertEquals(1, database.listens().observeSummary().first().total)
    }
}
