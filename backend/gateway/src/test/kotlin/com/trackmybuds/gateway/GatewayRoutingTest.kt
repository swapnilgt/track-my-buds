package com.trackmybuds.gateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    companion object {
        // Started eagerly at class load so its port is available to @DynamicPropertySource.
        private val wireMock = WireMockServer(options().dynamicPort()).also { it.start() }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            wireMock.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("gateway.user-service-uri") { "http://localhost:${wireMock.port()}" }
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Test
    fun `GET ping is routed to the user service`() {
        wireMock.stubFor(
            get(urlEqualTo("/ping")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"service":"user-service","status":"UP"}"""),
            ),
        )

        val response = restTemplate.getForEntity("/ping", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("""{"service":"user-service","status":"UP"}""", response.body)
    }
}
