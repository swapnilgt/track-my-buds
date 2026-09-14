package com.trackmybuds.userservice.integration

import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class PingIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres")
        ).withDatabaseName("user_service")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Autowired
    lateinit var heartbeatRepository: HeartbeatRepository

    @Test
    fun contextLoads() {
    }

    @Test
    fun `findMarker returns the seeded heartbeat row`() {
        val marker = heartbeatRepository.findMarker()
        assertNotNull(marker)
    }
}
