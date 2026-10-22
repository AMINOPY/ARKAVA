package dev.arkava.vpn.core

import org.json.JSONException
import org.json.JSONObject

object ProfileValidator {
    private val supportedSchemes = setOf("vless", "vmess", "trojan", "ss", "socks", "wireguard")
    fun validateJson(json: String): Result<JSONObject> = runCatching {
        require(json.isNotBlank()) { "Profile is empty" }
        require(json.length <= 1_000_000) { "Profile exceeds size limit" }
        JSONObject(json).also { require(it.length() > 0) { "Profile has no fields" } }
    }.recoverCatching { error ->
        if (error is JSONException) throw IllegalArgumentException("Invalid JSON profile", error)
        throw error
    }
    fun validateShareLink(link: String): Result<String> = runCatching {
        require(link.length <= 64_000) { "Share link exceeds size limit" }
        val scheme = link.substringBefore("://", "").lowercase()
        require(scheme in supportedSchemes) { "Unsupported profile scheme" }
        require(link.contains("://") && link.substringAfter("://").isNotBlank()) { "Malformed share link" }
        link
    }
}
