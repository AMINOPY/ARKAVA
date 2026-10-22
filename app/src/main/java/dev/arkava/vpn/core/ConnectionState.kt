package dev.arkava.vpn.core

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

data class ConnectionSnapshot(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val engineId: String? = null,
    val message: String? = null,
    val changedAtEpochMs: Long = System.currentTimeMillis()
)
