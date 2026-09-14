package com.trackmybuds.userservice.domain.usecase

import java.time.Instant

data class Pong(
    val service: String,
    val status: String,
    val dbCheckedAt: Instant,
)
