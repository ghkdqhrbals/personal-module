package com.ghkdqhrbals.mod.config

import org.springframework.boot.context.properties.ConfigurationProperties


/**
 * Represents the detailed configuration for a single OAuth provider.
 *
 * This class is typically used under the `oauth.providers.<provider>` property prefix.
 * Each field corresponds to the credentials and endpoints required for OAuth 2.0 integration.
 *
 * Example (application.yaml):
 * ```
 * oauth:
 *   providers:
 *     kakao:
 *       client-id: your-client-id
 *       client-secret: your-client-secret
 *       redirect-uri: http://localhost:8080/login/oauth2/code/kakao
 *       token-path: https://kauth.kakao.com/oauth/token
 *       code-path: https://kauth.kakao.com/oauth/authorize
 *       user-info-path: https://kapi.kakao.com/v2/user/me
 *       scopes:
 *         - account_email
 * ```
 */
data class OauthProviderProperties(

    /**
     * The OAuth client ID issued by the provider.
     *
     * For example, in Kakao Developer Console, this corresponds to the **REST API Key**.
     */
    var clientId: String = "",

    /**
     * The OAuth client secret issued by the provider.
     *
     * This value is used to authenticate your application when exchanging
     * authorization codes for access tokens.
     */
    var clientSecret: String = "",

    /**
     * The redirect URI registered with the provider.
     *
     * This URI must exactly match the one configured in the provider’s developer console.
     * It is where the authorization server will send the authorization code.
     *
     * Example:
     * ```
     * http://localhost:8080/login/oauth2/code/kakao
     * ```
     */
    var redirectUri: String = "",

    /**
     * The OAuth 2.0 token endpoint URL.
     *
     * Used to exchange the authorization code for an access token.
     * Example:
     * ```
     * https://kauth.kakao.com/oauth/token
     * ```
     */
    var tokenPath: String? = "",

    /**
     * The OAuth 2.0 authorization endpoint URL.
     *
     * This is the endpoint that users are redirected to in order to grant authorization.
     * Example:
     * ```
     * https://kauth.kakao.com/oauth/authorize
     * ```
     */
    var codePath: String? = "",

    /**
     * The OAuth 2.0 user information endpoint URL.
     *
     * After obtaining an access token, this endpoint is used to fetch the user's profile data.
     * Example:
     * ```
     * https://kapi.kakao.com/v2/user/me
     * ```
     */
    var userInfoPath: String? = "",

    /**
     * The list of permission scopes requested from the provider.
     *
     * These define what user data and permissions your application is requesting access to.
     *
     * Example:
     * ```
     * - account_email
     * - profile
     * - gender
     * ```
     */
    var scopes: List<String> = emptyList(),
)

@ConfigurationProperties(prefix = "oauth")
data class OauthProvidersProperties(
    var providers: Map<String, OauthProviderProperties> = emptyMap()
)