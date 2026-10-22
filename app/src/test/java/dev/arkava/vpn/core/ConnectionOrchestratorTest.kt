package dev.arkava.vpn.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionOrchestratorTest {
    private val profile = ConnectionProfile("test", "Test", "fake", """{"outbounds":[]}""")

    @Test fun doesNotConnectWhenHealthCheckFails() = runBlocking {
        var stopped = false
        val engine = object : EngineAdapter {
            override val id = "fake"
            override suspend fun validate(profile: ConnectionProfile) = EngineValidation.Valid
            override suspend fun start(profile: ConnectionProfile) = EngineStartResult.Started
            override suspend fun healthCheck() = EngineHealth.Unhealthy("probe failed")
            override suspend fun stop() { stopped = true }
        }
        val result = ConnectionOrchestrator(listOf(engine)).connect(profile)
        assertEquals(ConnectionState.ERROR, result.state)
        assertFalse(result.state == ConnectionState.CONNECTED)
        assertEquals(true, stopped)
    }

    @Test fun failedStartAlwaysAttemptsCleanup() = runBlocking {
        var stopCalled = false
        val engine = object : EngineAdapter {
            override val id = "fake"
            override suspend fun validate(profile: ConnectionProfile) = EngineValidation.Valid
            override suspend fun start(profile: ConnectionProfile): EngineStartResult =
                EngineStartResult.Failed("native engine rejected config")
            override suspend fun healthCheck() = EngineHealth.Healthy
            override suspend fun stop() { stopCalled = true }
        }
        val result = ConnectionOrchestrator(listOf(engine)).connect(profile)
        assertEquals(ConnectionState.ERROR, result.state)
        assertEquals(true, stopCalled)
    }

    @Test fun engineExceptionDoesNotLeaveConnectingState() = runBlocking {
        val engine = object : EngineAdapter {
            override val id = "fake"
            override suspend fun validate(profile: ConnectionProfile): EngineValidation = error("boom")
            override suspend fun start(profile: ConnectionProfile) = EngineStartResult.Started
            override suspend fun healthCheck() = EngineHealth.Healthy
            override suspend fun stop() = Unit
        }
        val result = ConnectionOrchestrator(listOf(engine)).connect(profile)
        assertEquals(ConnectionState.ERROR, result.state)
        assertEquals("Engine operation failed", result.message)
    }

    @Test fun missingEngineReturnsError() = runBlocking {
        val result = ConnectionOrchestrator(emptyList()).connect(profile)
        assertEquals(ConnectionState.ERROR, result.state)
    }
}
