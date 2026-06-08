package com.batch.ingestion.model;

import java.time.Instant;

/**
 * Real-time progress of an in-flight or completed batch job.
 */
public class BatchStatus {

  private String jobId;
  private JobState status;
  private int total;
  private int completed;
  private int failed;
  private double progressPct;
  private Instant createdAt;
  private Instant updatedAt;
  private String resultsPath;
  private String error;

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public JobState getStatus() {
    return status;
  }

  public void setStatus(JobState status) {
    this.status = status;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getCompleted() {
    return completed;
  }

  public void setCompleted(int completed) {
    this.completed = completed;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(int failed) {
    this.failed = failed;
  }

  public double getProgressPct() {
    return progressPct;
  }

  public void setProgressPct(double progressPct) {
    this.progressPct = progressPct;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getResultsPath() {
    return resultsPath;
  }

  public void setResultsPath(String resultsPath) {
    this.resultsPath = resultsPath;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }
}
