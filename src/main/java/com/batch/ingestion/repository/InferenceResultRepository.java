package com.batch.ingestion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.batch.ingestion.entity.InferenceResultEntity;

public interface InferenceResultRepository extends JpaRepository<InferenceResultEntity, Long> {

  List<InferenceResultEntity> findByJobIdOrderByIdAsc(String jobId);
}
