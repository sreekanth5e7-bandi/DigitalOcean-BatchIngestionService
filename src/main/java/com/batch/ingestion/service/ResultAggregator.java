package com.batch.ingestion.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.batch.ingestion.config.BatchProperties;
import com.batch.ingestion.model.InferenceResult;
import com.batch.ingestion.model.JobState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Collects inference results in memory during processing and writes
 * the final aggregated JSON file once the batch completes.
 */
@Service
public class ResultAggregator {

  private static final Logger log = LoggerFactory.getLogger(ResultAggregator.class);

  private final Path resultsDir;
  private final ObjectMapper mapper;
  private final Map<String, List<InferenceResult>> buffers = new ConcurrentHashMap<>();

  public ResultAggregator(BatchProperties properties) {
    this.resultsDir = Path.of(properties.getResultsDir());
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      Files.createDirectories(resultsDir);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create results directory: " + resultsDir, e);
    }
  }

  public void initJob(String jobId) {
    buffers.put(jobId, new ArrayList<>());
  }

  public void addResult(String jobId, InferenceResult result) {
    buffers.computeIfAbsent(jobId, k -> new ArrayList<>()).add(result);
  }

  public String finalizeJob(String jobId, int total, int completed, int failed, JobState status)
      throws IOException {
    List<InferenceResult> results = buffers.getOrDefault(jobId, List.of());
    Map<String, Object> output = new HashMap<>();
    output.put("jobId", jobId);
    output.put("status", status.name().toLowerCase());
    output.put("total", total);
    output.put("completed", completed);
    output.put("failed", failed);
    output.put("finalizedAt", Instant.now().toString());
    output.put("results", results);

    Path path = resultsDir.resolve(jobId + ".json");
    mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), output);
    log.info("Aggregated {} results for job {} -> {}", results.size(), jobId, path);
    return path.toString();
  }
}
