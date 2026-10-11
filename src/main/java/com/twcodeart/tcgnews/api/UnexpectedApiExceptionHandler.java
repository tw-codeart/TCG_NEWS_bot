package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {NewsController.class, QqNotificationController.class, ApiStatusController.class,
        QqDeliveryStatusController.class,QqEventController.class})
public class UnexpectedApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(UnexpectedApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        String id = DiagnosticIds.current();
        log.error("Unexpected API failure: errorId={}", id, exception);
        return ResponseEntity.internalServerError().body(new ApiError("Unexpected server error; check logs/errors.log", id));
    }

    public record ApiError(String message, String errorId) { }
}
