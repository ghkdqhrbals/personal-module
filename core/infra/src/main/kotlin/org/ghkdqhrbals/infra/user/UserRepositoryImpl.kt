package org.ghkdqhrbals.infra.user

import org.ghkdqhrbals.model.user.Gender
import org.ghkdqhrbals.model.user.OauthProviderModel
import org.ghkdqhrbals.model.user.Status
import org.ghkdqhrbals.model.user.UserModel
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val userJdbcRepository: UserJdbcRepository
) : UserRepository {

    override fun findById(id: Long): UserModel? {
        return userJdbcRepository.findById(id).orElse(null)?.toModel().apply {
            assert(this?.id != null ) { "Need Id" }
        }
    }

    override fun findByEmail(email: String): UserModel? {
        return userJdbcRepository.findByEmail(email)?.toModel()
    }

    override fun findByIdentity(identity: String): UserModel? {
        return userJdbcRepository.findByIdentity(identity)?.toModel()
    }

    override fun existsByEmail(email: String): Boolean {
        return userJdbcRepository.existsByEmail(email)
    }

    override fun save(user: UserModel): UserModel {
        val entity = user.toEntity()
        return userJdbcRepository.save(entity).toModel()
    }

    override fun deleteById(id: Long) {
        userJdbcRepository.deleteById(id)
    }

    private fun UserEntity.toModel(): UserModel {
        return UserModel(
            id = this.id,
            identity = this.identity,
            password = this.password,
            name = this.name,
            phoneNumber = this.phoneNumber,
            gender = Gender.valueOf(this.gender.name),
            birth = this.birth,
            email = this.email,
            ci = this.ci,
            status = Status.valueOf(this.status.name),
            certifiedAt = this.certifiedAt,
            note = this.note,
            deletedAt = this.deletedAt,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            activatedAt = this.activatedAt,
            migrationId = this.migrationId,
            oauthProviders = this.oauthProviderEntities.map { it.toModel() }
        )
    }

    private fun OauthProviderEntity.toModel(): OauthProviderModel {
        return OauthProviderModel(
            id = this.id,
            userId = this.userId,
            provider = this.kind.name,
            providerId = this.providerId,
            accessToken = null,
            refreshToken = null,
            createdAt = this.createdAt ?: java.time.OffsetDateTime.now(),
            updatedAt = this.updatedAt
        )
    }

    private fun UserModel.toEntity(): UserEntity {
        val entity = UserEntity.defUser()
        entity.identity = this.identity
        entity.password = this.password
        entity.name = this.name
        entity.phoneNumber = this.phoneNumber
        entity.gender = this.gender!!
        entity.birth = this.birth
        entity.email = this.email
        entity.ci = this.ci
        entity.status = org.ghkdqhrbals.infra.user.Status.valueOf(this.status.name)
        entity.certifiedAt = this.certifiedAt
        entity.note = this.note
        entity.deletedAt = this.deletedAt
        entity.activatedAt = this.activatedAt
        entity.migrationId = this.migrationId
        return entity
    }
}

