package com.batch.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.batch.ingestion.entity.BatchJobEntity;

public interface BatchJobRepository extends JpaRepository<BatchJobEntity, String> {}
