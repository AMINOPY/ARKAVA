package dev.arkava.vpn.core

import org.json.JSONObject

/** Small, fail-closed client for the pinned official libXray Invoke API. */
class LibXrayClient(
    private val invokeNative: (String) -> String = NativeInvoke::invoke
) {
    fun start(xrayJson: String) {
        require(xrayJson.isNotBlank()) { "Xray config is empty" }
        JSONObject(xrayJson) // reject malformed JSON before crossing the native boundary
        call("runXray", JSONObject().put("xrayJson", xrayJson))
    }

    fun stop() {
        call("stopXray", JSONObject())
    }

    fun version(): String {
        val data = call("xrayVersion", JSONObject())
        return data.optString("version").takeIf { it.isNotBlank() }
            ?: error("libXray version is missing from response")
    }

    fun isRunning(): Boolean = call("getXrayState", JSONObject()).optBoolean("running", false)

    private fun call(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", API_VERSION)
            .put("method", method)
            .put("payload", payload)
        val requestText = request.toString()
        require(requestText.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES) {
            "libXray request exceeds 16 MiB limit"
        }
        val responseText = invokeNative(requestText)
        require(responseText.isNotBlank()) { "libXray returned an empty response" }
        require(responseText.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES) {
            "libXray response exceeds 16 MiB limit"
        }
        val response = try { JSONObject(responseText) } catch (_: Exception) {
            throw IllegalStateException("libXray returned malformed response JSON")
        }
        if (!response.optBoolean("success", false)) {
            val message = response.optString("error").ifBlank { "libXray operation failed" }
            throw IllegalStateException(message.take(MAX_ERROR_LENGTH))
        }
        val data = response.opt("data")
        return when (data) {
            is JSONObject -> data
            null, JSONObject.NULL -> JSONObject()
            else -> JSONObject().put("value", data)
        }
    }

    private object NativeInvoke {
        private val method by lazy {
            val binding = try { Class.forName("libXray.LibXray") } catch (e: Exception) {
                throw IllegalStateException("Official libXray Android binding is not packaged", e)
            }
            try { binding.getMethod("invoke", String::class.java) } catch (e: Exception) {
                throw IllegalStateException("libXray Invoke API is unavailable", e)
            }
        }

        fun invoke(request: String): String = try {
            method.invoke(null, request) as? String
                ?: throw IllegalStateException("libXray returned a non-string response")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw IllegalStateException("libXray native invocation failed", e.cause ?: e)
        }
    }

    private companion object {
        const val API_VERSION = 3
        const val MAX_ENVELOPE_BYTES = 16 * 1024 * 1024
        const val MAX_ERROR_LENGTH = 240
    }
}