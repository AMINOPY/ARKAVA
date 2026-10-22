package dev.arkava.vpn.core

import org.json.JSONArray
import org.json.JSONObject

/** Adds the official Xray TUN inbound and passes Android VpnService FD through root env. */
object TunConfigBuilder {
    fun prepare(profileJson: String, tunFd: Int, mtu: Int = 1500): String {
        require(tunFd >= 0) { "Invalid Android TUN file descriptor" }
        require(mtu in 576..9000) { "Invalid TUN MTU" }
        val root = JSONObject(profileJson)
        require(root.optJSONArray("outbounds")?.length()?.let { it > 0 } == true) {
            "Xray profile must contain at least one outbound"
        }
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
        var tunInbound: JSONObject? = null
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            if (inbound.optString("protocol").equals("tun", ignoreCase = true)) {
                require(tunInbound == null) { "Profile contains multiple TUN inbounds" }
                tunInbound = inbound
            }
        }
        val inbound = tunInbound ?: JSONObject()
            .put("tag", "arkava-tun")
            .put("port", 0)
            .put("protocol", "tun")
        val settings = inbound.optJSONObject("settings") ?: JSONObject()
        settings.put("mtu", mtu)
        settings.put("gateway", JSONArray()
            .put("10.111.0.1/32")
            .put("fd00:1:fd00::1/128"))
        inbound.put("settings", settings)
        if (tunInbound == null) inbounds.put(inbound)
        root.put("inbounds", inbounds)
        val env = root.optJSONObject("env") ?: JSONObject()
        env.put("xray.tun.fd", tunFd.toString())
        root.put("env", env)
        return root.toString()
    }
}