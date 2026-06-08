package com.batch.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Central configuration for worker pool size, retry/backoff, and output paths.
 */
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

  private int workerPoolSize = 10;
  private int maxRetries = 5;
  private long initialBackoffMs = 500;
  private long maxBackoffMs = 30_000;
  private String mockApiUrl = "http://localhost:8080/mock/infer";
  private String resultsDir = "data/results";

  public int getWorkerPoolSize() {
    return workerPoolSize;
  }

  public void setWorkerPoolSize(int workerPoolSize) {
    this.workerPoolSize = workerPoolSize;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public long getInitialBackoffMs() {
    return initialBackoffMs;
  }

  public void setInitialBackoffMs(long initialBackoffMs) {
    this.initialBackoffMs = initialBackoffMs;
  }

  public long getMaxBackoffMs() {
    return maxBackoffMs;
  }

  public void setMaxBackoffMs(long maxBackoffMs) {
    this.maxBackoffMs = maxBackoffMs;
  }

  public String getMockApiUrl() {
    return mockApiUrl;
  }

  public void setMockApiUrl(String mockApiUrl) {
    this.mockApiUrl = mockApiUrl;
  }

  public String getResultsDir() {
    return resultsDir;
  }

  public void setResultsDir(String resultsDir) {
    this.resultsDir = resultsDir;
  }
}
