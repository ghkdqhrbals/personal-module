package org.ghkdqhrbals.client.domain.interview.api

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Controller for serving Interview Thymeleaf views
 */
@Controller
@RequestMapping("/interview")
class InterviewViewController {
    
    @GetMapping
    fun interviewPage(model: Model): String {
        model.addAttribute("pageTitle", "AI Interview")
        return "interview"
    }
}
