package org.ghkdqhrbals.client.domain.user.service

import org.ghkdqhrbals.infra.user.UserEntity
import org.ghkdqhrbals.infra.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    fun getUserById(userId: Long): UserEntity {
        return userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다. ID: $userId") }
    }

    fun getUserByEmail(email: String): UserEntity? {
        return userRepository.findByEmail(email)
    }

    fun existsByEmail(email: String): Boolean {
        return userRepository.existsByEmail(email)
    }
}

