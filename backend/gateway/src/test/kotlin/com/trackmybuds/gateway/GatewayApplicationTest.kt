package com.trackmybuds.gateway

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class GatewayApplicationTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            // No trace export during tests.
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Test
    fun contextLoads() {
    }
}
