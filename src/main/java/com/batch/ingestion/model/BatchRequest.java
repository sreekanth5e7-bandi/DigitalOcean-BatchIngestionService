package com.batch.ingestion.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Incoming batch payload — a JSON array of prompts.
 */
public class BatchRequest {

  @NotEmpty
  @Valid
  private List<PromptItem> prompts = new ArrayList<>();

  public List<PromptItem> getPrompts() {
    return prompts;
  }

  public void setPrompts(List<PromptItem> prompts) {
    this.prompts = prompts;
  }
}
