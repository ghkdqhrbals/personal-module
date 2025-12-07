package org.ghkdqhrbals.client.controller.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/papers/arxiv/categories")
@Tag(name = "arXiv Categories", description = "arXiv 카테고리 조회 API")
class ArxivCategoryController {

    private val categories = mapOf(
        // Computer Science 주요 카테고리
        "cs.AI" to "Artificial Intelligence",
        "cs.LG" to "Machine Learning",
        "cs.CV" to "Computer Vision and Pattern Recognition",
        "cs.CL" to "Computation and Language",
        "cs.NE" to "Neural and Evolutionary Computing",
        "cs.SI" to "Social and Information Networks",
        "cs.IR" to "Information Retrieval",
        "stat.ML" to "Machine Learning (Statistics)",
        // Math/Physics 일부
        "math.OC" to "Optimization and Control",
        "math.ST" to "Statistics Theory",
        "physics.optics" to "Optics"
    )

    @GetMapping
    @Operation(summary = "카테고리 검색", description = "키워드로 arXiv 카테고리 코드/설명을 검색")
    fun search(@RequestParam q: String): ResponseEntity<List<CategoryDto>> {
        val lower = q.lowercase()
        val matches = categories
            .filter { (code, desc) -> code.lowercase().contains(lower) || desc.lowercase().contains(lower) }
            .map { (code, desc) -> CategoryDto(code, desc) }
        return ResponseEntity.ok(matches)
    }

    data class CategoryDto(
        val code: String,
        val description: String
    )
}

