package com.trackmybuds.userservice.unit

import com.trackmybuds.userservice.adapter.out.mock.InMemoryHeartbeatRepository
import com.trackmybuds.userservice.domain.usecase.PingUseCaseImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingUseCaseImplTest {

    @Test
    fun `ping returns UP and service name when marker present`() {
        val useCase = PingUseCaseImpl(InMemoryHeartbeatRepository(), "user-service")
        val pong = useCase.ping()
        assertEquals("user-service", pong.service)
        assertEquals("UP", pong.status)
    }

    @Test
    fun `ping returns DOWN when marker absent`() {
        val useCase = PingUseCaseImpl(InMemoryHeartbeatRepository(marker = null), "user-service")
        assertEquals("DOWN", useCase.ping().status)
    }
}
