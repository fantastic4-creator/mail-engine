# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mail Engine is a multi-tenant marketing email platform. Backend: Java 21 / Spring Boot 3.4.5 with Postgres persistence (JPA + Flyway), async send loop, DKIM signing, API key authentication, and CI/CD via GitHub Actions. **The app is deployed on AWS (EC2 + RDS Postgres already provisioned and bootstrapped).**

**IMPORTANT: This platform uses Postfix on EC2 as the SMTP relay (port 25), NOT AWS SES. DKIM signing is done with per-domain RSA-2048 private keys stored in the database.**

## AWS Infrastructure (already live)

| Resource | Value |
|---|---|
| EC2 Elastic IP | `3.208.157.146` |
| EC2 Instance ID | `i-0787f6ec563498dd2` |
| RDS Endpoint | `mail-engine-db.cqp0eagwea87.us-east-1.rds.amazonaws.com` |
| DB Password | `9ZuJSbL6clDYEEEIs0UTVovd55mMYR` |
| SSH Key | `~/.ssh/mail-engine-key.pem` |
| Region | `us-east-1` |

State file: `scripts/.state` — holds all IDs.

### Bootstrapped Tenant (already done)

| Entity | ID |
|---|---|
| Tenant (Rissolv) | `01abaedb-6078-4a0a-b03e-62505cbab0f3` |
| Sending Domain | `31a02389-2826-4717-8f1b-c32892d905f2` (consult.rissolv.com) |
| IP Pool | `2638502a-f8dd-4f89-a45b-4402977a1c0f` |
| API Key | `me_N-okWHqYeDFF2ek-HIxdIVlntemEzn84BMSKX7SnrLA` |

### Redeploy (after code changes)

```bash
cd "/Users/jeebanjyotiswain/Documents/New project"
DELIVERY_MODE=smtp SMTP_HOST=localhost SMTP_PORT=25 \
SMTP_AUTH_ENABLED=false SMTP_STARTTLS_ENABLED=false \
APP_BASE_URL=http://3.208.157.146:8080 \
UNSUBSCRIBE_HMAC_SECRET=100cefdcec2019d12cd9baccea59b47478f1623ccd865363a003115fbc56200c \
bash scripts/aws/06-deploy-app.sh
```

### Tail logs on EC2

```bash
ssh -i ~/.ssh/mail-engine-key.pem ec2-user@3.208.157.146 \
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

# Run with Postgres + local SMTP capture
MAIL_ENGINE_STORAGE_MODE=postgres \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mailengine \
SPRING_DATASOURCE_USERNAME=mailengine \
SPRING_DATASOURCE_PASSWORD=mailengine \
MAIL_ENGINE_DELIVERY_MODE=smtp \
MAIL_ENGINE_SMTP_HOST=localhost \
MAIL_ENGINE_SMTP_PORT=1025 \
mvn spring-boot:run
```

## Runtime Environment Variables

| Variable | Default | Notes |
|---|---|---|
| `MAIL_ENGINE_DELIVERY_MODE` | `local-outbox` | `local-outbox`, `smtp` |
| `MAIL_ENGINE_STORAGE_MODE` | `in-memory` | `in-memory`, `postgres` |
| `MAIL_ENGINE_SMTP_HOST` | `localhost` | Production: `localhost` (Postfix relay) |
| `MAIL_ENGINE_SMTP_PORT` | `1025` | Production: `25` (Postfix) |
| `MAIL_ENGINE_SMTP_AUTH_ENABLED` | `false` | Keep false for Postfix on localhost |
| `MAIL_ENGINE_SMTP_STARTTLS_ENABLED` | `false` | Keep false for Postfix on localhost |
| `MAIL_ENGINE_APP_BASE_URL` | `http://localhost:8080` | Used in unsubscribe links |
| `MAIL_ENGINE_UNSUBSCRIBE_HMAC_SECRET` | `change-me-in-production` | HMAC-SHA256 key for unsubscribe tokens |
| `MAIL_ENGINE_SEND_LOOP_POLL_MS` | `5000` | How often the scheduler picks up PENDING jobs |
| `MAIL_ENGINE_RETRY_MAX_ATTEMPTS` | `5` | Max SMTP retry attempts before permanent FAILED |
| `MAIL_ENGINE_RETRY_BACKOFF_SECONDS` | `300` | Base backoff; doubles each retry (exponential) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/mailengine` | RDS endpoint in production |

## Architecture

```
api/            REST controllers (Tenant, Campaign, IpPool, Suppression, ApiKey, Health, Runtime)
api/dto/        Request/response records
service/        Business logic (TenantService, CampaignService, ApiKeyService, UnsubscribeTokenService…)
data/           PlatformStateStore interface
                InMemoryPlatformStateStore  (default, storage-mode=in-memory)
                JpaPlatformStateStore       (storage-mode=postgres)
data/entity/    JPA entity classes
data/repository/Spring Data JPA repositories
domain/         Java records (Tenant, Campaign, MessageJob, ApiKey, CampaignStatus, …)
worker/         DefaultCampaignSendLoop, SendLoopScheduler, RetryScheduler
delivery/       ConfigurableDeliveryGateway — DKIM signing + MimeMessage + List-Unsubscribe headers
config/         MailEngineRuntimeProperties, AsyncConfig (@EnableAsync @EnableScheduling),
                ApiKeyAuthInterceptor, WebMvcConfig
```

### API Authentication

Every request requires an `X-API-Key` header **except** these paths (bootstrap exclusions):
- `/actuator/**`
- `/unsubscribe`
- `/webhooks/**`
- `POST /api/tenants` — create first tenant
- `POST /api/tenants/*/api-keys` — create first API key

Keys are stored as SHA-256 hashes. Format: `me_<base64url(32 bytes)>`.

### DKIM + DNS

- `TenantService.addDomain()` generates a 2048-bit RSA key pair (stored in DB as PEM)
- DKIM selector defaults to `me1` (configurable via `MAIL_ENGINE_DKIM_SELECTOR`)
- `GET /api/tenants/{id}/domains/{id}/dns-records` returns 4 TXT records for the registrar
- `POST /api/tenants/{id}/domains/{id}/verify` does a real JNDI DNS TXT lookup for `_mailengine.<domain>`

### Send Loop & Retry

- `SendLoopScheduler` polls every 5 s for PENDING jobs → `DefaultCampaignSendLoop.process()`
- SMTP failures: exponential backoff (300 s × 2^retryCount), max 5 attempts → then FAILED
- `RetryScheduler` runs every 60 s to pick up `RETRY_SCHEDULED` jobs whose `nextRetryAt` has passed
- Campaign status: `SENDING` → `SENT` (all jobs terminal) or `FAILED` (cancelled)

### Message Job Lifecycle

```
Campaign created → per-recipient MessageJob (PENDING)
    → SendLoopScheduler claims (CLAIMED) → suppression check → DeliveryGateway.deliver()
    → SENT | SUPPRESSED | RETRY_SCHEDULED (→ PENDING retry) | FAILED (max retries exceeded)
```

### Bounce & Unsubscribe

- `POST /webhooks/{tenantId}/ses` — SNS webhook: SubscriptionConfirmation, Bounce (Permanent), Complaint → auto-suppression
- `GET /unsubscribe?tenant=&email=&campaign=&sig=` — HMAC-validated, auto-suppresses, returns 200
- `List-Unsubscribe` + `List-Unsubscribe-Post` headers injected on every outbound email

## Database Migrations (Flyway)

| File | Contents |
|---|---|
| `V1__create_schema.sql` | All core tables: tenant, sending_domain, ip_pool, outbound_ip, campaign, recipient, message_job, suppression_record, outbound_message |
| `V2__add_campaign_status_and_retry.sql` | `campaign.status`, `message_job.retry_count`, `message_job.next_retry_at`, partial index |
| `V3__add_api_keys.sql` | `api_key` table with hash index |

## First-Time Setup on a Fresh Deployment

```bash
# 1. Provision infrastructure
bash scripts/aws/01-install-aws-cli.sh
bash scripts/aws/02-create-key-pair.sh
bash scripts/aws/03-create-security-groups.sh
bash scripts/aws/04-create-rds.sh
bash scripts/aws/05-create-ec2.sh

# 2. Install Postfix on EC2
ssh -i ~/.ssh/mail-engine-key.pem ec2-user@<ELASTIC_IP> 'bash -s' < scripts/ec2/install-postfix.sh

# 3. Deploy app
DELIVERY_MODE=smtp SMTP_HOST=localhost SMTP_PORT=25 \
SMTP_AUTH_ENABLED=false SMTP_STARTTLS_ENABLED=false \
APP_BASE_URL=http://<ELASTIC_IP>:8080 \
UNSUBSCRIBE_HMAC_SECRET=$(openssl rand -hex 32) \
bash scripts/aws/06-deploy-app.sh

# 4. Bootstrap via API
BASE=http://<ELASTIC_IP>:8080

# Create tenant (no auth needed)
curl -X POST $BASE/api/tenants -H 'Content-Type: application/json' \
  -d '{"name":"My Company"}'   # → copy tenantId

# Create first API key (no auth needed)
curl -X POST $BASE/api/tenants/<tenantId>/api-keys -H 'Content-Type: application/json' \
  -d '{"name":"admin"}'        # → copy rawKey (shown ONCE)

# All subsequent requests require:  -H 'X-API-Key: <rawKey>'

# Add sending domain
curl -X POST $BASE/api/tenants/<tenantId>/domains \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"domainName":"mail.yourdomain.com"}'

# Get DNS records → add all 4 TXT records at your registrar
curl $BASE/api/tenants/<tenantId>/domains/<domainId>/dns-records \
  -H 'X-API-Key: <key>'

# After DNS propagates: verify domain
curl -X POST $BASE/api/tenants/<tenantId>/domains/<domainId>/verify \
  -H 'X-API-Key: <key>'

# Create IP pool
curl -X POST $BASE/api/tenants/<tenantId>/ip-pools \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"name":"default","trafficType":"bulk"}'

# Register outbound IP (note: path is /api/tenants/{tenantId}/ip-pools/{poolId}/ips)
curl -X POST $BASE/api/tenants/<tenantId>/ip-pools/<poolId>/ips \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"publicIpAddress":"<ELASTIC_IP>","elasticAllocationId":"<eipalloc-...>","reverseDnsName":"mail.yourdomain.com"}'

# Send test campaign
curl -X POST $BASE/api/campaigns \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"tenantId":"<tenantId>","domainId":"<domainId>","name":"Test","subject":"Hello","body":"<p>It works!</p>","recipientEmail":"you@example.com"}'
```

## Pending Actions Before First Email (live deployment)

1. **Add DNS records** at rissolv.com registrar (see memory/project_state.md for full record values)
2. **Request AWS port 25 unblocking** at https://aws.amazon.com/forms/ec2-email-limit-rdns-request (Account `334566771638`, region `us-east-1`) — AWS blocks outbound port 25 by default on all EC2 instances
3. **Verify domain** after DNS propagates: `POST http://3.208.157.146:8080/api/tenants/01abaedb.../domains/31a02389.../verify`

## GitHub Actions CI/CD

- `.github/workflows/deploy.yml`
- Tests run on every PR
- Auto-deploy on push to `main` — requires secrets `EC2_HOST` (`3.208.157.146`) and `EC2_SSH_KEY`

## What Is Not Implemented Yet

- Template rendering with variable substitution (body sent as-is)
- Warmup / rate limiting / hourly send quota
- Integration test suite (only a smoke test exists)
