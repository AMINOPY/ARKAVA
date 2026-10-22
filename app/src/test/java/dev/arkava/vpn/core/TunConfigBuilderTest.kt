package dev.arkava.vpn.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunConfigBuilderTest {
    @Test fun addsTunInboundAndDescriptorEnvironment() {
        val result = JSONObject(TunConfigBuilder.prepare(
            """{"outbounds":[{"protocol":"freedom"}]}""", 42
        ))
        val inbound = result.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("tun", inbound.getString("protocol"))
        assertEquals(1500, inbound.getJSONObject("settings").getInt("mtu"))
        assertEquals("42", result.getJSONObject("env").getString("xray.tun.fd"))
    }

    @Test fun preservesExistingInboundAndAddsOnlyOneTun() {
        val result = JSONObject(TunConfigBuilder.prepare(
            """{"inbounds":[{"protocol":"socks","port":1080}],"outbounds":[{"protocol":"freedom"}],"env":{"other":"kept"}}""", 7
        ))
        val inbounds = result.getJSONArray("inbounds")
        assertEquals(2, inbounds.length())
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))
        assertEquals("kept", result.getJSONObject("env").getString("other"))
        assertEquals("7", result.getJSONObject("env").getString("xray.tun.fd"))
    }

    @Test fun rejectsMultipleTunInboundsAndMissingOutbound() {
        try { TunConfigBuilder.prepare("""{"outbounds":[{"protocol":"freedom"}],"inbounds":[{"protocol":"tun"},{"protocol":"tun"}]}""", 1); throw AssertionError("expected rejection") }
        catch (_: IllegalArgumentException) { }
        try { TunConfigBuilder.prepare("""{"inbounds":[]}""", 1); throw AssertionError("expected rejection") }
        catch (_: IllegalArgumentException) { }
    }
}