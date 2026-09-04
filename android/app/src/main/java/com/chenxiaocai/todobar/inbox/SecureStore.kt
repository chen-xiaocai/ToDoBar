package com.chenxiaocai.todobar.inbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairingState(val serverID: String, val deviceID: String, val deviceName: String, val ssid: String, val sessionKey: ByteArray)
data class PairingTicket(val serverID: String, val provisionalKey: ByteArray)

class SecureStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_pairing", Context.MODE_PRIVATE)
    private val alias = "todobar-inbox-wrap-v1"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(): PairingState? {
        val encoded = preferences.getString("state", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            PairingState(json.getString("serverID"), json.getString("deviceID"), json.getString("deviceName"), json.getString("ssid"), Base64.decode(json.getString("sessionKey"), Base64.NO_WRAP))
        } catch (error: Exception) {
            Log.e("ToDoBarInbox", "Pairing state load failed error=${error.stackTraceToString()}"); null
        }
    }

    fun save(state: PairingState) {
        val json = JSONObject().put("serverID", state.serverID).put("deviceID", state.deviceID).put("deviceName", state.deviceName).put("ssid", state.ssid).put("sessionKey", Base64.encodeToString(state.sessionKey, Base64.NO_WRAP))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray())
        check(preferences.edit().putString("state", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).commit()) {
            "Pairing state could not be persisted"
        }
        Log.i("ToDoBarInbox", "Pairing state saved serverID=${state.serverID} deviceID=${state.deviceID} ssid=${state.ssid}")
    }

    fun clear() { preferences.edit().clear().apply(); Log.i("ToDoBarInbox", "Local pairing state cleared") }

    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    companion object {
        fun parseTicket(value: String): PairingTicket {
            val uri = android.net.Uri.parse(value)
            require(uri.scheme == "todobar-sync" && uri.host == "pair" && uri.getQueryParameter("v") == "1")
            val server = requireNotNull(uri.getQueryParameter("server"))
            val key = Base64.decode(requireNotNull(uri.getQueryParameter("key")), Base64.DEFAULT)
            require(key.size == 32)
            return PairingTicket(server, key)
        }
    }
}
