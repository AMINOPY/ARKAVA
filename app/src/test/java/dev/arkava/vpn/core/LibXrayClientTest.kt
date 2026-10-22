package dev.arkava.vpn.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibXrayClientTest {
    @Test fun startSendsVersionedRunRequest() {
        var requestSeen: JSONObject? = null
        val client = LibXrayClient { raw ->
            requestSeen = JSONObject(raw)
            """{"success":true,"data":{},"error":""}"""
        }
        val config = """{"outbounds":[{"protocol":"freedom"}]}"""
        client.start(config)
        assertEquals(3, requestSeen!!.getInt("apiVersion"))
        assertEquals("runXray", requestSeen!!.getString("method"))
        assertEquals(config, requestSeen!!.getJSONObject("payload").getString("xrayJson"))
    }

    @Test fun invalidConfigNeverCallsNative() {
        var called = false
        val client = LibXrayClient { called = true; """{"success":true,"data":{}}""" }
        try { client.start("not-json"); fail("expected malformed JSON rejection") }
        catch (_: org.json.JSONException) { }
        assertFalse(called)
    }

    @Test fun nativeFailureIsSurfacedAndBounded() {
        val client = LibXrayClient { """{"success":false,"data":null,"error":"bad outbound"}""" }
        try { client.stop(); fail("expected native failure") }
        catch (e: IllegalStateException) { assertEquals("bad outbound", e.message) }
    }

    @Test fun stateAndVersionResponsesAreParsed() {
        val client = LibXrayClient { raw ->
            when (JSONObject(raw).getString("method")) {
                "getXrayState" -> """{"success":true,"data":{"running":true},"error":""}"""
                else -> """{"success":true,"data":{"version":"test-version"},"error":""}"""
            }
        }
        assertTrue(client.isRunning())
        assertEquals("test-version", client.version())
    }
}