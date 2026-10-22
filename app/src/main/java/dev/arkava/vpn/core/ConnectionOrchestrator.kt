package dev.arkava.vpn.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes lifecycle transitions so two engines cannot own the traffic path concurrently. */
class ConnectionOrchestrator(
    adapters: Collection<EngineAdapter>,
    private val publish: (ConnectionSnapshot) -> Unit = {}
) {
    private val lock = Mutex()
    private val engines = adapters.associateBy { it.id }.also { indexed ->
        require(indexed.size == adapters.size) { "Engine adapter IDs must be unique" }
        require(indexed.keys.all { it.isNotBlank() }) { "Engine adapter IDs must not be blank" }
    }
    @Volatile private var current = ConnectionSnapshot()

    fun snapshot(): ConnectionSnapshot = current

    suspend fun connect(profile: ConnectionProfile): ConnectionSnapshot = lock.withLock {
        if (current.state != ConnectionState.DISCONNECTED && current.state != ConnectionState.ERROR) {
            return@withLock current
        }
        val engine = engines[profile.engineId]
            ?: return@withLock transition(ConnectionState.ERROR, profile.engineId, "Engine is not installed")

        transition(ConnectionState.CONNECTING, engine.id, null)
        try {
            when (val validation = engine.validate(profile)) {
                is EngineValidation.Invalid ->
                    transition(ConnectionState.ERROR, engine.id, validation.reason.take(MAX_ERROR_LENGTH))
                EngineValidation.Valid -> when (val result = engine.start(profile)) {
                    is EngineStartResult.Failed -> {
                        // start() may allocate resources before discovering a late failure.
                        runCatching { engine.stop() }
                        transition(ConnectionState.ERROR, engine.id, result.reason.take(MAX_ERROR_LENGTH))
                    }
                    EngineStartResult.Started -> when (val health = engine.healthCheck()) {
                        EngineHealth.Healthy -> transition(ConnectionState.CONNECTED, engine.id, null)
                        is EngineHealth.Unhealthy -> {
                            runCatching { engine.stop() }
                            transition(ConnectionState.ERROR, engine.id, health.reason.take(MAX_ERROR_LENGTH))
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            runCatching { engine.stop() }
            transition(ConnectionState.ERROR, engine.id, "Connection cancelled")
            throw cancelled
        } catch (_: Exception) {
            runCatching { engine.stop() }
            transition(ConnectionState.ERROR, engine.id, "Engine operation failed")
        }
    }

    suspend fun disconnect(): ConnectionSnapshot = lock.withLock {
        if (current.state == ConnectionState.DISCONNECTED) return@withLock current
        val id = current.engineId
        transition(ConnectionState.DISCONNECTING, id, null)
        val engine = id?.let(engines::get)
        if (engine == null) {
            transition(ConnectionState.ERROR, id, "Active engine is unavailable")
        } else {
            val stopped = runCatching { engine.stop() }
            if (stopped.isSuccess) transition(ConnectionState.DISCONNECTED, null, null)
            else transition(ConnectionState.ERROR, id, "Engine stop failed")
        }
    }

    private fun transition(state: ConnectionState, engineId: String?, message: String?): ConnectionSnapshot {
        current = ConnectionSnapshot(state, engineId, message)
        // UI observers must not break the underlying connection state machine.
        runCatching { publish(current) }
        return current
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 240
    }
}
