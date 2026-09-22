package com.trackmybuds.userservice.adapter.out.persistence

import com.trackmybuds.userservice.domain.entity.Heartbeat

internal fun HeartbeatJpaEntity.toDomain(): Heartbeat =
    Heartbeat(id = id, createdAt = createdAt)
