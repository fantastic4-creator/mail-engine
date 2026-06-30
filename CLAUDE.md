# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mail Engine is a multi-tenant marketing email platform. The backend is a Java 21 / Spring Boot 3.4.5 application with Postgres persistence (JPA + Flyway), CI/CD via GitHub Actions, and AWS deployment scripts. Delivery mode defaults to local capture.

## Build and Run

All commands run from the `backend/` directory. Maven is configured via `.mvn/maven.config` to use a local `.m2` repo and empty settings files — the wrapper handles this automatically.

```bash
# Run the application
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MailEngineApplicationTests
```

The app starts on port 8080. Actuator health is at `/actuator/health`.

## Runtime Mode Switching

Behavior is controlled entirely by environment variables — no code changes needed:

| Variable | Default | Options |
|---|---|---|
| `MAIL_ENGINE_DELIVERY_MODE` | `local-outbox` | `local-outbox`, `aws-smtp-relay`, `smtp` |
| `MAIL_ENGINE_STORAGE_MODE` | `in-memory` | `in-memory`, `postgres` |
| `MAIL_ENGINE_SMTP_HOST` | `localhost` | any host |
| `MAIL_ENGINE_SMTP_PORT` | `1025` | any port |

For local SMTP capture (Mailpit default port 1025), set `MAIL_ENGINE_DELIVERY_MODE=smtp` and point at localhost.

## Architecture

The codebase is split into four planes that map directly to Java packages under `com.mailengine`:

```
api/          Control plane — REST controllers (Tenant, Campaign, IpPool, Suppression, Health, Runtime)
              CampaignController includes POST /{id}/recipients/import (multipart CSV upload)
api/dto/      Request/response records (CreateCampaignRequest, ImportRecipientsResponse, etc.)
service/      Control plane — business logic (TenantService, CampaignService, IpPoolService, etc.)
              CampaignService.importRecipients() deduplicates and fires the send loop immediately
data/         Data plane — PlatformStateStore interface
              InMemoryPlatformStateStore (@ConditionalOnProperty storage-mode=in-memory, default)
              JpaPlatformStateStore      (@ConditionalOnProperty storage-mode=postgres)
data/entity/  9 JPA entity classes mapping domain records to DB rows
data/repository/  9 Spring Data JPA repositories; MessageJobRepository uses FOR UPDATE SKIP LOCKED
domain/       Data plane — Java records (Tenant, Campaign, MessageJob, OutboundMessage, etc.)
worker/       Worker plane — CampaignSendLoop / DefaultCampaignSendLoop
delivery/     Delivery plane — DeliveryGateway / ConfigurableDeliveryGateway
config/       MailEngineRuntimeProperties (@ConfigurationProperties on mail-engine.runtime.*)
              PostgresStorageConfiguration (@EnableJpaRepositories + @EntityScan, conditional)
```

### Local Development

```bash
# Start Postgres 16 + Mailpit (SMTP capture on :1025, web UI on :8025)
docker-compose up -d

# Run with Postgres storage and SMTP capture
MAIL_ENGINE_STORAGE_MODE=postgres \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mailengine \
SPRING_DATASOURCE_USERNAME=mailengine \
SPRING_DATASOURCE_PASSWORD=mailengine \
MAIL_ENGINE_DELIVERY_MODE=smtp \
./mvnw spring-boot:run
```

Flyway runs automatically on startup and applies `V1__create_schema.sql`.

### CI/CD

`.github/workflows/deploy.yml` runs tests on every PR and auto-deploys to EC2 on push to `main` using SSH + the `scripts/aws/06-deploy-app.sh` script.

### Key Design Invariants

**Interfaces shield the planes from each other.** `PlatformStateStore` decouples the API/service layer from the storage backend. `DeliveryGateway` decouples the worker from the actual SMTP mechanism. Both `InMemoryPlatformStateStore` and `JpaPlatformStateStore` implement the same interface and are selected by env var — nothing outside `data/` changes when switching.

**Delivery mode is a runtime switch, not a code branch.** `ConfigurableDeliveryGateway` reads `MailEngineRuntimeProperties.deliveryMode` at call time. Adding a new mode means adding a case to the `switch` and a new `DeliveryMode` enum value.

**The send loop is synchronous in the scaffold.** `DefaultCampaignSendLoop.process()` claims up to 1,000 jobs and processes them inline. This is intentional — the interface is already worker-shaped for a future async/scheduled implementation.

### Message Job Lifecycle

```
Campaign created → per-recipient MessageJob (PENDING)
    → worker claims → suppression check → DeliveryGateway.deliver()
    → SENT / SUPPRESSED / FAILED
```

Status values: `PENDING`, `CLAIMED`, `SENT`, `FAILED`, `SUPPRESSED`, `RETRY_SCHEDULED`

## What Is Not Implemented Yet

- Database-backed retry scheduling (`RETRY_SCHEDULED` status exists but the scheduler is not wired)
- Bounce and complaint ingestion
- Warmup and pacing logic
- Authentication/authorization
- DKIM signing (key generation exists in `DkimKeyGenerator`, signing is not wired into delivery)
- Template rendering (campaign body is stored/sent as-is)
- CSV import field validation beyond email presence (malformed addresses are imported as-is)
