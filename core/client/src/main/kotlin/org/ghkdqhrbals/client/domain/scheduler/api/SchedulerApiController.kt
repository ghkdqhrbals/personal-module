package org.ghkdqhrbals.client.domain.scheduler.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.quartz.Scheduler
import org.quartz.impl.matchers.GroupMatcher
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Quartz 스케줄러 관리 및 모니터링 API
 */
@RestController
@RequestMapping("/api/scheduler")
@Tag(name = "Scheduler", description = "Quartz 스케줄러 관리 API")
class SchedulerApiController(
    @Parameter(hidden = true) private val scheduler: Scheduler
) {

    private val logger = LoggerFactory.getLogger(SchedulerApiController::class.java)

    /**
     * 스케줄러 상태 조회
     */
    @Operation(summary = "스케줄러 상태 조회", description = "Quartz 스케줄러의 현재 상태와 메타데이터를 조회합니다")
    @GetMapping("/status")
    fun getSchedulerStatus(): ResponseEntity<SchedulerStatusResponse> {
        val status = SchedulerStatusResponse(
            schedulerName = scheduler.schedulerName,
            isStarted = scheduler.isStarted,
            isInStandbyMode = scheduler.isInStandbyMode,
            isShutdown = scheduler.isShutdown,
            metadata = scheduler.metaData.let {
                SchedulerMetadataResponse(
                    schedulerName = it.schedulerName,
                    schedulerInstanceId = it.schedulerInstanceId,
                    schedulerClass = it.schedulerClass.simpleName,
                    version = it.version,
                    numberOfJobsExecuted = it.numberOfJobsExecuted,
                    runningSince = it.runningSince?.let { date ->
                        OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(date.time),
                            ZoneOffset.UTC
                        ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    }
                )
            }
        )
        return ResponseEntity.ok(status)
    }

    /**
     * 등록된 모든 Job 목록 조회
     */
    @Operation(summary = "등록된 Job 목록", description = "스케줄러에 등록된 모든 Job과 Trigger 정보를 조회합니다")
    @GetMapping("/jobs")
    fun getAllJobs(): ResponseEntity<List<JobInfoResponse>> {
        val jobs = mutableListOf<JobInfoResponse>()

        scheduler.jobGroupNames.forEach { groupName ->
            scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName)).forEach { jobKey ->
                val jobDetail = scheduler.getJobDetail(jobKey)
                val triggers = scheduler.getTriggersOfJob(jobKey)

                jobs.add(
                    JobInfoResponse(
                        jobName = jobKey.name,
                        jobGroup = jobKey.group,
                        jobClass = jobDetail.jobClass.simpleName,
                        description = jobDetail.description,
                        triggers = triggers.map { trigger ->
                            TriggerInfoResponse(
                                triggerName = trigger.key.name,
                                triggerGroup = trigger.key.group,
                                triggerState = scheduler.getTriggerState(trigger.key).name,
                                nextFireTime = trigger.nextFireTime?.let {
                                    OffsetDateTime.ofInstant(
                                        java.time.Instant.ofEpochMilli(it.time),
                                        ZoneOffset.UTC
                                    ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                },
                                previousFireTime = trigger.previousFireTime?.let {
                                    OffsetDateTime.ofInstant(
                                        java.time.Instant.ofEpochMilli(it.time),
                                        ZoneOffset.UTC
                                    ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                }
                            )
                        }
                    )
                )
            }
        }

        return ResponseEntity.ok(jobs)
    }

    /**
     * 특정 Job 즉시 실행
     */
    @Operation(summary = "Job 즉시 실행", description = "지정된 Job을 스케줄과 관계없이 즉시 실행합니다")
    @PostMapping("/jobs/{jobGroup}/{jobName}/trigger")
    fun triggerJob(
        @PathVariable jobGroup: String,
        @PathVariable jobName: String
    ): ResponseEntity<Map<String, String>> {
        try {
            val jobKey = org.quartz.JobKey.jobKey(jobName, jobGroup)
            scheduler.triggerJob(jobKey)
            logger.info("Job 수동 실행: {}.{}", jobGroup, jobName)
            return ResponseEntity.ok(mapOf("message" to "Job이 실행되었습니다: $jobGroup.$jobName"))
        } catch (e: Exception) {
            logger.error("Job 실행 실패: {}.{}", jobGroup, jobName, e)
            return ResponseEntity.badRequest().body(mapOf("error" to e.message.orEmpty()))
        }
    }

    /**
     * 스케줄러 일시 정지
     */
    @Operation(summary = "스케줄러 일시 정지", description = "스케줄러를 일시 정지합니다 (Job은 실행되지 않음)")
    @PostMapping("/standby")
    fun standby(): ResponseEntity<Map<String, String>> {
        scheduler.standby()
        logger.info("스케줄러가 일시 정지되었습니다.")
        return ResponseEntity.ok(mapOf("message" to "스케줄러가 일시 정지되었습니다."))
    }

    /**
     * 스케줄러 재시작
     */
    @Operation(summary = "스케줄러 시작/재시작", description = "정지된 스케줄러를 시작하거나 재시작합니다")
    @PostMapping("/start")
    fun start(): ResponseEntity<Map<String, String>> {
        scheduler.start()
        logger.info("스케줄러가 시작/재시작되었습니다.")
        return ResponseEntity.ok(mapOf("message" to "스케줄러가 시작되었습니다."))
    }
}

@Schema(description = "스케줄러 상태 응답")
data class SchedulerStatusResponse(
    @Schema(description = "스케줄러 이름", example = "NotiPaperScheduler")
    val schedulerName: String,

    @Schema(description = "시작 여부", example = "true")
    val isStarted: Boolean,

    @Schema(description = "대기 모드 여부", example = "false")
    val isInStandbyMode: Boolean,

    @Schema(description = "종료 여부", example = "false")
    val isShutdown: Boolean,

    @Schema(description = "스케줄러 메타데이터")
    val metadata: SchedulerMetadataResponse
)

@Schema(description = "스케줄러 메타데이터")
data class SchedulerMetadataResponse(
    @Schema(description = "스케줄러 이름")
    val schedulerName: String,

    @Schema(description = "스케줄러 인스턴스 ID")
    val schedulerInstanceId: String,

    @Schema(description = "스케줄러 클래스")
    val schedulerClass: String,

    @Schema(description = "Quartz 버전")
    val version: String,

    @Schema(description = "실행된 Job 총 개수")
    val numberOfJobsExecuted: Int,

    @Schema(description = "스케줄러 시작 시간")
    val runningSince: String?
)

@Schema(description = "Job 정보")
data class JobInfoResponse(
    @Schema(description = "Job 이름")
    val jobName: String,

    @Schema(description = "Job 그룹")
    val jobGroup: String,

    @Schema(description = "Job 클래스")
    val jobClass: String,

    @Schema(description = "Job 설명")
    val description: String?,

    @Schema(description = "연결된 Trigger 목록")
    val triggers: List<TriggerInfoResponse>
)

@Schema(description = "Trigger 정보")
data class TriggerInfoResponse(
    @Schema(description = "Trigger 이름")
    val triggerName: String,

    @Schema(description = "Trigger 그룹")
    val triggerGroup: String,

    @Schema(description = "Trigger 상태", example = "NORMAL")
    val triggerState: String,

    @Schema(description = "다음 실행 시간")
    val nextFireTime: String?,

    @Schema(description = "이전 실행 시간")
    val previousFireTime: String?
)

