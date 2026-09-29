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
    // LookupProxyExchangeHandlerFunction with the URI supplied up front,
    // proxying the incoming request (path/method/body preserved) to
    // userServiceUri.
    @Bean
    fun pingRoute(
        @Value("\${gateway.user-service-uri}") userServiceUri: String,
    ): RouterFunction<ServerResponse> =
        route("ping")
            .GET("/ping", http(userServiceUri))
            .build()
}
