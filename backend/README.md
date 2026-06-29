# Backend Quick Start

This is the first runnable Java/Spring scaffold for the mail engine.

It is currently built and verified with **JDK 25**.

The same codebase runs locally or in deployment mode through configuration:

- `MAIL_ENGINE_DELIVERY_MODE=local-outbox`
- `MAIL_ENGINE_DELIVERY_MODE=aws-smtp-relay`

## Build

From `backend/`:

```bash
mvn test
```

If your environment has a custom Maven mirror, you may need to point Maven at a writable local cache and bypass that mirror, for example:

```bash
mvn -s empty-settings.xml -gs empty-settings.xml -Dmaven.repo.local=./.m2 test
```

## Run

```bash
mvn spring-boot:run
```

The application starts on port `8080`.

The current local loop does not require MailHog yet. It uses an in-memory outbox so you can exercise:

- tenant creation
- domain onboarding
- domain verification
- tenant IP pool creation
- outbound Elastic IP registration
- suppression registration
- campaign creation
- message job inspection
- captured outbound message inspection

## Available Endpoints

- `GET /api/health`
- `GET /api/runtime`
- `POST /api/tenants`
- `GET /api/tenants`
- `GET /api/tenants/{tenantId}`
- `POST /api/tenants/{tenantId}/domains`
- `GET /api/tenants/{tenantId}/domains`
- `POST /api/tenants/{tenantId}/domains/{domainId}/verify`
- `POST /api/tenants/{tenantId}/ip-pools`
- `GET /api/tenants/{tenantId}/ip-pools`
- `POST /api/tenants/{tenantId}/ip-pools/{ipPoolId}/ips`
- `GET /api/tenants/{tenantId}/ip-pools/{ipPoolId}/ips`
- `POST /api/tenants/{tenantId}/suppressions`
- `GET /api/tenants/{tenantId}/suppressions`
- `POST /api/campaigns`
- `GET /api/campaigns`
- `GET /api/campaigns/{campaignId}`
- `GET /api/campaigns/{campaignId}/jobs`
- `GET /api/campaigns/outbox`

## Example Flow

1. Create a tenant.
2. Add a domain to that tenant.
3. Verify the domain.
4. Create a marketing IP pool.
5. Add an outbound Elastic IP entry to the pool.
6. Optionally add suppression entries.
7. Create a campaign with `recipientEmail` or `recipientEmails`.
8. Inspect message jobs.
9. Inspect the local outbox.

## Next Build Step

Add Postgres-backed repositories after the in-memory message-job flow is stable. Redis is intentionally deferred until there is a concrete rate-limit or worker-coordination bottleneck.

## Code Architecture Mapping

- Control plane: `com.mailengine.api`, `com.mailengine.service`
- Data state: `com.mailengine.data`, `com.mailengine.domain`
- Worker plane: `com.mailengine.worker`
- Delivery plane: `com.mailengine.delivery`

The worker and delivery code is generic. Runtime behavior is controlled by `mail-engine.runtime.*` properties in [application.yml](/Users/uttasing/dev/mydev/mail-engine/backend/src/main/resources/application.yml).
