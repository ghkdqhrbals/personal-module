package com.ghkdqhrbals.mod.controller

import com.ghkdqhrbals.mod.config.log
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
    private val google: GoogleOauthService,

    ) {
    @GetMapping("/login/google")
    fun loginSuccess(response: HttpServletResponse) = google.requestAuthCode(response)

    @GetMapping("/login/oauth2/code/google")
    fun googlecallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        val accessToken = google.getAccessToken(code ?: "")
        log().info("Kakao access token: $accessToken")
        val userInfo = google.getUserInfo(accessToken!!)
        log().info("Kakao user info: $userInfo")
        return "Login successful! User info: $userInfo"
    }

    @GetMapping("/logout/google")
    fun googleLogout(@RequestParam accessToken: String): String {
        google.disconnect(accessToken)
        return "Google logout successful"
    }

    @GetMapping("/login/kakao")
    fun loginSuccess(response: HttpServletResponse) = kakao.requestAuthCode(response)

    @GetMapping("/login/naver")
    fun loginSuccess(response: HttpServletResponse) = naver.requestAuthCode(response)

    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        val accessToken = kakao.getAccessToken(code ?: "")
        log().info("Kakao access token: $accessToken")
        val userInfo = kakao.getUserInfo(accessToken!!)
        log().info("Kakao user info: $userInfo")
        return "Login successful! User info: $userInfo"
    }

    @GetMapping("/logout/kakao")
    fun kakaoLogout(@RequestParam accessToken: String): String {
        kakao.disconnect(accessToken)
        return "Kakao logout successful"
    }

    @GetMapping("/logout/naver")
    fun naverLogout(@RequestParam accessToken: String): String {
        naver.disconnect(accessToken)
        return "Naver logout successful"
    }

    @GetMapping("/login/oauth2/code/naver")
    fun naverCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false, name = "error_description") errorDescription: String?,
        @RequestParam(required = false) state: String?,
    ): String {
        val accessToken = naver.getAccessToken(code ?: "")
        log().info("Naver access token: $accessToken")
        val userInfo = naver.getUserInfo(accessToken!!)
        log().info("Naver user info: $userInfo")
        return "Login successful! User info: $userInfo"
    }
}