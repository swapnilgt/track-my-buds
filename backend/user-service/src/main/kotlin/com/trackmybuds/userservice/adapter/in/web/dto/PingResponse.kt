package com.trackmybuds.userservice.adapter.`in`.web.dto

import java.time.Instant

data class PingResponse(
    val service: String,
    val status: String,
    val dbCheckedAt: Instant,
)
