package com.batch.ingestion.model;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * A single prompt to be sent to the inference endpoint.
 */
public class PromptItem {

  @NotBlank
  private String id;

  @NotBlank
  private String prompt;

  private Map<String, Object> metadata = new HashMap<>();

  public PromptItem() {}

  public PromptItem(String id, String prompt) {
    this.id = id;
    this.prompt = prompt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }
}
