package com.trackmybuds.userservice.adapter.`in`.web

import com.trackmybuds.userservice.adapter.`in`.web.dto.PingResponse
import com.trackmybuds.userservice.adapter.`in`.web.mapper.toResponse
import com.trackmybuds.userservice.domain.usecase.PingUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
internal class PingController(
    private val pingUseCase: PingUseCase,
) {
    @GetMapping("/ping")
    fun ping(): PingResponse = pingUseCase.ping().toResponse()
}
