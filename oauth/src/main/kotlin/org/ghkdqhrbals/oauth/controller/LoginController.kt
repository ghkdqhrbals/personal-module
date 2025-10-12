package org.ghkdqhrbals.oauth.controller


import jakarta.servlet.http.HttpServletResponse
import org.ghkdqhrbals.oauth.service.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ConditionalOnClass(LoginControllerModuleMarker::class)
@RestController
class LoginController(private val oAuthClientFactory: OAuthClientFactory) {
    @GetMapping("/login/google")
    fun googleLogin(response: HttpServletResponse) = response.sendRedirect(oAuthClientFactory.get(OauthProviderKind.GOOGLE).buildAuthorizationUrl())
    @GetMapping("/logout/google")
    fun googleLogout(@RequestParam accessToken: String) = oAuthClientFactory.get(OauthProviderKind.GOOGLE).revoke(accessToken)
    @GetMapping("/userinfo/google")
    fun googleUserInfo(@RequestParam accessToken: String) = oAuthClientFactory.get(OauthProviderKind.GOOGLE).fetchUserInfo(accessToken)

    @GetMapping("/login/oauth2/code/google")
    fun googleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        return oAuthClientFactory.get(OauthProviderKind.GOOGLE).exchangeToken(code ?: "") ?: throw IllegalStateException("Failed to get access token")
    }

    @GetMapping("/login/kakao")
    fun kakaoLogin(response: HttpServletResponse) = response.sendRedirect(oAuthClientFactory.get(OauthProviderKind.KAKAO).buildAuthorizationUrl())
    @GetMapping("/logout/kakao")
    fun kakaoLogout(@RequestParam accessToken: String) = oAuthClientFactory.get(OauthProviderKind.KAKAO).revoke(accessToken)
    @GetMapping("/userinfo/kakao")
    fun kakaoUserInfo(@RequestParam accessToken: String) = oAuthClientFactory.get(OauthProviderKind.KAKAO).fetchUserInfo(accessToken)

    @GetMapping("/login/naver")
    fun naverLogin(response: HttpServletResponse) = response.sendRedirect(oAuthClientFactory.get(OauthProviderKind.NAVER).buildAuthorizationUrl())
    @GetMapping("/logout/naver")
    fun naverLogout(@RequestParam accessToken: String) = oAuthClientFactory.get(OauthProviderKind.NAVER).revoke(accessToken)
    @GetMapping("/userinfo/naver")
    fun naverUserInfo(@RequestParam accessToken: String) = oAuthClientFactory.get(OauthProviderKind.NAVER).fetchUserInfo(accessToken)

    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        return oAuthClientFactory.get(OauthProviderKind.KAKAO).exchangeToken(code ?: "") ?: throw IllegalStateException("Failed to get access token")
    }

    @GetMapping("/login/oauth2/code/naver")
    fun naverCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        return oAuthClientFactory.get(OauthProviderKind.NAVER).exchangeToken(code ?: "") ?: throw IllegalStateException("Failed to get access token")
    }
}