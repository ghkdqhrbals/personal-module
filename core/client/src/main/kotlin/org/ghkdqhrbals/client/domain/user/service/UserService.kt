package org.ghkdqhrbals.client.domain.user.service

import org.ghkdqhrbals.infra.user.UserEntity
import org.ghkdqhrbals.infra.user.UserRepository
import org.ghkdqhrbals.model.user.UserModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    fun getUserById(userId: Long): UserModel {
        return userRepository.findById(userId)?: throw IllegalArgumentException("사용자를 찾을 수 없습니다. ID: $userId")
    }

    fun getUserByEmail(email: String): UserModel? {
        return userRepository.findByEmail(email)
    }

    fun existsByEmail(email: String): Boolean {
        return userRepository.existsByEmail(email)
    }
}

