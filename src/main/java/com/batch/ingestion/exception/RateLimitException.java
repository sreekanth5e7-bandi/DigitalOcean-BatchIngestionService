package com.batch.ingestion.exception;

/**
 * Thrown when all retry attempts are exhausted due to HTTP 429 rate limiting.
 */
public class RateLimitException extends RuntimeException {

  private final String promptId;
  private final int attempts;

  public RateLimitException(String promptId, int attempts) {
    super("Rate limit exceeded for prompt " + promptId + " after " + attempts + " attempts");
    this.promptId = promptId;
    this.attempts = attempts;
  }

  public String getPromptId() {
    return promptId;
  }

  public int getAttempts() {
    return attempts;
  }
}
