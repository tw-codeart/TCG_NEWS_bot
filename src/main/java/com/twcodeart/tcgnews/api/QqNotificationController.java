package com.twcodeart.tcgnews.api;

import com.twcodeart.tcgnews.notification.qq.QqNotificationTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/notifications")
public class QqNotificationController {

    private final QqNotificationTestService service;

    public QqNotificationController(QqNotificationTestService service) {
        this.service = service;
    }

    @PostMapping("/test")
    public ResponseEntity<QqNotificationTestService.Result> test(@RequestParam Map<String, String> parameters,
                                                                @RequestBody(required = false) String body) {
        validateRequest(parameters, body, Set.of());
        return response(service.sendTest());
    }

    @PostMapping("/news")
    public ResponseEntity<QqNotificationTestService.Result> storedNews(@RequestParam(defaultValue = "3") int limit,
            @RequestParam Map<String, String> parameters, @RequestBody(required = false) String body) {
        validateRequest(parameters, body, Set.of("limit"));
        return response(service.sendStoredNews(limit));
    }

    private void validateRequest(Map<String, String> parameters, String body, Set<String> allowed) {
        if (!allowed.containsAll(parameters.keySet()) || (body != null && !body.isBlank())) {
            throw new IllegalArgumentException("Only the documented limit parameter is allowed; request bodies are not accepted");
        }
    }

    private ResponseEntity<QqNotificationTestService.Result> response(QqNotificationTestService.Result result) {
        return ResponseEntity.status(result.errors().isEmpty() ? org.springframework.http.HttpStatus.OK
                : QqNotificationApiExceptionHandler.status(result.errors().get(0).reason())).body(result);
    }
}
