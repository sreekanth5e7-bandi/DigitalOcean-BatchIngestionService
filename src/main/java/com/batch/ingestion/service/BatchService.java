package com.batch.ingestion.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.batch.ingestion.entity.BatchJobEntity;
import com.batch.ingestion.entity.InferenceResultEntity;
import com.batch.ingestion.exception.JobNotFoundException;
import com.batch.ingestion.model.BatchAcknowledgement;
import com.batch.ingestion.model.BatchRequest;
import com.batch.ingestion.model.BatchStatus;
import com.batch.ingestion.model.InferenceResult;
import com.batch.ingestion.model.JobState;
import com.batch.ingestion.model.PromptItem;
import com.batch.ingestion.repository.BatchJobRepository;
import com.batch.ingestion.repository.InferenceResultRepository;

/**
 * Top-level orchestrator for the batch lifecycle:
 *
 *   1. Accept batch → return acknowledgement immediately (HTTP 202)
 *   2. Background task → worker pool processes prompts concurrently
 *   3. On completion → aggregate results to JSON + persist to H2
 */
@Service
public class BatchService {

  private static final Logger log = LoggerFactory.getLogger(BatchService.class);

  private final BatchJobRepository jobRepository;
  private final InferenceResultRepository resultRepository;
  private final WorkerPool workerPool;
  private final InferenceClient inferenceClient;
  private final ResultAggregator aggregator;
  private final ExecutorService backgroundExecutor;

  public BatchService(
      BatchJobRepository jobRepository,
      InferenceResultRepository resultRepository,
      WorkerPool workerPool,
      InferenceClient inferenceClient,
      ResultAggregator aggregator,
      ExecutorService batchExecutor) {
    this.jobRepository = jobRepository;
    this.resultRepository = resultRepository;
    this.workerPool = workerPool;
    this.inferenceClient = inferenceClient;
    this.aggregator = aggregator;
    this.backgroundExecutor = batchExecutor;
  }

  @Transactional
  public BatchAcknowledgement submitBatch(BatchRequest request) {
    String jobId = UUID.randomUUID().toString();
    int total = request.getPrompts().size();

    BatchJobEntity job = new BatchJobEntity();
    job.setJobId(jobId);
    job.setStatus(JobState.PENDING);
    job.setTotal(total);
    jobRepository.save(job);

    aggregator.initJob(jobId);
    List<PromptItem> prompts = List.copyOf(request.getPrompts());

    backgroundExecutor.submit(() -> processBatch(jobId, prompts));
    log.info("Batch {} accepted with {} prompts", jobId, total);

    return new BatchAcknowledgement(jobId, total);
  }

  @Transactional(readOnly = true)
  public BatchStatus getStatus(String jobId) {
    BatchJobEntity job = jobRepository.findById(jobId)
        .orElseThrow(() -> new JobNotFoundException(jobId));
    return toStatus(job);
  }

  @Transactional(readOnly = true)
  public List<InferenceResult> getResults(String jobId) {
    if (!jobRepository.existsById(jobId)) {
      throw new JobNotFoundException(jobId);
    }
    return resultRepository.findByJobIdOrderByIdAsc(jobId).stream()
        .map(e -> new InferenceResult(
            e.getPromptId(), e.getPrompt(), e.getResponse(), e.getRetries()))
        .toList();
  }

  private void processBatch(String jobId, List<PromptItem> prompts) {
    updateJobStatus(jobId, JobState.PROCESSING, null, null, null);

    AtomicInteger completed = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();

    try {
      workerPool.processBatch(
          prompts,
          item -> {
            try {
              return inferenceClient.infer(item);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException(e);
            }
          },
          result -> handleSuccess(jobId, result, completed),
          (item, ex) -> handleFailure(jobId, failed));

      String resultsPath = aggregator.finalizeJob(
          jobId, prompts.size(), completed.get(), failed.get(), JobState.COMPLETED);
      updateJobStatus(jobId, JobState.COMPLETED, completed.get(), failed.get(), resultsPath);
      log.info("Batch {} completed: {} success, {} failed", jobId, completed.get(), failed.get());
    } catch (Exception ex) {
      log.error("Batch {} failed: {}", jobId, ex.getMessage(), ex);
      updateJobStatus(jobId, JobState.FAILED, completed.get(), failed.get(), null);
      jobRepository.findById(jobId).ifPresent(job -> {
        job.setError(ex.getMessage());
        jobRepository.save(job);
      });
    }
  }

  private void handleSuccess(String jobId, InferenceResult result, AtomicInteger completed) {
    InferenceResultEntity entity = new InferenceResultEntity();
    entity.setJobId(jobId);
    entity.setPromptId(result.getPromptId());
    entity.setPrompt(result.getPrompt());
    entity.setResponse(result.getResponse());
    entity.setRetries(result.getRetries());
    entity.setCompletedAt(result.getCompletedAt());
    resultRepository.save(entity);

    aggregator.addResult(jobId, result);
    jobRepository.findById(jobId).ifPresent(job -> {
      job.setCompleted(job.getCompleted() + 1);
      jobRepository.save(job);
    });
    completed.incrementAndGet();
  }

  private void handleFailure(String jobId, AtomicInteger failed) {
    jobRepository.findById(jobId).ifPresent(job -> {
      job.setFailed(job.getFailed() + 1);
      jobRepository.save(job);
    });
    failed.incrementAndGet();
  }

  private void updateJobStatus(
      String jobId, JobState status, Integer completed, Integer failed, String resultsPath) {
    jobRepository.findById(jobId).ifPresent(job -> {
      job.setStatus(status);
      if (completed != null) job.setCompleted(completed);
      if (failed != null) job.setFailed(failed);
      if (resultsPath != null) job.setResultsPath(resultsPath);
      jobRepository.save(job);
    });
  }

  private BatchStatus toStatus(BatchJobEntity job) {
    BatchStatus status = new BatchStatus();
    status.setJobId(job.getJobId());
    status.setStatus(job.getStatus());
    status.setTotal(job.getTotal());
    status.setCompleted(job.getCompleted());
    status.setFailed(job.getFailed());
    status.setProgressPct(job.getTotal() > 0
        ? Math.round(job.getCompleted() * 10000.0 / job.getTotal()) / 100.0
        : 0.0);
    status.setCreatedAt(job.getCreatedAt());
    status.setUpdatedAt(job.getUpdatedAt());
    status.setResultsPath(job.getResultsPath());
    status.setError(job.getError());
    return status;
  }
}
