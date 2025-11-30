package org.ghkdqhrbals.client.error

enum class ErrorCode(val code: String, val description: String = "") {
    NOT_FOUND("0001", "요청하신 리소스를 찾을 수 없습니다."),
    INVALID_PARAMETER("0002", "잘못된 요청 파라미터입니다."),
    ILLEGAL_STATE("0003", "잘못된 상태입니다."),
    ACCESS_DENIED("0004", "접근이 거부되었습니다."),
    NOT_ALLOWED("0005", "허용되지 않은 요청입니다."),
    TOO_MANY_REQUESTS("0006", "요청이 너무 많습니다."),
    CONFLICT("0007", "충돌이 발생했습니다."),
    TIMEOUT("0008", "요청 시간이 초과되었습니다."),
    UPDATE_REQUIRED("9998", "업데이트가 필요합니다."),

    // 일반적으로 사용되는 오류 코드. 기타오류.
    UNEXPECTED("9999", "예기치 못한 오류가 발생했습니다."),
    ;
}