package com.trackmybuds.userservice.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HeartbeatJpaRepository : JpaRepository<HeartbeatJpaEntity, UUID>
