# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mail Engine is a multi-tenant marketing email platform. Backend: Java 21 / Spring Boot 3.4.5 with Postgres persistence (JPA + Flyway), async send loop, DKIM signing, API key authentication, and CI/CD via GitHub Actions. **The app is deployed on AWS** (EC2 + RDS Postgres + security groups already provisioned).

## AWS Infrastructure (already live)

All resources were created using scripts in `scripts/aws/` with AWS access keys.

| Resource | Details |
|---|---|
| EC2 | Amazon Linux 2023, `t3.micro`, Java 21 (Amazon Corretto) via `dnf`, systemd service `mail-engine` |
| RDS | Postgres 16, `db.t3.micro`, identifier `mail-engine-db`, DB name `mailengine` |
| Elastic IP | Static public IP attached to EC2 (saved in `scripts/.state`) |
| Security Groups | `mail-engine-ec2` (SSH :22, API :8080, SMTP :25 inbound); `mail-engine-rds` (Postgres :5432 from EC2 SG only) |
| Region | `us-east-1` (default; overridable via `AWS_REGION`) |

The `scripts/.state` file holds `ELASTIC_IP`, `KEY_FILE`, `RDS_ENDPOINT`, `DB_PASSWORD`, `SG_EC2_ID`, `SG_RDS_ID`.

### Redeploy (after code changes)

```bash
# With AWS SES SMTP (recommended — no port-25 unblocking needed):
DELIVERY_MODE=smtp \
SMTP_HOST=email-smtp.us-east-1.amazonaws.com \
SMTP_PORT=587 \
SMTP_AUTH_ENABLED=true \
SMTP_STARTTLS_ENABLED=true \
SMTP_USERNAME=<SES_SMTP_KEY_ID> \
SMTP_PASSWORD=<SES_SMTP_SECRET> \
APP_BASE_URL=http://<ELASTIC_IP>:8080 \
UNSUBSCRIBE_HMAC_SECRET=<strong-random-string> \
./scripts/aws/06-deploy-app.sh
```

The script builds the JAR locally (`mvn clean package -DskipTests`), copies it to EC2 via SCP, writes `/opt/mail-engine/config/app.env`, and restarts the `mail-engine` systemd service.

### Tail logs on EC2

```bash
ssh -i ~/.ssh/mail-engine-key.pem ec2-user@<ELASTIC_IP> \
  'sudo journalctl -fu mail-engine'
```

## Build and Run (local)

All commands run from the `backend/` directory. Use `mvn` (no wrapper — `mvnw` is not present).

```bash
# Run the application
mvn spring-boot:run

# Build (skip tests)
mvn clean package -DskipTests

# Run all tests
mvn test
```

The app starts on port 8080. Actuator health: `GET /actuator/health`.

### Local development with docker-compose

```bash
# Start Postgres 16 + Mailpit (SMTP capture :1025, web UI :8025)
docker-compose up -d

# Run with Postgres + SMTP capture
MAIL_ENGINE_STORAGE_MODE=postgres \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mailengine \
SPRING_DATASOURCE_USERNAME=mailengine \
SPRING_DATASOURCE_PASSWORD=mailengine \
MAIL_ENGINE_DELIVERY_MODE=smtp \
mvn spring-boot:run
```

## Runtime Environment Variables

| Variable | Default | Notes |
|---|---|---|
| `MAIL_ENGINE_DELIVERY_MODE` | `local-outbox` | `local-outbox`, `aws-smtp-relay`, `smtp` |
| `MAIL_ENGINE_STORAGE_MODE` | `in-memory` | `in-memory`, `postgres` |
| `MAIL_ENGINE_SMTP_HOST` | `localhost` | SES: `email-smtp.us-east-1.amazonaws.com` |
| `MAIL_ENGINE_SMTP_PORT` | `1025` | SES: `587` |
| `MAIL_ENGINE_SMTP_AUTH_ENABLED` | `false` | `true` for SES |
| `MAIL_ENGINE_SMTP_STARTTLS_ENABLED` | `false` | `true` for SES |
| `MAIL_ENGINE_SMTP_USERNAME` | _(empty)_ | SES SMTP access key ID |
| `MAIL_ENGINE_SMTP_PASSWORD` | _(empty)_ | SES SMTP secret |
| `MAIL_ENGINE_APP_BASE_URL` | `http://localhost:8080` | Used in unsubscribe links |
| `MAIL_ENGINE_UNSUBSCRIBE_HMAC_SECRET` | `change-me-in-production` | HMAC-SHA256 key for unsubscribe tokens |
| `MAIL_ENGINE_SEND_LOOP_POLL_MS` | `5000` | How often the scheduler picks up PENDING jobs |
| `MAIL_ENGINE_RETRY_MAX_ATTEMPTS` | `5` | Max SMTP retry attempts before permanent FAILED |
| `MAIL_ENGINE_RETRY_BACKOFF_SECONDS` | `300` | Base backoff; doubles each retry (exponential) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/mailengine` | RDS endpoint in production |

## Architecture

```
api/            REST controllers (Tenant, Campaign, IpPool, Suppression, ApiKey, Health, Runtime)
                POST /{id}/recipients/import  — multipart CSV upload
                POST /{id}/cancel             — cancel a running campaign
api/dto/        Request/response records
service/        Business logic (TenantService, CampaignService, ApiKeyService, UnsubscribeTokenService…)
data/           PlatformStateStore interface
                InMemoryPlatformStateStore  (default, storage-mode=in-memory)
                JpaPlatformStateStore       (storage-mode=postgres)
data/entity/    10 JPA entity classes (Tenant, Campaign, MessageJob, ApiKey, …)
data/repository/10 Spring Data JPA repositories
domain/         Java records (Tenant, Campaign, MessageJob, ApiKey, CampaignStatus, …)
worker/         DefaultCampaignSendLoop, SendLoopScheduler, RetryScheduler
delivery/       ConfigurableDeliveryGateway — DKIM + MimeMessage + List-Unsubscribe headers
config/         MailEngineRuntimeProperties, AsyncConfig (@EnableAsync @EnableScheduling),
                ApiKeyAuthInterceptor, WebMvcConfig
```

### API Authentication

Every request (except `/actuator/**`, `/unsubscribe`, `/webhooks/**`) requires an `X-API-Key` header.

```bash
# Create a tenant, then generate an API key (key is shown once):
curl -X POST http://<HOST>/api/tenants -H 'Content-Type: application/json' \
  -d '{"name":"Acme"}' -H 'X-API-Key: <key>'

# All subsequent requests:
curl http://<HOST>/api/campaigns -H 'X-API-Key: me_<key>'
```

Keys are stored as SHA-256 hashes. Format: `me_<base64url(32 bytes)>`.

### Send Loop & Retry

- `SendLoopScheduler` polls every 5 s for PENDING jobs → calls `DefaultCampaignSendLoop.process()`
- SMTP failures schedule a retry with exponential backoff (300 s × 2^retryCount, max 5 attempts)
- `RetryScheduler` runs every 60 s to pick up `RETRY_SCHEDULED` jobs whose `nextRetryAt` has passed
- Campaign transitions: `SENDING` → `SENT` (all jobs terminal) or `FAILED` (cancelled)

### Message Job Lifecycle

```
Campaign created → per-recipient MessageJob (PENDING)
    → SendLoopScheduler claims (CLAIMED) → suppression check → DeliveryGateway.deliver()
    → SENT | SUPPRESSED | RETRY_SCHEDULED (→ PENDING retry) | FAILED (max retries exceeded)
```

### DKIM + DNS Verification

- `TenantService.addDomain()` generates a 2048-bit RSA key pair via `DkimKeyGenerator`
- `GET /api/tenants/{id}/domains/{id}/dns-records` returns 4 TXT records to add to your registrar:
  - Ownership verification (`_mailengine.<domain>`)
  - DKIM public key (`<selector>._domainkey.<domain>`)
  - SPF starter record
  - DMARC starter record
- `POST /api/tenants/{id}/domains/{id}/verify` does a real DNS TXT lookup — domain must pass before campaigns can send

### Bounce & Unsubscribe

- `POST /webhooks/{tenantId}/ses` — SNS/SES webhook; handles SubscriptionConfirmation, Bounce (Permanent), Complaint → auto-suppression
- `GET /unsubscribe?tenant=&email=&campaign=&sig=` — HMAC-validated unsubscribe; auto-suppresses and returns 200
- `List-Unsubscribe` + `List-Unsubscribe-Post` headers injected on every outbound email

## Database Migrations (Flyway)

| File | Contents |
|---|---|
| `V1__create_schema.sql` | All core tables: tenant, sending_domain, ip_pool, outbound_ip, campaign, recipient, message_job, suppression_record, outbound_message |
| `V2__add_campaign_status_and_retry.sql` | `campaign.status`, `message_job.retry_count`, `message_job.next_retry_at`, partial index |
| `V3__add_api_keys.sql` | `api_key` table with hash index |

## First-Time Setup on a New Deployment

```bash
# 1. Deploy infra (skip if already done)
./scripts/aws/01-install-aws-cli.sh
./scripts/aws/02-create-key-pair.sh
./scripts/aws/03-create-security-groups.sh
./scripts/aws/04-create-rds.sh
./scripts/aws/05-create-ec2.sh

# 2. Deploy app with SES SMTP
DELIVERY_MODE=smtp SMTP_HOST=email-smtp.us-east-1.amazonaws.com \
SMTP_PORT=587 SMTP_AUTH_ENABLED=true SMTP_STARTTLS_ENABLED=true \
SMTP_USERNAME=<SES_KEY> SMTP_PASSWORD=<SES_SECRET> \
APP_BASE_URL=http://<ELASTIC_IP>:8080 \
UNSUBSCRIBE_HMAC_SECRET=$(openssl rand -hex 32) \
./scripts/aws/06-deploy-app.sh

# 3. Bootstrap the app via API
BASE=http://<ELASTIC_IP>:8080

# Create tenant
curl -X POST $BASE/api/tenants -H 'Content-Type: application/json' \
  -d '{"name":"My Company"}'     # → copy tenantId

# Create first API key (note: initial bootstrap has no auth — add a key immediately)
curl -X POST $BASE/api/tenants/<tenantId>/api-keys -H 'Content-Type: application/json' \
  -d '{"name":"admin"}'          # → copy rawKey from response (shown once)

# All future calls use: -H 'X-API-Key: <rawKey>'

# Add a sending domain
curl -X POST $BASE/api/tenants/<tenantId>/domains \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"domainName":"mail.yourdomain.com"}'

# Get DNS records → add all 4 TXT records to your registrar
curl $BASE/api/tenants/<tenantId>/domains/<domainId>/dns-records \
  -H 'X-API-Key: <key>'

# After DNS propagates (~5 min): verify domain
curl -X POST $BASE/api/tenants/<tenantId>/domains/<domainId>/verify \
  -H 'X-API-Key: <key>'

# Create IP pool + register EC2 elastic IP
curl -X POST $BASE/api/tenants/<tenantId>/ip-pools \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"name":"default","trafficType":"bulk"}'

curl -X POST $BASE/api/ip-pools/<poolId>/ips \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"publicIpAddress":"<ELASTIC_IP>"}'

# Send a test campaign
curl -X POST $BASE/api/campaigns \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{
    "tenantId":"<tenantId>","domainId":"<domainId>",
    "name":"Test","subject":"Hello","body":"<p>It works!</p>",
    "recipientEmail":"you@yourdomain.com"
  }'
```

## What Is Not Implemented Yet

- Template rendering with variable substitution (body is sent as-is)
- Warmup / rate limiting / hourly send quota
- Integration test suite (only a smoke test exists)
- CSV import email address validation beyond presence of `@`
