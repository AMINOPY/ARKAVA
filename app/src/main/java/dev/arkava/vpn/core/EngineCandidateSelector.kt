package dev.arkava.vpn.core

/** Health class used by the coordinator's engine-selection policy. */
enum class CandidateHealth { HEALTHY, DEGRADED, UNAVAILABLE }

data class EngineCandidate(
    val engineId: String,
    val health: CandidateHealth,
    val latencyMillis: Long
)

/**
 * Selects healthy engines first, then degraded engines, ordered by latency and
 * stable engine ID. Invalid candidates are excluded. This policy is not yet
 * wired into connection startup; it becomes useful when multiple real adapters
 * provide comparable end-to-end health measurements.
 */
object EngineCandidateSelector {
    fun select(candidates: Collection<EngineCandidate>): EngineCandidate? =
        candidates.asSequence()
            .filter { it.engineId.isNotBlank() && it.latencyMillis >= 0 }
            .filter { it.health == CandidateHealth.HEALTHY || it.health == CandidateHealth.DEGRADED }
            .sortedWith(
                compareBy<EngineCandidate> { if (it.health == CandidateHealth.HEALTHY) 0 else 1 }
                    .thenBy { it.latencyMillis }
                    .thenBy { it.engineId }
            )
            .firstOrNull()
}
