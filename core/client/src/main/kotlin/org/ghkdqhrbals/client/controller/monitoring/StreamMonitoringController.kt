package org.ghkdqhrbals.client.controller.monitoring

import org.ghkdqhrbals.client.domain.monitoring.RedisStreamMonitoringService
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.Parameter
import org.ghkdqhrbals.model.monitoring.StreamMessageResponse

@RestController
@RequestMapping("/monitoring/streams")
@Tag(name = "Stream Monitoring", description = "Redis Stream 모니터링 관련 API")
class StreamMonitoringController(
    private val streamMonitoringService: RedisStreamMonitoringService
) {
    @GetMapping("/info/{stream}")
    @Operation(summary = "Stream 정보 조회", description = "Redis Stream의 상세 정보를 조회합니다")
    fun getStreamInfo(
        @PathVariable
        @Parameter(description = "조회할 Stream 이름")
        stream: String,
    ) = streamMonitoringService.getStreamInfo(stream)

    @GetMapping("/groups/{stream}")
    @Operation(summary = "Stream 그룹 정보 조회", description = "Redis Stream의 그룹 정보를 조회합니다")
    fun getStreamGroups(
        @PathVariable
        @Parameter(description = "조회할 Stream 이름")
        stream: String,
    ) = streamMonitoringService.getStreamGroups(stream)

    @GetMapping("/messages/{stream}")
    @Operation(
        summary = "Stream 메시지 페이지네이션 조회",
        description = """
            Redis Stream의 메시지를 페이지네이션으로 조회합니다.
            
            - cursor가 null이면 처음부터 조회합니다 ("-"와 동일)
            - 응답의 nextCursor를 다음 요청의 cursor로 사용하면 다음 페이지를 조회할 수 있습니다
            - hasMore가 true이면 다음 페이지가 있다는 의미입니다
            
            예시:
            1. GET /messages/mystream?pageSize=10 (첫 페이지)
            2. GET /messages/mystream?cursor=1234567890-0&pageSize=10 (다음 페이지)
        """
    )
    fun getMessage(
        @PathVariable
        @Parameter(description = "조회할 Stream 이름")
        stream: String,
        @RequestParam(required = false)
        @Parameter(description = "페이지네이션 커서 (null이면 처음부터 조회)")
        cursor: String? = null,
        @RequestParam(defaultValue = "10")
        @Parameter(description = "페이지당 메시지 수 (1-100, 기본값: 10)")
        pageSize: Int = 10
    ): StreamMessageResponse {
        return streamMonitoringService.getMessagesWithPagination(stream, cursor, pageSize)
    }


    @GetMapping("/message/{stream}/{id}")
    @Operation(
        summary = "단일 메시지 조회",
        description = """
            Redis Stream에서 특정 Message ID의 메시지를 조회합니다.
            
            Message ID 형식: "timestamp-sequence" (예: 1735541234567-0)
            
            응답에는 메시지 ID와 모든 필드가 포함됩니다.
        """
    )
    fun getMsg(
        @PathVariable
        @Parameter(description = "조회할 Stream 이름", example = "summary:1")
        stream: String,
        @PathVariable(required = true)
        @Parameter(description = "메시지 ID (timestamp-sequence 형식)", example = "1735541234567-0")
        id: String,
    ) = streamMonitoringService.getMessage(stream, id)
}