package dev.jellude.listenbrainz

import android.content.Context
import androidx.room.*
import androidx.work.*
import dev.jellude.BuildConfig
import dev.jellude.JelludeApp
import dev.jellude.core.QualifiedListen
import dev.jellude.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

@Entity(tableName = "listens")
data class QueuedListen(@PrimaryKey val id: String, val account: String, val title: String, val artist: String,
    val album: String, val durationMs: Long, val startedAt: Long, val blocked: Boolean = false,
    val error: String? = null, val retryAt: Long = 0)

data class ListenQueueSummary(val total: Int, val failed: Int)

@Dao
interface ListenDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(listen: QueuedListen)
    @Update suspend fun update(listen: QueuedListen)
    @Query("SELECT * FROM listens WHERE account=:account AND blocked=0 ORDER BY startedAt LIMIT 100")
    suspend fun pending(account: String): List<QueuedListen>
    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN error IS NOT NULL THEN 1 ELSE 0 END), 0) AS failed FROM listens")
    fun observeSummary(): kotlinx.coroutines.flow.Flow<ListenQueueSummary>
    @Query("SELECT error FROM listens WHERE error IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeError(): kotlinx.coroutines.flow.Flow<String?>
    @Query("DELETE FROM listens WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM listens") suspend fun clear()
    @Query("UPDATE listens SET blocked=0, error=NULL") suspend fun retry()
}

@Database(entities = [QueuedListen::class], version = 1, exportSchema = true)
abstract class ListenDatabase : RoomDatabase() { abstract fun listens(): ListenDao }

data class ListenBrainzConnection(val username: String = "", val enabled: Boolean = false)

/** Own HTTP client: Jellyfin's authentication and server-operation policy must never reach this API. */
internal class ListenBrainzApi(private val client: OkHttpClient,
    private val base: HttpUrl = "https://api.listenbrainz.org/1/".toHttpUrl()) {
    private fun request(path: String, token: String) = Request.Builder().url(requireNotNull(base.resolve(path)))
        .header("Authorization", "Token $token")
        .header("User-Agent", "Jellude/${BuildConfig.VERSION_NAME} (https://github.com/cdalamagkas/Jellude)")

    fun validate(token: String): String {
        client.newCall(request("validate-token", token).build()).execute().use { response ->
            if (response.code != 200) throw IOException("Could not validate ListenBrainz token. Try again when online.")
            val result = JSONObject(response.body.string())
            check(result.optBoolean("valid")) { "Invalid ListenBrainz token" }
            return result.getString("user_name").also { check(it.isNotBlank()) { "Missing ListenBrainz account name" } }
        }
    }

    fun submit(token: String, listen: QueuedListen): Pair<Int, Long> {
        val info = JSONObject().put("media_player", "Jellude").put("submission_client", "Jellude")
            .put("submission_client_version", BuildConfig.VERSION_NAME)
        if (listen.durationMs > 0) info.put("duration_ms", listen.durationMs)
        val metadata = JSONObject().put("artist_name", listen.artist).put("track_name", listen.title).put("additional_info", info)
        if (listen.album.isNotBlank()) metadata.put("release_name", listen.album)
        // Preserve local metadata; never invent a MusicBrainz ID or send server URLs/audio.
        val payload = JSONObject().put("listen_type", "import").put("payload", JSONArray().put(
            JSONObject().put("listened_at", listen.startedAt).put("track_metadata", metadata)))
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        client.newCall(request("submit-listens", token).post(body).build()).execute().use { response ->
            val retrySeconds = response.header("Retry-After")?.toLongOrNull()
                ?: response.header("X-RateLimit-Reset-In")?.toLongOrNull() ?: 60L
            return response.code to retrySeconds.coerceIn(1, 86_400)
        }
    }
}

class ListenBrainzManager(context: Context, private val store: SessionStore, val database: ListenDatabase,
    baseUrl: HttpUrl = "https://api.listenbrainz.org/1/".toHttpUrl(),
    private val scheduleWork: Boolean = true, private val clock: () -> Long = System::currentTimeMillis) {
    private val work = WorkManager.getInstance(context)
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("listenbrainz-status", Context.MODE_PRIVATE)
    private val mutableConnection = MutableStateFlow(readConnection())
    val connection = mutableConnection.asStateFlow()
    private val mutableStatus = MutableStateFlow(preferences.getString("status", "Not connected").orEmpty())
    val status = mutableStatus.asStateFlow()
    val dao = database.listens()
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            if (store.offline) throw IOException("Offline mode is enabled")
            if (chain.request().url.encodedPath.endsWith("/submit-listens") && !connection.value.enabled)
                throw IOException("ListenBrainz submissions are disabled")
            chain.proceed(chain.request())
        }.build()
    private val api = ListenBrainzApi(http, baseUrl)

    private fun readConnection(): ListenBrainzConnection {
        val credentials = runCatching { store.loadListenBrainz() }.getOrNull()
        return ListenBrainzConnection(credentials?.second.orEmpty(), credentials != null && store.listenBrainzEnabled)
    }
    private fun status(value: String) {
        mutableStatus.value = value
        preferences.edit().putString("status", value).apply()
    }

    suspend fun connect(token: String) = withContext(Dispatchers.IO) { mutex.withLock {
        check(!store.offline) { "Turn off Offline mode to connect ListenBrainz" }
        val value = token.trim()
        require(value.matches(Regex("[a-fA-F0-9-]{36}"))) { "Enter your ListenBrainz user token" }
        val username = try { api.validate(value) }
        catch (e: CancellationException) { throw e }
        catch (e: IllegalStateException) { throw e }
        catch (_: Exception) { throw IOException("Could not validate ListenBrainz token. Check your connection and token.") }
        val previous = connection.value.username
        check(previous.isBlank() || previous == username) { "Disconnect the current ListenBrainz account before changing accounts" }
        store.saveListenBrainz(value, username)
        store.listenBrainzEnabled = true
        preferences.edit().putBoolean("auth_failed", false).apply()
        mutableConnection.value = readConnection()
        dao.retry()
        status("Connected to $username")
        schedule()
    } }

    fun setEnabled(value: Boolean) {
        check(!value || connection.value.username.isNotBlank()) { "Connect ListenBrainz first" }
        store.listenBrainzEnabled = value
        mutableConnection.value = readConnection()
        if (value) { status("Submissions enabled"); schedule(replace = true) }
        else { http.dispatcher.cancelAll(); stopWork(); status("Paused; queued listens are kept") }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        setEnabled(false)
        mutex.withLock {
            store.forgetListenBrainz()
            mutableConnection.value = ListenBrainzConnection()
            dao.clear()
            status("Disconnected; queued listens deleted")
        }
    }
    fun cancelNetwork() { http.dispatcher.cancelAll() }
    fun recordingFailed() { status("Could not save a listen on this device. Check free storage.") }
    suspend fun clearQueue() = mutex.withLock { dao.clear() }
    suspend fun retry() {
        check(connection.value.enabled) { "Enable ListenBrainz submissions first" }
        check(!preferences.getBoolean("auth_failed", false)) { "Reconnect ListenBrainz before retrying uploads" }
        dao.retry()
        status(if (store.offline) "Waiting: offline mode" else "Retry scheduled")
        schedule(replace = true)
    }

    suspend fun record(listen: QualifiedListen) = withContext(Dispatchers.IO) {
        // Persist immediately even while a slow upload holds the credentials/submission mutex.
        val current = connection.value
        if (!current.enabled || listen.account != current.username) return@withContext
        val track = listen.track
        val invalid = track.artist.isBlank() || track.title.isBlank()
        dao.insert(QueuedListen(listen.id, listen.account, track.title, track.artist, track.album,
            track.durationMs, listen.startedAt, blocked = invalid,
            error = if (invalid) "Missing artist or title in Jellyfin metadata" else null))
        val afterInsert = connection.value
        if (!afterInsert.enabled || listen.account != afterInsert.username) dao.delete(listen.id)
        else schedule()
    }

    /** Returns true when WorkManager should retry. Rows are removed only after a successful response. */
    suspend fun upload(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        if (!connection.value.enabled) return@withLock false
        if (preferences.getBoolean("auth_failed", false)) return@withLock false
        if (store.offline) { status("Waiting: offline mode"); return@withLock true }
        if (preferences.getLong("retry_at", 0) > clock()) return@withLock true
        val credentials = runCatching { store.loadListenBrainz() }.getOrNull()
            ?: run { status("Reconnect ListenBrainz to continue"); return@withLock false }
        val rows = dao.pending(credentials.second)
        for (row in rows) {
            if (!connection.value.enabled || store.offline) return@withLock store.offline
            if (row.retryAt > clock()) return@withLock true
            try {
                val (code, retrySeconds) = api.submit(credentials.first, row)
                when {
                    code == 200 -> { dao.delete(row.id); status("Last upload succeeded") }
                    code == 401 || code == 403 -> {
                        dao.update(row.copy(blocked = true, error = "ListenBrainz rejected the token. Reconnect to retry."))
                        preferences.edit().putBoolean("auth_failed", true).apply()
                        status("Reconnect ListenBrainz: authorization failed")
                        return@withLock false
                    }
                    code == 429 || code >= 500 -> {
                        preferences.edit().putLong("retry_at", clock() + retrySeconds * 1000).apply()
                        dao.update(row.copy(error = "ListenBrainz temporarily unavailable; will retry",
                            retryAt = clock() + retrySeconds * 1000))
                        status("Waiting to retry ListenBrainz")
                        return@withLock true
                    }
                    else -> dao.update(row.copy(blocked = true, error = "ListenBrainz rejected this listen (HTTP $code)"))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                dao.update(row.copy(error = "Upload interrupted; listen kept for retry"))
                status(if (store.offline) "Waiting: offline mode" else "Connection failed; will retry")
                return@withLock connection.value.enabled
            }
        }
        rows.size == 100
    } }

    fun schedule(replace: Boolean = false) {
        if (!scheduleWork || !connection.value.enabled) return
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        work.enqueueUniquePeriodicWork("listenbrainz-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ListenBrainzWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        work.enqueueUniqueWork("listenbrainz-upload", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ListenBrainzWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    private fun stopWork() {
        if (!scheduleWork) return
        work.cancelUniqueWork("listenbrainz-maintenance")
        work.cancelUniqueWork("listenbrainz-upload")
    }
}

class ListenBrainzWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        if ((applicationContext as JelludeApp).listenBrainz.upload()) Result.retry() else Result.success()
}
