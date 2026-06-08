package com.batch.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.batch.ingestion.config.BatchProperties;

@SpringBootApplication
@EnableConfigurationProperties(BatchProperties.class)
public class BatchIngestionApplication {

  public static void main(String[] args) {
    SpringApplication.run(BatchIngestionApplication.class, args);
  }
}
