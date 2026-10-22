package dev.arkava.vpn.core

/** An adapter must not report CONNECTED until a real tunnel and proxied connectivity probe succeed. */
interface EngineAdapter {
    val id: String
    suspend fun validate(profile: ConnectionProfile): EngineValidation
    suspend fun start(profile: ConnectionProfile): EngineStartResult
    suspend fun healthCheck(): EngineHealth
    suspend fun stop()
}

data class ConnectionProfile(val id: String, val name: String, val engineId: String, val configJson: String)
sealed interface EngineValidation { data object Valid : EngineValidation; data class Invalid(val reason: String) : EngineValidation }
sealed interface EngineStartResult { data object Started : EngineStartResult; data class Failed(val reason: String) : EngineStartResult }
sealed interface EngineHealth { data object Healthy : EngineHealth; data class Unhealthy(val reason: String) : EngineHealth }
