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

class JelludeApp : Application(), coil.ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var store: SessionStore
    lateinit var http: OkHttpClient
    var repository: MusicRepository? = null
        private set
    override fun newImageLoader() = coil.ImageLoader.Builder(this).okHttpClient(http).build()
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
            }.build()
        store.load()?.let(::openSession)
    }
    fun openSession(session: ServerSession) {
        check(repository == null || repository?.session?.namespace == session.namespace) {
            "Changing accounts requires restarting the app"
        }
        store.save(session)
        if (repository != null) { repository!!.api = JellyfinApi(http, session); return }
        val database = Room.databaseBuilder(this, LibraryDatabase::class.java, "library-${session.namespace}.db").build()
        repository = MusicRepository(this, session, database, JellyfinApi(http, session),
            File(filesDir, "music/${session.namespace}").apply { mkdirs() })
        repository!!.scheduleMaintenance()
        applicationScope.launch { repository?.repair(null, false) }
    }
}
