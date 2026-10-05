package com.twcodeart.tcgnews.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = NewsController.class)
public class NewsApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NewsApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidGame(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid game filter"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> storageUnavailable(DataAccessException exception) {
        log.error("News API storage access failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("News storage is unavailable"));
    }

    public record ApiError(String message) {
    }
}
