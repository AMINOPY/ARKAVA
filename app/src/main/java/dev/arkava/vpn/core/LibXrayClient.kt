package dev.arkava.vpn.core

import android.net.VpnService
import java.lang.reflect.Proxy
import org.json.JSONObject

/** Small, fail-closed client for the pinned official libXray Invoke API. */
class LibXrayClient(
    private val invokeNative: (String) -> String = NativeInvoke::invoke
) {
    fun configureAndroidController(service: VpnService, dnsEndpoint: String) {
        val binding = NativeInvoke.bindingClass()
        val register = binding.methods.firstOrNull {
            it.name.equals("registerDialerController", ignoreCase = true) && it.parameterCount == 1
        } ?: error("libXray dialer-controller API is unavailable")
        val controllerType = register.parameterTypes[0]
        require(controllerType.isInterface) { "Unexpected libXray controller binding type" }
        val controller = Proxy.newProxyInstance(
            controllerType.classLoader, arrayOf(controllerType)
        ) { _, method, args ->
            when (method.name.lowercase()) {
                "protectfd" -> {
                    val fd = (args?.firstOrNull() as? Number)?.toInt()
                        ?: return@newProxyInstance false
                    service.protect(fd)
                }
                "tostring" -> "ARKAVA-VpnSocketProtector"
                "hashcode" -> System.identityHashCode(service)
                "equals" -> args?.firstOrNull() === service
                else -> error("Unsupported libXray controller callback: ${method.name}")
            }
        }
        register.invoke(null, controller)
        val setDns = binding.methods.firstOrNull {
            it.name.equals("setDNS", ignoreCase = true) && it.parameterCount == 2
        } ?: error("libXray Android DNS API is unavailable")
        setDns.invoke(null, controller, dnsEndpoint)
    }

    fun resetAndroidDns() {
        val binding = NativeInvoke.bindingClass()
        val reset = binding.methods.firstOrNull {
            it.name.equals("resetDNS", ignoreCase = true) && it.parameterCount == 0
        } ?: error("libXray Android DNS reset API is unavailable")
        reset.invoke(null)
    }

    fun start(xrayJson: String) {
        require(xrayJson.isNotBlank()) { "Xray config is empty" }
        JSONObject(xrayJson)
        call("runXray", JSONObject().put("xrayJson", xrayJson))
    }

    fun stop() { call("stopXray", JSONObject()) }

    fun version(): String {
        val data = call("xrayVersion", JSONObject())
        return data.optString("version").takeIf { it.isNotBlank() }
            ?: error("libXray version is missing from response")
    }

    fun isRunning(): Boolean = call("getXrayState", JSONObject()).optBoolean("running", false)

    private fun call(method: String, payload: JSONObject): JSONObject {
        val requestText = JSONObject()
            .put("apiVersion", API_VERSION)
            .put("method", method)
            .put("payload", payload)
            .toString()
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
        return when (val data = response.opt("data")) {
            is JSONObject -> data
            null, JSONObject.NULL -> JSONObject()
            else -> JSONObject().put("value", data)
        }
    }

    private object NativeInvoke {
        private val binding by lazy { bindingClass() }
        private val invokeMethod by lazy {
            try { binding.getMethod("invoke", String::class.java) } catch (e: Exception) {
                throw IllegalStateException("libXray Invoke API is unavailable", e)
            }
        }
        fun bindingClass(): Class<*> = try {
            Class.forName("libXray.LibXray")
        } catch (e: Exception) {
            throw IllegalStateException("Official libXray Android binding is not packaged", e)
        }
        fun invoke(request: String): String = try {
            invokeMethod.invoke(null, request) as? String
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