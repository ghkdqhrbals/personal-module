package org.ghkdqhrbals.client.controller.stream

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.domain.stream.StreamConfigManager
import org.springframework.web.bind.annotation.*

/**
 * Redis Stream 설정 관리 API
 */
@RestController
@RequestMapping("/api/stream/config")
@Tag(name = "Stream Config", description = "Redis Stream 설정 관리")
class StreamConfigController(
    private val manager: StreamConfigManager,
) {
    /**
     * 모든 설정 조회
     */
    @GetMapping("")
    @Operation(summary = "모든 Stream 설정 조회", description = "현재 적용된 모든 설정값 조회")
    fun getAllConfigs() = manager.cachedSummaryConfig

    /**
     * MAX_LEN 업데이트
     */
    @PutMapping("/maxlen")
    @Operation(summary = "MAX_LEN 업데이트", description = "Redis Stream의 최대 길이를 동적으로 변경")
    fun updateMaxLen(
        @RequestParam
        @Parameter(description = "새로운 MAX_LEN 값 (0 이상)")
        value: Long
    ) = manager.update(value)
}