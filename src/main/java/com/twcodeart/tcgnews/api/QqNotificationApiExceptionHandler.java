package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = QqNotificationController.class)
public class QqNotificationApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QqNotificationApiExceptionHandler.class);

    static HttpStatus status(QqNotificationException.Reason reason) {
        return switch (reason) {
            case CONFIGURATION, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case API_FAILURE -> HttpStatus.BAD_GATEWAY;
            case FORMATTING_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(QqNotificationException.class)
    public ResponseEntity<ApiError> unavailable(QqNotificationException exception) {
        log.warn("QQ smoke request rejected: reason={}", exception.getReason());
        return ResponseEntity.status(status(exception.getReason())).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("Use no body or group parameter; limit must be an integer between 1 and 5"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> storageUnavailable(DataAccessException exception) {
        log.error("QQ smoke news query failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("News storage is unavailable"));
    }

    public record ApiError(String message) { }
}
