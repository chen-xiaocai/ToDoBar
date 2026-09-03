package com.chenxiaocai.todobar.inbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncProtocolTest {
    @Test fun encryptedRoundTripAuthenticatesMetadata() {
        val key = ByteArray(32) { it.toByte() }
        val data = SyncProtocol.seal(JSONObject().put("value", 7), "sync", "server", "device", key)
        val (_, opened) = SyncProtocol.open(data, "sync", key)
        assertEquals(7, opened.getInt("value"))
        assertThrows(Exception::class.java) { SyncProtocol.open(data, "sync", ByteArray(32) { (it + 1).toByte() }) }
    }
}
