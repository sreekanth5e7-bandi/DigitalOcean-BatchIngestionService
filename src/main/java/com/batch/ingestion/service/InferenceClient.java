package com.batch.ingestion.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.batch.ingestion.config.BatchProperties;
import com.batch.ingestion.exception.RateLimitException;
import com.batch.ingestion.model.InferenceResult;
import com.batch.ingestion.model.PromptItem;

/**
 * HTTP client for the mock inference API with exponential-backoff retry on HTTP 429.
 *
 * Retry loop:
 *   1. POST prompt to external endpoint
 *   2. On 429 → sleep with exponential backoff + jitter → retry
 *   3. On success → return {@link InferenceResult}
 *   4. After max retries → throw {@link RateLimitException}
 */
@Service
public class InferenceClient {

  private static final Logger log = LoggerFactory.getLogger(InferenceClient.class);

  private final RestClient restClient;
  private final BatchProperties properties;

  public InferenceClient(BatchProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.create();
  }

  InferenceClient(BatchProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  public InferenceResult infer(PromptItem item) throws InterruptedException {
    int retries = 0;
    int maxRetries = properties.getMaxRetries();

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      ApiResponse apiResponse = restClient.post()
          .uri(properties.getMockApiUrl())
          .contentType(MediaType.APPLICATION_JSON)
          .body(buildPayload(item))
          .exchange((request, response) -> {
            int status = response.getStatusCode().value();
            if (status == 429) {
              return ApiResponse.rateLimited();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
              throw new IllegalStateException(
                  "Inference failed for " + item.getId() + " with status " + status);
            }
            Map<String, Object> body = response.bodyTo(new ParameterizedTypeReference<>() {});
            return ApiResponse.success(body != null ? body : Map.of());
          });

      if (apiResponse.rateLimited) {
        if (attempt >= maxRetries) {
          log.warn("Prompt {}: exhausted {} retries due to rate limiting", item.getId(), maxRetries);
          throw new RateLimitException(item.getId(), attempt + 1);
        }
        long backoff = computeBackoff(attempt);
        retries++;
        log.info("Prompt {}: 429 received, backing off {}ms (attempt {}/{})",
            item.getId(), backoff, attempt + 1, maxRetries);
        Thread.sleep(backoff);
        continue;
      }

      String text = apiResponse.body.getOrDefault("response", "").toString();
      return new InferenceResult(item.getId(), item.getPrompt(), text, retries);
    }

    throw new RateLimitException(item.getId(), maxRetries + 1);
  }

  long computeBackoff(int attempt) {
    double delay = Math.min(
        properties.getInitialBackoffMs() * Math.pow(2, attempt),
        properties.getMaxBackoffMs());
    double jitter = ThreadLocalRandom.current().nextDouble(0, delay * 0.1);
    return (long) (delay + jitter);
  }

  private Map<String, Object> buildPayload(PromptItem item) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", item.getId());
    payload.put("prompt", item.getPrompt());
    return payload;
  }

  private static final class ApiResponse {
    final boolean rateLimited;
    final Map<String, Object> body;

    private ApiResponse(boolean rateLimited, Map<String, Object> body) {
      this.rateLimited = rateLimited;
      this.body = body;
    }

    static ApiResponse rateLimited() {
      return new ApiResponse(true, Map.of());
    }

    static ApiResponse success(Map<String, Object> body) {
      return new ApiResponse(false, body);
    }
  }
}
