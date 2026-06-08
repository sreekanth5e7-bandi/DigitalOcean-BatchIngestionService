package com.batch.ingestion.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.batch.ingestion.model.BatchAcknowledgement;
import com.batch.ingestion.model.BatchRequest;
import com.batch.ingestion.model.BatchStatus;
import com.batch.ingestion.model.InferenceResult;
import com.batch.ingestion.model.JobState;
import com.batch.ingestion.model.PromptItem;
import com.batch.ingestion.service.BatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

/**
 * REST API for batch ingestion, file upload, job status, and results retrieval.
 */
@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

  private final BatchService batchService;
  private final ObjectMapper objectMapper;

  public BatchController(BatchService batchService, ObjectMapper objectMapper) {
    this.batchService = batchService;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  public ResponseEntity<BatchAcknowledgement> submitBatch(@Valid @RequestBody BatchRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(batchService.submitBatch(request));
  }

  @PostMapping("/upload")
  public ResponseEntity<BatchAcknowledgement> uploadBatch(@RequestParam("file") MultipartFile file)
      throws Exception {
    if (file.isEmpty() || file.getOriginalFilename() == null
        || !file.getOriginalFilename().endsWith(".json")) {
      return ResponseEntity.badRequest().build();
    }
    JsonNode root = objectMapper.readTree(file.getInputStream());
    BatchRequest request = new BatchRequest();
    JsonNode promptsNode = root.has("prompts") ? root.get("prompts") : root;
    request.setPrompts(objectMapper.readerForListOf(PromptItem.class).readValue(promptsNode));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(batchService.submitBatch(request));
  }

  @GetMapping("/{jobId}/status")
  public BatchStatus getStatus(@PathVariable String jobId) {
    return batchService.getStatus(jobId);
  }

  @GetMapping("/{jobId}/results")
  public ResponseEntity<List<InferenceResult>> getResults(@PathVariable String jobId) {
    BatchStatus status = batchService.getStatus(jobId);
    if (status.getStatus() != JobState.COMPLETED && status.getStatus() != JobState.FAILED) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    return ResponseEntity.ok(batchService.getResults(jobId));
  }
}
