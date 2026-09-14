package com.trackmybuds.userservice.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "heartbeat")
class HeartbeatJpaEntity(
    @Id
    var id: UUID,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
)
