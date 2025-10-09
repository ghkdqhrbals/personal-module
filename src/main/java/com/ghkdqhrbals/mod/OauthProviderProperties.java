package com.ghkdqhrbals.mod;

import java.util.List;

/**
 * Represents the configuration details for a single OAuth 2.0 provider.
 *
 * <p>This class defines the essential fields required to integrate an external OAuth provider
 * such as Kakao, Naver, or Google. It is typically bound under the property path:
 * <pre>
 * oauth.providers.&lt;provider&gt;
 * </pre>
 *
 * <p>Example configuration in <code>application.yaml</code>:
 * <pre>
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
 *         - profile
 * </pre>
 *
 * <p>Each field corresponds to a standard element in the OAuth 2.0 authorization flow:
 * <ul>
 *   <li><b>Authorization Endpoint</b> → {@code codePath}</li>
 *   <li><b>Token Endpoint</b> → {@code tokenPath}</li>
 *   <li><b>User Info Endpoint</b> → {@code userInfoPath}</li>
 * </ul>
 *
 * <p>These configurations are typically consumed by your {@code OAuthService} implementation
 * to dynamically build authorization URLs, request access tokens, and retrieve user information.
 */
public class OauthProviderProperties {

    /**
     * The client identifier issued by the OAuth provider.
     *
     * <p>This value uniquely identifies your application to the provider.
     * For Kakao, this corresponds to the <b>REST API Key</b> in the developer console.
     *
     * <p>Example:
     * <pre>client-id: 49676cd76a76d1cbf985faaa88abf5bd</pre>
     */
    private String clientId;

    /**
     * The client secret issued by the OAuth provider.
     *
     * <p>This is used alongside the {@link #clientId} to authenticate your application
     * when exchanging an authorization code for an access token.
     * Some providers (e.g., Google) require it; others (e.g., Kakao) may not.
     *
     * <p><b>Do not expose this value publicly.</b>
     */
    private String clientSecret;

    /**
     * The redirect URI registered with the OAuth provider.
     *
     * <p>After the user grants permission, the authorization server will redirect
     * the browser back to this URI with an authorization code.
     *
     * <p>This must exactly match the redirect URI configured in the provider’s
     * developer console, including path and query parameters.
     *
     * <p>Example:
     * <pre>redirect-uri: http://localhost:8080/login/oauth2/code/kakao</pre>
     */
    private String redirectUri;

    /**
     * The endpoint used to exchange an authorization code for an access token.
     *
     * <p>This corresponds to the <b>token endpoint</b> in the OAuth 2.0 specification.
     * The application sends a POST request to this URL including the {@code code},
     * {@code client_id}, {@code client_secret}, and {@code redirect_uri}.
     *
     * <p>Example:
     * <pre>token-path: https://kauth.kakao.com/oauth/token</pre>
     */
    private String tokenPath;

    /**
     * The endpoint where the user is redirected to grant authorization.
     *
     * <p>This corresponds to the <b>authorization endpoint</b> in the OAuth 2.0 specification.
     * The application should build a URL including parameters such as {@code client_id},
     * {@code redirect_uri}, {@code response_type=code}, and {@code scope}.
     *
     * <p>Example:
     * <pre>code-path: https://kauth.kakao.com/oauth/authorize</pre>
     */
    private String codePath;

    /**
     * The endpoint used to retrieve user profile information after authorization.
     *
     * <p>After obtaining an access token, this URL is called (typically via a GET request)
     * with an {@code Authorization: Bearer <token>} header to fetch the user’s basic info.
     *
     * <p>Example:
     * <pre>user-info-path: https://kapi.kakao.com/v2/user/me</pre>
     */
    private String userInfoPath;

    /**
     * The list of OAuth scopes your application is requesting.
     *
     * <p>Each scope defines a specific permission level or data access requested from the user.
     * Common examples include {@code email}, {@code profile}, and {@code account_email}.
     * The available scopes depend on the provider.
     *
     * <p>Example:
     * <pre>
     * scopes:
     *   - account_email
     *   - profile
     * </pre>
     */
    private List<String> scopes = List.of();

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getTokenPath() { return tokenPath; }
    public void setTokenPath(String tokenPath) { this.tokenPath = tokenPath; }

    public String getCodePath() { return codePath; }
    public void setCodePath(String codePath) { this.codePath = codePath; }

    public String getUserInfoPath() { return userInfoPath; }
    public void setUserInfoPath(String userInfoPath) { this.userInfoPath = userInfoPath; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }
}