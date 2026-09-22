package com.trackmybuds.userservice.domain.entity

import java.time.Instant
import java.util.UUID

data class Heartbeat(
    val id: UUID,
    val createdAt: Instant,
)
