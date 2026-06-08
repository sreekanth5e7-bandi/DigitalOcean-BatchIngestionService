package com.batch.ingestion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.batch.ingestion.model.InferenceResult;
import com.batch.ingestion.model.PromptItem;

/**
 * Distributes prompts across a bounded fixed thread pool.
 *
 * Concurrency is limited by the injected {@link ExecutorService} (see {@link
 * com.batch.ingestion.config.WorkerPoolConfig}). At most N prompts are processed
 * concurrently, preventing unbounded thread creation for large batches.
 */
@Service
public class WorkerPool {

  private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

  private final ExecutorService executor;

  public WorkerPool(ExecutorService batchExecutor) {
    this.executor = batchExecutor;
  }

  public void processBatch(
      List<PromptItem> prompts,
      Function<PromptItem, InferenceResult> processFn,
      Consumer<InferenceResult> onSuccess,
      BiConsumer<PromptItem, Exception> onFailure) {

    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (PromptItem item : prompts) {
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        try {
          InferenceResult result = processFn.apply(item);
          onSuccess.accept(result);
        } catch (Exception ex) {
          log.error("Worker failed for prompt {}: {}", item.getId(), ex.getMessage());
          onFailure.accept(item, ex);
        }
      }, executor);
      futures.add(future);
    }

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    log.info("Worker pool finished processing {} prompts", prompts.size());
  }
}
