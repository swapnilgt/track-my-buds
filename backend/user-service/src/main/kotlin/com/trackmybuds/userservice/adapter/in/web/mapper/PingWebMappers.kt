package com.trackmybuds.userservice.adapter.`in`.web.mapper

import com.trackmybuds.userservice.adapter.`in`.web.dto.PingResponse
import com.trackmybuds.userservice.domain.usecase.Pong

fun Pong.toResponse(): PingResponse =
    PingResponse(service = service, status = status, dbCheckedAt = dbCheckedAt)
