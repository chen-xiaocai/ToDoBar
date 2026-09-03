package com.chenxiaocai.todobar.inbox

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class Envelope(val version: Int, val kind: String, val serverID: String, val deviceID: String?, val sealedPayload: String)

object SyncProtocol {
    const val VERSION = 1
    const val MAX_FRAME = 1_048_576

    fun seal(payload: JSONObject, kind: String, serverID: String, deviceID: String?, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.updateAAD(aad(kind, serverID, deviceID))
        val combined = cipher.iv + cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        return JSONObject().put("version", VERSION).put("kind", kind).put("serverID", serverID).apply {
            if (deviceID == null) put("deviceID", JSONObject.NULL) else put("deviceID", deviceID)
        }.put("sealedPayload", Base64.getEncoder().encodeToString(combined)).toString().toByteArray(Charsets.UTF_8)
    }

    fun open(data: ByteArray, expectedKind: String, key: ByteArray): Pair<Envelope, JSONObject> {
        val json = JSONObject(String(data, Charsets.UTF_8))
        val envelope = Envelope(json.getInt("version"), json.getString("kind"), json.getString("serverID"), if (json.isNull("deviceID")) null else json.getString("deviceID"), json.getString("sealedPayload"))
        require(envelope.version == VERSION && envelope.kind == expectedKind)
        val combined = Base64.getDecoder().decode(envelope.sealedPayload)
        require(combined.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
        cipher.updateAAD(aad(envelope.kind, envelope.serverID, envelope.deviceID))
        return envelope to JSONObject(String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8))
    }

    fun syncPayload(items: List<InboxItem>) = JSONObject().put("items", JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("createdAt", it.createdAt)) }
    })

    fun frame(data: ByteArray): ByteArray {
        require(data.size in 1..MAX_FRAME)
        return ByteBuffer.allocate(4 + data.size).putInt(data.size).put(data).array()
    }

    private fun aad(kind: String, serverID: String, deviceID: String?) = "$VERSION|$kind|$serverID|${deviceID ?: ""}".toByteArray(Charsets.UTF_8)
}
