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

// Verifies a trailing slash in gateway.user-service-uri (e.g. an operator setting
// USER_SERVICE_URI=http://host:8081/) does NOT break routing.
//
// A CodeAnt review bot flagged this as a possible "//ping" double-slash bug, assuming
// HandlerFunctions.http(String) does base-string + path-string concatenation. Checked
// against the actual spring-cloud-gateway v4.2.0 source: ProxyExchangeHandlerFunction
// builds the outbound URI via `UriComponentsBuilder.fromUri(serverRequest.uri())`
// (the INCOMING request's own URI, path included) and only replaces scheme/host/port
// from the configured base — the base URI's path (and thus any trailing slash) is
// never read. So the claimed bug does not reproduce with this dependency version.
// This test pins that verified behavior as a regression guard in case a future
// gateway-library upgrade changes the join strategy.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTrailingSlashTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    companion object {
        private val wireMock = WireMockServer(options().dynamicPort()).also { it.start() }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            wireMock.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            // Trailing slash, deliberately — this is the misconfiguration being guarded against.
            registry.add("gateway.user-service-uri") { "http://localhost:${wireMock.port()}/" }
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Test
    fun `GET ping is routed correctly even when user-service-uri has a trailing slash`() {
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
