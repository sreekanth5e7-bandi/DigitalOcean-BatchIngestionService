package com.batch.ingestion.entity;

import java.time.Instant;
import java.util.UUID;

import com.batch.ingestion.model.JobState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "batch_jobs")
public class BatchJobEntity {

  @Id
  private String jobId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobState status;

  @Column(nullable = false)
  private int total;

  @Column(nullable = false)
  private int completed = 0;

  @Column(nullable = false)
  private int failed = 0;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private String resultsPath;
  private String error;

  @PrePersist
  void onCreate() {
    if (jobId == null) {
      jobId = UUID.randomUUID().toString();
    }
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

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
