package org.ghkdqhrbals.oauth.oauth

import org.ghkdqhrbals.oauth.service.OauthProviderKind

/**
 * configuration for each OAuth provider.
 */
class OauthProviderRegistry private constructor(
    private val registry: Map<OauthProviderKind, OAuthConfig>
) {
    fun get(kind: OauthProviderKind): OAuthConfig? = registry[kind]
    fun require(kind: OauthProviderKind): OAuthConfig =
        get(kind) ?: error("OAuth config not set for provider=$kind")

    fun all(): Map<OauthProviderKind, OAuthConfig> = registry.toMap()
    fun kinds(): Set<OauthProviderKind> = registry.keys
    fun afterPropSet() {

    }

    fun validate() {
        registry.forEach {
            it.value.userInfoPath
        }
    }

    fun validateClient() {

    }

    companion object {
        fun build(block: Builder.() -> Unit): OauthProviderRegistry {
            val b = Builder()
            b.block()
            return OauthProviderRegistry(b.tempRegistry.toMap())
        }
    }

    class Builder {
        internal val tempRegistry = mutableMapOf<OauthProviderKind, OAuthConfig>()
        fun add(kind: OauthProviderKind, block: OAuthConfig.() -> Unit) {
            var mutable = tempRegistry[kind]
            if (mutable == null) {
                mutable = OAuthConfig(kind)
            }
            mutable.apply(block)
            tempRegistry[kind] = mutable.snapshot()
        }
    }
}