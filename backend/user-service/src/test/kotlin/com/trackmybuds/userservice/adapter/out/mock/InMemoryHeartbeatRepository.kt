package com.trackmybuds.userservice.adapter.out.mock

import com.trackmybuds.userservice.domain.entity.Heartbeat
import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import java.time.Instant
import java.util.UUID

class InMemoryHeartbeatRepository(
    private val marker: Heartbeat? = Heartbeat(UUID.randomUUID(), Instant.now()),
) : HeartbeatRepository {
    override fun findMarker(): Heartbeat? = marker
}
