package dev.arkava.vpn.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ProfileValidator {
    private val supportedSchemes = setOf("vless", "vmess", "trojan", "ss", "socks", "hysteria2", "hy2")

    /** Validates a full Xray JSON configuration shape without starting the engine. */
    fun validateJson(json: String): Result<JSONObject> = runCatching {
        require(json.isNotBlank()) { "Profile is empty" }
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "Profile exceeds size limit" }
        val root = JSONObject(json)
        val outbounds = root.optJSONArray("outbounds")
            ?: throw IllegalArgumentException("Xray profile must contain an outbounds array")
        require(outbounds.length() in 1..MAX_OUTBOUNDS) { "Xray profile has no outbounds or too many outbounds" }
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index)
                ?: throw IllegalArgumentException("Outbound entries must be JSON objects")
            require(outbound.optString("protocol").isNotBlank()) { "Outbound protocol is missing" }
        }
        root
    }.recoverCatching { error ->
        if (error is JSONException) throw IllegalArgumentException("Invalid JSON profile", error)
        throw error
    }

    /** Validates one supported share-link URI; native parsing remains the engine's job. */
    fun validateShareLink(link: String): Result<String> = runCatching {
        require(link.isNotBlank() && link.length <= MAX_SHARE_LINK_CHARS) { "Share link is empty or too large" }
        require(link.none { it == '\n' || it == '\r' }) { "Only one share link is accepted at a time" }
        val separator = link.indexOf("://")
        require(separator > 0 && separator < link.length - 3) { "Malformed share link" }
        val scheme = link.substring(0, separator).lowercase()
        require(scheme in supportedSchemes) { "Unsupported profile scheme" }
        link
    }

    fun validateSubscriptionText(text: String): Result<String> = runCatching {
        require(text.isNotBlank()) { "Subscription is empty" }
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_SUBSCRIPTION_BYTES) { "Subscription exceeds size limit" }
        text
    }

    private const val MAX_JSON_BYTES = 1_000_000
    private const val MAX_SHARE_LINK_CHARS = 64_000
    private const val MAX_SUBSCRIPTION_BYTES = 4_000_000
    private const val MAX_OUTBOUNDS = 256
}
