package org.ghkdqhrbals.client.config

import org.ghkdqhrbals.client.config.interceptor.ApiCallInterceptor
import org.ghkdqhrbals.client.config.log.ExternalApiLog
import org.ghkdqhrbals.client.config.log.logger
import org.springframework.stereotype.Component
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import java.nio.charset.StandardCharsets

@Component("consoleLogApiCallInterceptor")
class ConsoleLogApiCallInterceptorImpl() : ApiCallInterceptor {

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val response = execution.execute(request, body)

        logger().info(
            ExternalApiLog(
                uri = request.uri.toString(),
                method = request.method.name(),
                requestBody = String(body, StandardCharsets.UTF_8),
                responseBody = String(
                    response.body.readAllBytes(),
                    StandardCharsets.UTF_8,
                ),
                statusCode = response.statusCode.value(),
                headers = null,
                mdc = null,
            ).toJson(),
        )

        return response
    }
}
