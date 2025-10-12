package com.ghkdqhrbals.mod.controller

import com.ghkdqhrbals.mod.service.GoogleOauthService
import com.ghkdqhrbals.mod.service.KakaoOauthService
import com.ghkdqhrbals.mod.service.NaverOauthService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ConditionalOnClass(LoginControllerModuleMarker::class)
@RestController
class LoginController(
    private val kakao: KakaoOauthService,
    private val naver: NaverOauthService,
    private val google: GoogleOauthService) {

    @GetMapping("/login/google")
    fun googleLogin(response: HttpServletResponse) = response.sendRedirect(google.buildAuthorizationUrl())
    @GetMapping("/logout/google")
    fun googleLogout(@RequestParam accessToken: String) = google.revoke(accessToken)
    @GetMapping("/userinfo/google")
    fun googleUserInfo(@RequestParam accessToken: String) = google.fetchUserInfo(accessToken)

    @GetMapping("/login/oauth2/code/google")
    fun googleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        return google.exchangeToken(code ?: "") ?: throw IllegalStateException("Failed to get access token")
    }

    @GetMapping("/login/kakao")
    fun kakaoLogin(response: HttpServletResponse) = response.sendRedirect(kakao.buildAuthorizationUrl())
    @GetMapping("/logout/kakao")
    fun kakaoLogout(@RequestParam accessToken: String) = kakao.revoke(accessToken)
    @GetMapping("/userinfo/kakao")
    fun kakaoUserInfo(@RequestParam accessToken: String) = kakao.fetchUserInfo(accessToken)

    @GetMapping("/login/naver")
    fun naverLogin(response: HttpServletResponse) = response.sendRedirect(naver.buildAuthorizationUrl())
    @GetMapping("/logout/naver")
    fun naverLogout(@RequestParam accessToken: String) = naver.revoke(accessToken)
    @GetMapping("/userinfo/naver")
    fun naverUserInfo(@RequestParam accessToken: String) = naver.fetchUserInfo(accessToken)

    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        return kakao.exchangeToken(code ?: "") ?: throw IllegalStateException("Failed to get access token")
    }

    @GetMapping("/login/oauth2/code/naver")
    fun naverCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        return naver.exchangeToken(code ?: "") ?: throw IllegalStateException("Failed to get access token")
    }
}