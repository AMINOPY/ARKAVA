package dev.arkava.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineCandidateSelectorTest {
    @Test
    fun healthyCandidateBeatsFasterDegradedCandidate() {
        val selected = EngineCandidateSelector.select(
            listOf(
                EngineCandidate("degraded-fast", CandidateHealth.DEGRADED, 2),
                EngineCandidate("healthy-slower", CandidateHealth.HEALTHY, 80)
            )
        )

        assertEquals("healthy-slower", selected?.engineId)
    }

    @Test
    fun lowestLatencyHealthyCandidateWins() {
        val selected = EngineCandidateSelector.select(
            listOf(
                EngineCandidate("beta", CandidateHealth.HEALTHY, 20),
                EngineCandidate("alpha", CandidateHealth.HEALTHY, 20),
                EngineCandidate("fastest", CandidateHealth.HEALTHY, 5)
            )
        )

        assertEquals("fastest", selected?.engineId)
    }

    @Test
    fun tiesAreDeterministicByEngineId() {
        val selected = EngineCandidateSelector.select(
            listOf(
                EngineCandidate("zeta", CandidateHealth.HEALTHY, 10),
                EngineCandidate("alpha", CandidateHealth.HEALTHY, 10)
            )
        )

        assertEquals("alpha", selected?.engineId)
    }

    @Test
    fun unavailableAndInvalidCandidatesAreIgnored() {
        val selected = EngineCandidateSelector.select(
            listOf(
                EngineCandidate("offline", CandidateHealth.UNAVAILABLE, 1),
                EngineCandidate("negative", CandidateHealth.HEALTHY, -1),
                EngineCandidate("", CandidateHealth.HEALTHY, 1)
            )
        )

        assertNull(selected)
    }
}
