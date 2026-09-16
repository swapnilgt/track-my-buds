package com.trackmybuds.userservice.adapter.out.persistence

import com.trackmybuds.userservice.domain.entity.Heartbeat
import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import org.springframework.stereotype.Repository

@Repository
internal class HeartbeatRepositoryImpl(
    private val jpaRepository: HeartbeatJpaRepository,
) : HeartbeatRepository {
    override fun findMarker(): Heartbeat? =
        jpaRepository.findAll().firstOrNull()?.toDomain()
}
