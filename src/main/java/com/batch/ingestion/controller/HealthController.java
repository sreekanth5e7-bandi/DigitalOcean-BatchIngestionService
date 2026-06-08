package com.batch.ingestion.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.batch.ingestion.config.BatchProperties;

@RestController
public class HealthController {

  private final BatchProperties properties;

  public HealthController(BatchProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("status", "ok", "workers", properties.getWorkerPoolSize());
  }
}
