package dev.jellude

import android.app.Application
import androidx.room.Room
import dev.jellude.data.*
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import dev.jellude.listenbrainz.ListenBrainzManager
import dev.jellude.listenbrainz.ListenDatabase

class JelludeApp : Application(), coil.ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var store: SessionStore
    lateinit var http: OkHttpClient
    lateinit var listenBrainz: ListenBrainzManager
    var repository: MusicRepository? = null
        private set
    override fun newImageLoader() = coil.ImageLoader.Builder(this).okHttpClient(http).build()
    fun recordListen(listen: dev.jellude.core.QualifiedListen) {
        applicationScope.launch {
            try { listenBrainz.record(listen) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { listenBrainz.recordingFailed() }
        }
    }
    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        http = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                if (store.offline) throw IOException("Offline mode is enabled")
                var request = chain.request()
                repository?.api?.session?.let { current ->
                    val base = current.url.toHttpUrl()
                    if (request.header("Authorization") != null && request.url.scheme == base.scheme &&
                        request.url.host == base.host && request.url.port == base.port &&
                        request.url.encodedPath.startsWith(base.encodedPath.trimEnd('/') + "/")) {
                        request = request.newBuilder().header("Authorization", JellyfinApi.auth(current.token)).build()
                    }
                }
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                if (!dev.jellude.core.ServerRequestPolicy.allows(request.method, request.url.pathSegments))
                    throw IOException("Server operation blocked: Jellude cannot delete server files")
                chain.proceed(request)
            }.build()
        listenBrainz = ListenBrainzManager(this, store,
            Room.databaseBuilder(this, ListenDatabase::class.java, "listenbrainz.db").build())
        listenBrainz.schedule()
        store.load()?.let(::openSession)
    }
    fun openSession(session: ServerSession) {
        check(repository == null || repository?.session?.namespace == session.namespace) {
            "Changing accounts requires restarting the app"
        }
        store.save(session)
        if (repository != null) { repository!!.api = JellyfinApi(http, session); return }
        val database = Room.databaseBuilder(this, LibraryDatabase::class.java, "library-${session.namespace}.db")
            .addMigrations(LibraryDatabase.MIGRATION_1_2).build()
        repository = MusicRepository(this, session, database, JellyfinApi(http, session),
            File(filesDir, "music/${session.namespace}").apply { mkdirs() })
        repository!!.scheduleMaintenance()
        applicationScope.launch { repository?.repair(null, false) }
    }
}
