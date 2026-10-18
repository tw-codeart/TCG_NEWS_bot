package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.diagnostics.DiagnosticIds;
import com.twcodeart.tcgnews.news.Game;
import com.twcodeart.tcgnews.notification.NewsBackfillService;
import com.twcodeart.tcgnews.notification.qq.QqNotificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/backfill")
public class NewsBackfillController {
    private static final Logger log = LoggerFactory.getLogger(NewsBackfillController.class);
    private final NewsBackfillService service;
    public NewsBackfillController(NewsBackfillService service) { this.service = service; }

    @PostMapping("/preview")
    public NewsBackfillService.Preview preview(@RequestParam Game game, @RequestParam(required=false) Integer days,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam Map<String,String> parameters, @RequestBody(required=false) String body) {
        validate(parameters, body, Set.of("game", "days", "from", "through"));
        return service.preview(game, days, from, through);
    }

    @PostMapping("/confirm")
    public NewsBackfillService.Confirmation confirm(@RequestParam UUID previewId,
            @RequestParam Map<String,String> parameters, @RequestBody(required=false) String body) {
        validate(parameters, body, Set.of("previewId"));
        return service.confirm(previewId);
    }

    private void validate(Map<String,String> parameters, String body, Set<String> allowed) {
        if (!allowed.containsAll(parameters.keySet()) || body != null && !body.isBlank())
            throw new IllegalArgumentException("Only documented backfill parameters are allowed; no body or group override");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> invalid(Exception exception) {
        return error(400, exception instanceof MethodArgumentTypeMismatchException ? "Invalid game, date or preview ID" : exception.getMessage(), exception);
    }
    @ExceptionHandler(QqNotificationException.class)
    public ResponseEntity<Error> configuration(QqNotificationException exception) {
        return error(QqNotificationApiExceptionHandler.status(exception.getReason()).value(), exception.getMessage(), exception);
    }
    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<Error> storage(Exception exception) { return error(503, "Backfill storage is unavailable; check the error ID", exception); }
    private ResponseEntity<Error> error(int status, String message, Exception exception) {
        String id = DiagnosticIds.current();
        log.warn("Backfill request failed: errorId={}", id, exception);
        return ResponseEntity.status(status).body(new Error(message, id));
    }
    public record Error(String message, String errorId) { }
}
