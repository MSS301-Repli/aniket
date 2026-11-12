package com.animallocket.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Trả lỗi rõ ràng khi circuit breaker mở. Discovery là fail-soft nên trả danh sách rỗng. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/default")
    public ResponseEntity<Map<String, Object>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("status", 503, "code", "SERVICE_UNAVAILABLE",
                        "detail", "Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau"));
    }

    @RequestMapping("/fallback/discovery")
    public ResponseEntity<Map<String, Object>> discoveryFallback() {
        return ResponseEntity.ok(Map.of("cards", List.of(), "degraded", true));
    }
}
