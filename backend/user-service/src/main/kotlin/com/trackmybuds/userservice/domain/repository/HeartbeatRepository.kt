package com.trackmybuds.userservice.domain.repository

import com.trackmybuds.userservice.domain.entity.Heartbeat

interface HeartbeatRepository {
    fun findMarker(): Heartbeat?
}
