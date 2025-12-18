package org.ghkdqhrbals.client.domain.user.api

import org.ghkdqhrbals.infra.user.UserEntity
import org.ghkdqhrbals.client.domain.user.service.UserService
import org.ghkdqhrbals.model.user.UserModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserApiController(
    private val userService: UserService
) {
    @GetMapping("/{userId}")
    fun getUserInfo(@PathVariable userId: Long): ResponseEntity<UserResponse> {
        val user = userService.getUserById(userId)
        return ResponseEntity.ok(UserResponse.from(user))
    }

    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<UserResponse> {
        // TODO: Security Context에서 현재 사용자 정보 가져오기
        // 현재는 임시로 ID 1 사용
        val user = userService.getUserById(1L)
        return ResponseEntity.ok(UserResponse.from(user))
    }
}

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String?,
    val phoneNumber: String,
    val gender: String,
    val age: Long,
    val status: String
) {
    companion object {
        fun from(user: UserModel): UserResponse {
            return UserResponse(
                id = user.id!!,
                name = user.name,
                email = user.email,
                phoneNumber = user.phoneNumber!!,
                gender = user.gender?.name?:"UNKNOWN",
                age = 0,
                status = user.status.name
            )
        }
    }
}


