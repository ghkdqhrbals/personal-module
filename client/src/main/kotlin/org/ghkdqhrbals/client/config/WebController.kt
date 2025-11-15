package org.ghkdqhrbals.client.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class WebController {

    @GetMapping("/")
    fun index(): String {
        return "redirect:/paper-search"
    }

    @GetMapping("/paper-search")
    fun paperSearch(): String {
        return "paper-search"
    }
}

