package com.ghkdqhrbals.mod.oauth

import com.ghkdqhrbals.mod.service.OauthProviderKind

class OauthProviderRegistry private constructor(
    private val registry: Map<OauthProviderKind, OAuthConfig>
) {
    fun get(kind: OauthProviderKind): OAuthConfig? = registry[kind]
    fun require(kind: OauthProviderKind): OAuthConfig =
        get(kind) ?: error("OAuth config not set for provider=$kind")
    fun all(): Map<OauthProviderKind, OAuthConfig> = registry.toMap()
    fun kinds(): Set<OauthProviderKind> = registry.keys

    companion object {
        fun build(block: Builder.() -> Unit): OauthProviderRegistry {
            val b = Builder()
            b.block()
            return OauthProviderRegistry(b.tempRegistry.toMap())
        }
    }

    class Builder {
        internal val tempRegistry = mutableMapOf<OauthProviderKind, OAuthConfig>()

        // 기존 값 존재 시 복사하여 mutable 로 만든 뒤 block 적용
        fun add(kind: OauthProviderKind, block: MutableOAuthConfig.() -> Unit) {
            val mutable = tempRegistry[kind]
                ?.toMutable()
                ?: MutableOAuthConfig(kind)
            mutable.apply(block)
            tempRegistry[kind] = mutable.snapshot()
        }
    }
}

// 기존 OAuthConfig 를 MutableOAuthConfig 로 변환하여 수정 가능하게 함
private fun OAuthConfig.toMutable(): MutableOAuthConfig = MutableOAuthConfig(kind).also { m ->
    m.clientSecret = clientSecret
    m.redirectUri = redirectUri
    m.tokenPath = tokenPath
    m.codePath = codePath
    m.userInfoPath = userInfoPath
    m.clientId = clientId
    m.scopes.addAll(scopes)
    extra.forEach { (k, v) -> m.putExtra(k, v) }
}
