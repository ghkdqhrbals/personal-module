package org.ghkdqhrbals.client.error

class CommonException(
    title: String = "Common Exception",
    detail: String? = null,
    status: org.springframework.http.HttpStatus = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
    ex: Throwable? = null,
) : MyRuntimeException(title, detail, status, ex)