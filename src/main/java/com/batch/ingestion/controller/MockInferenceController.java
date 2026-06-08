package com.batch.ingestion.controller;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Built-in mock inference endpoint that simulates rate limiting (HTTP 429).
 * Every 4th request returns 429 to exercise client retry/backoff logic.
 */
@RestController
@RequestMapping("/mock")
public class MockInferenceController {

  private static final int RATE_LIMIT_EVERY = 4;
  private final AtomicInteger requestCounter = new AtomicInteger();

  @PostMapping("/infer")
  public ResponseEntity<Map<String, Object>> infer(@RequestBody Map<String, Object> payload) {
    int count = requestCounter.incrementAndGet();
    if (count % RATE_LIMIT_EVERY == 0) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("error", "Too Many Requests", "retry_after", 1));
    }

    String prompt = payload.getOrDefault("prompt", "").toString();
    String truncated = prompt.length() > 80 ? prompt.substring(0, 80) : prompt;
    return ResponseEntity.ok(Map.of(
        "id", payload.getOrDefault("id", ""),
        "response", "Mock inference for: " + truncated,
        "tokens_used", ThreadLocalRandom.current().nextInt(10, 200)));
  }
}
