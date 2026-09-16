package com.trackmybuds.userservice.domain.usecase

import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
internal class PingUseCaseImpl(
    private val heartbeatRepository: HeartbeatRepository,
    @Value("\${spring.application.name}") private val serviceName: String,
) : PingUseCase {
    override fun ping(): Pong {
        val status = if (heartbeatRepository.findMarker() != null) "UP" else "DOWN"
        return Pong(service = serviceName, status = status, dbCheckedAt = Instant.now())
    }
}
