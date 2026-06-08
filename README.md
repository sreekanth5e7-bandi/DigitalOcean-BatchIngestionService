# Batch Ingestion Service (Java)

Spring Boot backend that accepts batches of AI prompts, processes them concurrently against a rate-limited mock inference endpoint, and aggregates results into JSON + H2 database.

## Quick Start

**Prerequisites:** Java 17+, Maven 3.9+

```bash
# Build and run
mvn spring-boot:run

# Submit a batch (returns 202 immediately with job_id)
curl -X POST http://localhost:8080/api/v1/batches \
  -H "Content-Type: application/json" \
  -d @sample_data/prompts.json

# Check real-time progress (e.g. 400/1000)
curl http://localhost:8080/api/v1/batches/{jobId}/status

# Get results when complete
curl http://localhost:8080/api/v1/batches/{jobId}/results
```

## Architecture

```mermaid
flowchart TD
    Client([Client]) -->|POST /batches| API[BatchController]
    API -->|202 Accepted + jobId| Client
    API --> BS[BatchService]

    BS -->|create job| DB[(H2 Database)]
    BS -->|submit background task| WP[WorkerPool]

    subgraph Worker Pool — FixedThreadPool
        WP --> W1[Worker 1]
        WP --> W2[Worker 2]
        WP --> WN[Worker N]
    end

    W1 & W2 & WN --> IC[InferenceClient]
    IC -->|POST /mock/infer| Mock[MockInferenceController]

    Mock -->|200 OK| IC
    Mock -->|429 Too Many Requests| IC

    IC -->|Thread.sleep + retry| IC

    IC -->|InferenceResult| AGG[ResultAggregator]
    IC -->|persist row| DB
    AGG -->|finalize JSON| FS[data/results/jobId.json]

    BS -->|update status| DB
    Client -->|GET /status| API
    API --> DB
```

## Main Classes

| Class | Package | Responsibility |
|---|---|---|
| `BatchIngestionApplication` | root | Spring Boot entry point |
| `BatchController` | controller | REST endpoints (submit, upload, status, results) |
| `MockInferenceController` | controller | Built-in mock API returning 429 every 4th request |
| `BatchService` | service | Orchestrates lifecycle: accept → background process → aggregate |
| `WorkerPool` | service | Bounded concurrency via `ExecutorService` fixed thread pool |
| `InferenceClient` | service | HTTP calls with exponential-backoff retry on HTTP 429 |
| `ResultAggregator` | service | In-memory buffer + final JSON export |
| `BatchJobEntity` / `InferenceResultEntity` | entity | JPA persistence |
| `BatchProperties` | config | Worker pool size, retry/backoff settings |

## Concurrency Model

1. **Batch ingestion** returns `202 Accepted` immediately. Processing runs on the shared fixed thread pool.
2. **Worker pool** uses `Executors.newFixedThreadPool(N)` where N = `batch.worker-pool-size` (default 10). Each prompt is a `CompletableFuture` task; the pool caps concurrent in-flight work.
3. **Rate limiting** — on HTTP 429, `InferenceClient` sleeps with exponential backoff + jitter, then retries (up to `batch.max-retries`). A single prompt failure does not abort the batch.
4. **Aggregation** — successful results saved to H2 row-by-row; on completion a JSON file is written to `data/results/{jobId}.json`.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/batches` | Submit JSON array of prompts |
| `POST` | `/api/v1/batches/upload` | Upload a `.json` file |
| `GET` | `/api/v1/batches/{jobId}/status` | Real-time progress |
| `GET` | `/api/v1/batches/{jobId}/results` | Inference results (when complete) |
| `POST` | `/mock/infer` | Mock rate-limited inference endpoint |
| `GET` | `/health` | Health check |

## Configuration (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `batch.worker-pool-size` | `10` | Max concurrent inference calls |
| `batch.max-retries` | `5` | Retry attempts on HTTP 429 |
| `batch.initial-backoff-ms` | `500` | Initial backoff delay |
| `batch.max-backoff-ms` | `30000` | Max backoff cap |
| `batch.results-dir` | `data/results` | Aggregated JSON output directory |

## Testing

```bash
mvn test
```

- `InferenceClientTest` — 429 retry, backoff, exhausted retries
- `WorkerPoolTest` — concurrency bounded to pool size

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) runs `mvn verify` on push/PR with JDK 17.

## Project Structure

```
src/main/java/com/batch/ingestion/
├── BatchIngestionApplication.java
├── config/          BatchProperties, WorkerPoolConfig
├── controller/      BatchController, MockInferenceController
├── entity/          JPA entities
├── exception/       RateLimitException, JobNotFoundException
├── model/           DTOs (BatchRequest, BatchStatus, etc.)
├── repository/      Spring Data JPA
└── service/         BatchService, WorkerPool, InferenceClient, ResultAggregator
src/test/java/       Unit tests
sample_data/         Sample prompt batch
```

## Implementation Roadmap

Build incrementally in this order:

1. **Models & Config** — DTOs, `BatchProperties`, thread pool bean
2. **InferenceClient** — HTTP + 429 retry (write tests first)
3. **WorkerPool** — fixed thread pool + `CompletableFuture`
4. **JPA Entities & Repositories** — job metadata + results
5. **ResultAggregator** — buffer + JSON export
6. **BatchService** — wire background processing
7. **Controllers** — REST API + mock endpoint
8. **Tests & CI** — unit tests + GitHub Actions
