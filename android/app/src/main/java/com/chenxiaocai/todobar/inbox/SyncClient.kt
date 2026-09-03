package com.chenxiaocai.todobar.inbox

import android.net.Network
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class SyncClient(private val endpoint: ServiceEndpoint, private val network: Network? = null) {
    fun pair(ticket: PairingTicket, deviceID: String, deviceName: String): ByteArray {
        val payload = JSONObject().put("deviceID", deviceID).put("deviceName", deviceName)
        val response = exchange(SyncProtocol.seal(payload, "pair", ticket.serverID, deviceID, ticket.provisionalKey))
        val (envelope, opened) = SyncProtocol.open(response, "pair_response", ticket.provisionalKey)
        require(envelope.serverID == ticket.serverID && envelope.deviceID == deviceID)
        return Base64.decode(opened.getString("sessionKey"), Base64.NO_WRAP).also { require(it.size == 32) }
    }

    fun sync(state: PairingState, items: List<InboxItem>): List<String> {
        val request = SyncProtocol.seal(SyncProtocol.syncPayload(items), "sync", state.serverID, state.deviceID, state.sessionKey)
        val response = exchange(request)
        val (envelope, opened) = SyncProtocol.open(response, "sync_response", state.sessionKey)
        require(envelope.serverID == state.serverID && envelope.deviceID == state.deviceID)
        val values = opened.getJSONArray("acknowledgedIDs")
        return List(values.length()) { values.getString(it) }
    }

    fun unbind(state: PairingState) {
        val payload = JSONObject().put("requestedAt", System.currentTimeMillis())
        val response = exchange(SyncProtocol.seal(payload, "unbind", state.serverID, state.deviceID, state.sessionKey))
        SyncProtocol.open(response, "unbind_response", state.sessionKey)
    }

    private fun exchange(request: ByteArray): ByteArray {
        val socket = Socket()
        socket.soTimeout = 8_000
        network?.bindSocket(socket)
        socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 5_000)
        return socket.use {
            DataOutputStream(it.getOutputStream()).apply { write(SyncProtocol.frame(request)); flush() }
            val input = DataInputStream(it.getInputStream())
            val length = input.readInt()
            require(length in 1..SyncProtocol.MAX_FRAME)
            ByteArray(length).also(input::readFully).also { data -> Log.i("ToDoBarInbox", "Encrypted response received byteCount=${data.size}") }
        }
    }
}
