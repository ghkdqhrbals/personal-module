package org.ghkdqhrbals.client.controller

import jakarta.servlet.http.HttpServletResponse
import org.ghkdqhrbals.client.domain.user.service.OAuthService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class UserLoginController(
    private val oAuthService: OAuthService,
) {
    @GetMapping("/login")
    fun loginPage(model: Model): String {
        model.addAttribute("providers", listOf("google", "kakao", "naver"))
        return "login"
    }

    @GetMapping("/login/{provider}")
    fun loginWithProvider(@PathVariable provider: String, response: HttpServletResponse) {
        val authUrl = oAuthService.getAuthorizationUrl(provider)
        response.sendRedirect(authUrl)
    }
}