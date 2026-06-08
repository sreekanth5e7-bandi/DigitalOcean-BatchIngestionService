package com.batch.ingestion.model;

/**
 * Immediate response returned after batch ingestion (HTTP 202).
 */
public class BatchAcknowledgement {

  private String jobId;
  private int totalPrompts;
  private JobState status = JobState.PENDING;
  private String message = "Batch accepted. Processing in background.";

  public BatchAcknowledgement() {}

  public BatchAcknowledgement(String jobId, int totalPrompts) {
    this.jobId = jobId;
    this.totalPrompts = totalPrompts;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public int getTotalPrompts() {
    return totalPrompts;
  }

  public void setTotalPrompts(int totalPrompts) {
    this.totalPrompts = totalPrompts;
  }

  public JobState getStatus() {
    return status;
  }

  public void setStatus(JobState status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
