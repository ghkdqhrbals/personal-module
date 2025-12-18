package org.ghkdqhrbals.infra.user

import org.ghkdqhrbals.model.user.UserModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserJdbcRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun findByIdentity(identity: String): UserEntity?
}

interface UserRepository {
    fun findById(id: Long): UserModel?
    fun findByEmail(email: String): UserModel?
    fun findByIdentity(identity: String): UserModel?
    fun existsByEmail(email: String): Boolean
    fun save(user: UserModel): UserModel
    fun deleteById(id: Long)
}