//package org.ghkdqhrbals.client.error
//
//import org.ghkdqhrbals.client.config.log.logger
//import org.springframework.http.HttpStatus
//import org.springframework.http.ResponseEntity
//import org.springframework.web.bind.annotation.ExceptionHandler
//import org.springframework.web.bind.annotation.RestControllerAdvice
//
//@RestControllerAdvice
//class CommonGlobalExceptionHandler {
//
//    @ExceptionHandler(value = [MyRuntimeException::class])
//    fun notFoundException(exception: MyRuntimeException): ResponseEntity<ExceptionDto> {
//        logger().error("exception", exception)
//
//        return ResponseEntity<ExceptionDto>(
//            ExceptionDto(exception.detail(), exception.code()),
//            exception.status(),
//        )
//    }
//
//    @ExceptionHandler(value = [Throwable::class])
//    fun handleThrowable(exception: Throwable): ResponseEntity<ExceptionDto> {
//        logger().error("Unexpected exception", exception)
//        val fxException = CommonException(ex=exception)
//
//        return ResponseEntity<ExceptionDto>(
//            ExceptionDto(fxException.detail(), fxException.code()),
//            HttpStatus.INTERNAL_SERVER_ERROR,
//        )
//    }
//}
//
