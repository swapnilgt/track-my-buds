package com.trackmybuds.userservice.unit

import com.trackmybuds.userservice.adapter.out.mock.InMemoryHeartbeatRepository
import com.trackmybuds.userservice.domain.usecase.PingUseCaseImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingUseCaseImplTest {

    @Test
    fun `ping returns UP and the service name`() {
        val useCase = PingUseCaseImpl(InMemoryHeartbeatRepository(), "user-service")
        val pong = useCase.ping()
        assertEquals("user-service", pong.service)
        assertEquals("UP", pong.status)
    }
}
