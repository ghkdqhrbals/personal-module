package org.ghkdqhrbals.client.domain.user.api

import jakarta.servlet.http.HttpServletResponse
import org.ghkdqhrbals.client.domain.user.service.OAuthService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

//@Controller
//class LoginController(
//    private val oAuthService: OAuthService,
//) {
//    @GetMapping("/login")
//    fun loginPage(model: Model): String {
//        // 타임리프에서 provider 버튼을 노출
//        model.addAttribute("providers", listOf("google", "kakao", "naver"))
//        return "login"
//    }
//
//    @GetMapping("/login/{provider}")
//    fun loginRedirect(@PathVariable provider: String, response: HttpServletResponse) {
//        val url = oAuthService.getAuthorizationUrl(provider)
//        response.sendRedirect(url)
//    }
//}
//
