package org.ghkdqhrbals.client.domain.user.api

import org.ghkdqhrbals.client.domain.user.service.OauthSignupService
import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class OauthSignupController(
    private val oauthSignupService: OauthSignupService
) {
    data class CodeSignupRequest(
        val provider: OauthProviderKind,
        val code: String,
    )

    data class AccessTokenSignupRequest(
        val provider: OauthProviderKind,
        val accessToken: String,
    )

    @PostMapping("/oauth/code")
    fun signupWithCode(@RequestBody req: CodeSignupRequest): ResponseEntity<OauthSignupService.SignupResult> {
        val res = oauthSignupService.signupWithAuthorizationCode(req.provider, req.code)
        return ResponseEntity.ok(res)
    }

    @PostMapping("/oauth/token")
    fun signupWithAccessToken(@RequestBody req: AccessTokenSignupRequest): ResponseEntity<OauthSignupService.SignupResult> {
        val res = oauthSignupService.signupWithAccessToken(req.provider, req.accessToken)
        return ResponseEntity.ok(res)
    }
}

