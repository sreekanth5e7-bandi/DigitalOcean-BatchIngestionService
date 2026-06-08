package com.batch.ingestion.model;

import java.time.Instant;

/**
 * Result of a single successful inference call.
 */
public class InferenceResult {

  private String promptId;
  private String prompt;
  private String response;
  private int retries;
  private Instant completedAt = Instant.now();

  public InferenceResult() {}

  public InferenceResult(String promptId, String prompt, String response, int retries) {
    this.promptId = promptId;
    this.prompt = prompt;
    this.response = response;
    this.retries = retries;
    this.completedAt = Instant.now();
  }

  public String getPromptId() {
    return promptId;
  }

  public void setPromptId(String promptId) {
    this.promptId = promptId;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getResponse() {
    return response;
  }

  public void setResponse(String response) {
    this.response = response;
  }

  public int getRetries() {
    return retries;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
