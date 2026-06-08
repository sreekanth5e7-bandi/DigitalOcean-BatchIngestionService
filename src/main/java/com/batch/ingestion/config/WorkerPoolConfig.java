package com.batch.ingestion.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a fixed-size thread pool bean used by {@link com.batch.ingestion.service.WorkerPool}.
 *
 * Pool size is bounded by {@link BatchProperties#getWorkerPoolSize()} so the application
 * never spawns unbounded threads when processing large batches (e.g. 1000 prompts).
 */
@Configuration
public class WorkerPoolConfig {

  @Bean(destroyMethod = "shutdown")
  public ExecutorService batchExecutor(BatchProperties properties) {
    int poolSize = properties.getWorkerPoolSize();
    AtomicInteger counter = new AtomicInteger();
    ThreadFactory factory = r -> {
      Thread t = new Thread(r);
      t.setName("batch-worker-" + counter.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
    return Executors.newFixedThreadPool(poolSize, factory);
  }
}
