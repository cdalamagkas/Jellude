package dev.jellude.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServerSession(val url: String, val userId: String, val token: String, val serverId: String) {
    val namespace: String get() = MessageDigest.getInstance("SHA-256")
        .digest("$serverId|$userId".toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
}

class SessionStore(context: Context, preferencesName: String = "session") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("jellude-session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("jellude-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(session: ServerSession) {
        val data = JSONObject().put("url", session.url).put("user", session.userId)
            .put("token", session.token).put("server", session.serverId).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(data.toByteArray())
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    fun load(): ServerSession? {
        val data = prefs.getString("data", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)))
        }
        val json = JSONObject(String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))))
        return ServerSession(json.getString("url"), json.getString("user"), json.getString("token"), json.getString("server"))
    }
    fun saveListenBrainz(token: String, username: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = JSONObject().put("token", token).put("username", username).toString()
        check(prefs.edit().putString("lb_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("lb_data", Base64.encodeToString(cipher.doFinal(data.toByteArray()), Base64.NO_WRAP)).commit())
    }
    fun loadListenBrainz(): Pair<String, String>? {
        val data = prefs.getString("lb_data", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("lb_iv", ""), Base64.NO_WRAP)))
        }
        val json = JSONObject(String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))))
        return json.getString("token") to json.getString("username")
    }
    fun forgetListenBrainz() { check(prefs.edit().remove("lb_iv").remove("lb_data").putBoolean("lb_enabled", false).commit()) }
    var listenBrainzEnabled: Boolean
        get() = prefs.getBoolean("lb_enabled", false)
        set(value) { check(prefs.edit().putBoolean("lb_enabled", value).commit()) }
    var offline: Boolean
        get() = prefs.getBoolean("offline", false)
        set(value) { prefs.edit().putBoolean("offline", value).apply() }
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifiOnly", true)
        set(value) { prefs.edit().putBoolean("wifiOnly", value).apply() }
}
