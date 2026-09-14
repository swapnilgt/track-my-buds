package com.trackmybuds.userservice.adapter.out.persistence

import com.trackmybuds.userservice.domain.entity.Heartbeat

fun HeartbeatJpaEntity.toDomain(): Heartbeat =
    Heartbeat(id = id, createdAt = createdAt)
