package dev.jellude.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import dev.jellude.BuildConfig

class ApiError(val status: Int) : IOException(when (status) {
    401 -> "Sign in again: authentication expired"
    403 -> "Your Jellyfin account does not have permission"
    404 -> "Item is unavailable on the server"
    else -> "Jellyfin returned HTTP $status"
})

class JellyfinApi(val client: OkHttpClient, val session: ServerSession) {
    fun url(path: String, query: Map<String, String> = emptyMap()): String =
        (session.url.trimEnd('/') + "/" + path.trimStart('/')).toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build().toString()

    fun request(path: String, query: Map<String, String> = emptyMap()): Request.Builder =
        Request.Builder().url(url(path, query)).header("Authorization", auth(session.token))

    fun call(path: String, query: Map<String, String> = emptyMap(), method: String = "GET", body: JSONObject? = null): String {
        require(dev.jellude.core.ServerRequestPolicy.allows(method, path.split('/'))) { "This server operation is blocked. Jellude cannot delete server files." }
        val builder = request(path, query)
        if (method != "GET") builder.method(method,
            if (method == "DELETE") null else (body?.toString() ?: "").toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) throw ApiError(it.code)
            it.body?.string().orEmpty()
        }
    }

    /** Publish nothing until all pages succeed. Stable ordering reduces paging drift. */
    fun pages(path: String, query: Map<String, String>): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var start = 0
        var previousPage: String? = null
        var expectedTotal: Int? = null
        while (true) {
            val page = JSONObject(call(path, query + mapOf("StartIndex" to "$start", "Limit" to "200")))
            val items = page.getJSONArray("Items")
            val total = page.getInt("TotalRecordCount")
            if (expectedTotal != null && total != expectedTotal) throw IOException("Library changed while loading; refresh again")
            expectedTotal = total
            if (items.length() == 0) {
                if (start < total) throw IOException("Library changed while loading; refresh again")
                break
            }
            val signature = items.toString()
            if (signature == previousPage) throw IOException("Server repeated a catalogue page")
            previousPage = signature
            for (i in 0 until items.length()) result.add(items.getJSONObject(i))
            start += items.length()
            if (start >= total) break
        }
        return result
    }
    fun catalogue(): List<Item> = pages("Users/${session.userId}/Items", mapOf(
        "Recursive" to "true", "IncludeItemTypes" to "Audio,MusicAlbum,MusicArtist,Playlist",
        "Fields" to "MediaSources,MediaStreams", "SortBy" to "SortName", "SortOrder" to "Ascending"
    )).map(::item).distinctBy { it.id }

    fun collection(selection: Selection): List<Item> = when (selection.kind) {
        "Audio" -> listOf(item(JSONObject(call("Users/${session.userId}/Items/${selection.id}"))))
        "Playlist" -> playlist(selection.id).second
        else -> pages("Users/${session.userId}/Items", mapOf(
            "Recursive" to "true", "IncludeItemTypes" to "Audio", "Fields" to "MediaSources,MediaStreams",
            "SortBy" to "ParentIndexNumber,IndexNumber,SortName", "SortOrder" to "Ascending"
        ) + if (selection.kind == "MusicArtist") mapOf("AlbumArtistIds" to selection.id)
            else mapOf("ParentId" to selection.id)).map(::item).distinctBy { it.id }
    }
    fun playlist(id: String): Pair<List<PlaylistEntry>, List<Item>> {
        val rows = pages("Playlists/$id/Items", mapOf("UserId" to session.userId, "Fields" to "MediaSources,MediaStreams"))
        val entries = rows.mapIndexed { index, row -> PlaylistEntry(id, row.getString("PlaylistItemId"), row.getString("Id"), index) }
        return entries to rows.map(::item)
    }
    fun lyrics(id: String): String? = try { call("Audio/$id/Lyrics") } catch (e: ApiError) {
        if (e.status == 404) null else throw e
    }
    fun item(json: JSONObject): Item {
        val artists = json.optJSONArray("Artists") ?: JSONArray()
        val media = json.optJSONArray("MediaSources")?.optJSONObject(0)
        val streams = media?.optJSONArray("MediaStreams") ?: json.optJSONArray("MediaStreams")
        val audio = (0 until (streams?.length() ?: 0)).mapNotNull { streams?.optJSONObject(it) }
            .firstOrNull { it.optString("Type") == "Audio" }
        return Item(json.getString("Id"), json.optString("Name", "Untitled"), json.optString("Type", "Audio"),
            (0 until artists.length()).joinToString(", ") { artists.getString(it) }, json.optString("Album"),
            json.optString("AlbumId"), json.optInt("ParentIndexNumber"), json.optInt("IndexNumber"),
            json.optLong("RunTimeTicks") / 10_000, json.optJSONObject("ImageTags")?.optString("Primary").orEmpty(),
            media?.optString("ETag").orEmpty(), json.optJSONArray("AlbumArtists")?.let { rows ->
                (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optString("Id") }.joinToString(",")
            }.orEmpty(), audio?.optString("Codec").orEmpty(), audio?.optInt("BitRate") ?: 0,
            audio?.optInt("SampleRate") ?: 0)
    }

    companion object {
        private val deviceId = UUID.randomUUID().toString()
        fun auth(token: String = "") = "MediaBrowser Client=\"Jellude\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\"" +
            if (token.isBlank()) "" else ", Token=\"${token.replace("\"", "")}\""
        fun login(client: OkHttpClient, baseUrl: String, username: String, password: String): ServerSession {
            val parsed = baseUrl.trim().trimEnd('/').toHttpUrl()
            require(parsed.scheme in listOf("https", "http") && parsed.username.isEmpty() && parsed.password.isEmpty()
                && parsed.query == null && parsed.fragment == null) { "Enter a server URL, including https://" }
            val base = parsed.toString().trimEnd('/')
            val request = Request.Builder().url("$base/Users/AuthenticateByName")
                .header("Authorization", auth()).post(JSONObject().put("Username", username).put("Pw", password)
                    .toString().toRequestBody("application/json".toMediaType())).build()
            return client.newCall(request).execute().use {
                if (!it.isSuccessful) throw ApiError(it.code)
                val json = JSONObject(it.body!!.string())
                ServerSession(base, json.getJSONObject("User").getString("Id"), json.getString("AccessToken"), json.getString("ServerId"))
            }
        }
    }
}
