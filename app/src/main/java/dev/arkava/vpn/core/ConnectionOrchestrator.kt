package dev.arkava.vpn.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes lifecycle transitions so two engines cannot own the traffic path concurrently. */
class ConnectionOrchestrator(adapters: Collection<EngineAdapter>, private val publish: (ConnectionSnapshot) -> Unit = {}) {
    private val lock = Mutex()
    private val engines = adapters.associateBy { it.id }
    private var current = ConnectionSnapshot()
    fun snapshot(): ConnectionSnapshot = current

    suspend fun connect(profile: ConnectionProfile): ConnectionSnapshot = lock.withLock {
        if (current.state != ConnectionState.DISCONNECTED && current.state != ConnectionState.ERROR) return@withLock current
        val engine = engines[profile.engineId] ?: return@withLock transition(ConnectionState.ERROR, profile.engineId, "Engine is not installed")
        transition(ConnectionState.CONNECTING, engine.id, null)
        when (val validation = engine.validate(profile)) {
            is EngineValidation.Invalid -> transition(ConnectionState.ERROR, engine.id, validation.reason)
            EngineValidation.Valid -> when (val result = engine.start(profile)) {
                is EngineStartResult.Failed -> transition(ConnectionState.ERROR, engine.id, result.reason)
                EngineStartResult.Started -> when (val health = engine.healthCheck()) {
                    EngineHealth.Healthy -> transition(ConnectionState.CONNECTED, engine.id, null)
                    is EngineHealth.Unhealthy -> {
                        runCatching { engine.stop() }
                        transition(ConnectionState.ERROR, engine.id, health.reason)
                    }
                }
            }
        }
    }

    suspend fun disconnect(): ConnectionSnapshot = lock.withLock {
        if (current.state == ConnectionState.DISCONNECTED) return@withLock current
        val id = current.engineId
        transition(ConnectionState.DISCONNECTING, id, null)
        val stopped = runCatching { id?.let(engines::get)?.stop() }
        if (stopped.isSuccess) transition(ConnectionState.DISCONNECTED, null, null)
        else transition(ConnectionState.ERROR, id, "Engine stop failed")
    }

    private fun transition(state: ConnectionState, engineId: String?, message: String?): ConnectionSnapshot {
        current = ConnectionSnapshot(state, engineId, message)
        publish(current)
        return current
    }
}
