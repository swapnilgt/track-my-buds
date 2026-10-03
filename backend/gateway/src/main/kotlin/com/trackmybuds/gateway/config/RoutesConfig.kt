package com.trackmybuds.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

@Configuration
class RoutesConfig {

    // Forwards GET /ping to ${gateway.user-service-uri}/ping.
    //
    // Adjusted from the brief for the resolved dependency: this module pulls
    // spring-cloud-gateway-server-mvc 4.2.0 (not 4.2.7), whose
    // BeforeFilterFunctions has no uri(String) before-filter — that helper
    // does not exist in this jar (confirmed via javap against the resolved
    // artifact). The no-arg http() handler relies on such a filter having set
    // the target URI request attribute first, so it cannot be used alone.
    // HandlerFunctions.http(String) is present in 4.2.0 and is the direct
    // equivalent for a single static target: it constructs the same
    // LookupProxyExchangeHandlerFunction with the URI supplied up front.
    // The actual proxying (in ProxyExchangeHandlerFunction) builds the outbound
    // URI from the INCOMING request's own URI via UriComponentsBuilder, only
    // replacing scheme/host/port from userServiceUri — the path/query of the
    // incoming request is preserved as-is, and userServiceUri's own path (if
    // any) is never read. See GatewayRoutingTrailingSlashTest for the verified
    // consequence: a trailing slash on userServiceUri is harmless.
    @Bean
    fun pingRoute(
        @Value("\${gateway.user-service-uri}") userServiceUri: String,
    ): RouterFunction<ServerResponse> =
        route("ping")
            .GET("/ping", http(userServiceUri))
            .build()
}
