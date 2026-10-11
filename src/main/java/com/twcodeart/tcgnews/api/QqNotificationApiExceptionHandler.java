package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;

import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = {QqNotificationController.class, QqDeliveryStatusController.class,QqEventController.class})
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
        String id = DiagnosticIds.current();
        log.warn("QQ smoke request rejected: errorId={}, reason={}", id, exception.getReason(), exception);
        return ResponseEntity.status(status(exception.getReason())).body(new ApiError(exception.getMessage(), id));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        String message="Use a supported game, no body/group parameter, and an integer limit between 1 and 5";
        if(exception instanceof IllegalArgumentException && exception.getMessage()!=null
                && exception.getMessage().startsWith("Selected posts exceed five forward packets")) {
            message="Selected posts exceed five forward packets; lower the post limit";
        }
        String id = DiagnosticIds.current();
        log.warn("Invalid QQ preview request: errorId={}", id);
        return ResponseEntity.badRequest().body(new ApiError(message, id));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> storageUnavailable(DataAccessException exception) {
        String id = DiagnosticIds.current();
        log.error("QQ smoke news query failed: errorId={}", id, exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("News storage is unavailable", id));
    }

    public record ApiError(String message, String errorId) { }
}
