package com.batch.ingestion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.batch.ingestion.model.InferenceResult;
import com.batch.ingestion.model.PromptItem;

class WorkerPoolTest {

  private ExecutorService executor;

  @AfterEach
  void tearDown() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  void poolRespectsConcurrencyLimit() throws InterruptedException {
    int poolSize = 2;
    executor = Executors.newFixedThreadPool(poolSize);
    WorkerPool pool = new WorkerPool(executor);

    AtomicInteger concurrent = new AtomicInteger();
    AtomicInteger maxConcurrent = new AtomicInteger();

    List<PromptItem> prompts = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      prompts.add(new PromptItem("p" + i, "prompt " + i));
    }

    pool.processBatch(
        prompts,
        item -> {
          int current = concurrent.incrementAndGet();
          maxConcurrent.updateAndGet(max -> Math.max(max, current));
          Thread.sleep(50);
          concurrent.decrementAndGet();
          return new InferenceResult(item.getId(), item.getPrompt(), "ok", 0);
        },
        r -> {},
        (item, ex) -> {});

    assertTrue(maxConcurrent.get() <= poolSize);
  }

  @Test
  void poolProcessesAllPrompts() {
    executor = Executors.newFixedThreadPool(3);
    WorkerPool pool = new WorkerPool(executor);
    List<String> processed = new CopyOnWriteArrayList<>();

    List<PromptItem> prompts = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      prompts.add(new PromptItem("x" + i, "p"));
    }

    pool.processBatch(
        prompts,
        item -> new InferenceResult(item.getId(), item.getPrompt(), "done", 0),
        r -> processed.add(r.getPromptId()),
        (item, ex) -> {});

    assertEquals(5, processed.size());
  }
}
