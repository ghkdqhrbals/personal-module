package com.ghkdqhrbals.mod.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping
class LoginTestController {

    @GetMapping("/oauth2")
    fun oauthPage(): String = "oauth" // templates/oauth.html
}