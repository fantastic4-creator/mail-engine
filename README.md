# Mail Engine

This project is a marketing email platform built around a Java/Spring backend, Postgres as the durable state store, and a separate SMTP relay tier for production.

The system is designed for:
- multiple customer tenants
- multiple verified sending domains per tenant
- a practical first target of 100,000 recipients per day for one sending domain
- controlled warmup and throttling
- bounce, complaint, and unsubscribe handling
- future horizontal scaling only when the simpler design is no longer enough

## Chosen Architecture

This is the architecture we will proceed with.

At the highest level, the system has two operational divisions:

- Control plane: configuration, users, tenants, domains, campaigns, policies, and admin actions
- Data plane: actual campaign execution, message jobs, throttling, delivery handoff, and delivery feedback

Inside the Java codebase, we keep the data plane split into smaller packages so the responsibilities stay clear:

- data/state layer
- worker layer
- delivery layer

The user app and admin app are clients of the control plane. They are not the control plane by themselves.

```text
User App / Admin App
        |
Control Plane API
        |
Postgres / State Store
        |
Worker Plane
        |
Delivery Plane
        |
Postfix Relay Nodes
        |
Recipient Mail Servers
```

### Control Plane
The API surface that manages the platform.

Clients:
- customer user app
- internal admin app
- future public API clients

Responsibilities:
- authentication and authorization
- tenant management
- domain onboarding and verification
- campaign creation
- template management
- recipient list upload
- scheduling
- billing and quotas
- reporting and audit logs

### Data Plane
The state and coordination layer used by the workers.

Responsibilities:
- Postgres as the source of truth
- database-backed message jobs
- recipient imports
- persistence for campaign state, delivery state, suppression state, and feedback events

### Worker Plane
The execution layer that turns campaign configuration into sendable message work.

Responsibilities:
- claim pending message jobs
- split large campaigns into manageable batches
- render templates
- apply suppression and unsubscribe rules
- enforce warmup and pacing
- pause tenants/domains when bounce, complaint, or unsubscribe rates become unsafe
- hand approved messages to the delivery plane

### Delivery Plane
The outbound SMTP handoff layer.

Responsibilities:
- SMTP handoff
- Postfix / Exim / OpenSMTPD relay usage
- retry handling
- bounce and complaint ingestion
- source IP / relay-node routing

Production path:

```text
Spring Boot Worker -> Delivery Gateway -> Postfix on EC2 -> Elastic IP -> Recipient MX
```

## Recommended Technology Stack

Backend:
- Java 25 for the current scaffold
- Spring Boot
- Spring Web
- Spring Data JPA or JDBC
- Micrometer
- OpenTelemetry

Core infrastructure:
- Postgres
- local SMTP capture for development
- Postfix as SMTP relay
- Prometheus
- Grafana
- Loki

Optional later additions:
- Redis, only if database-backed pacing and job claiming become a bottleneck
- S3-compatible object storage, only when uploaded recipient files or assets become too large for direct database-backed handling

## Local Development Requirements

To run the first basic version locally, you should have:

- Java 25 JDK for the current scaffold
- Maven 3.9+ or Gradle 8+
- Docker Desktop or a compatible container runtime
- Postgres 16+
- A local SMTP capture tool such as Mailpit or MailHog
- Git
- An IDE such as IntelliJ IDEA or VS Code

Recommended for local file and asset testing:

- MinIO or another S3-compatible object store

For the first local loop, do not use a production relay or real outbound IPs. Use a local SMTP capture service so you can verify:

- message generation
- database-backed job processing
- retry logic
- bounce path stubs
- template rendering
- suppression checks

The current scaffold uses an in-memory local outbox before Mailpit/MailHog is introduced. The code path is still shaped like the production design: campaign creation creates per-recipient message jobs, the worker claims those jobs, suppression is checked, and the delivery gateway captures or queues the outbound message based on configuration.

## Minimal Local Environment

The smallest useful local stack is:

- 1 Spring Boot API
- 1 Postgres instance
- 1 local SMTP capture service

Optional additions:

- 1 worker process when the send loop is split out of the API
- 1 MinIO instance
- 1 observability stack

This is enough to exercise the control plane, message job flow, and message handoff without needing real delivery infrastructure.

## Suggested Environment Variables

At minimum, the backend will eventually need configuration like:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `OBJECT_STORAGE_ENDPOINT`
- `OBJECT_STORAGE_ACCESS_KEY`
- `OBJECT_STORAGE_SECRET_KEY`
- `OBJECT_STORAGE_BUCKET`

For local development, the SMTP settings should point to Mailpit or MailHog.

## How The System Works

1. A tenant registers and verifies ownership of one or more sending domains.
2. The tenant creates a campaign and uploads or selects recipients.
3. The control plane expands the campaign into per-recipient message jobs.
4. Jobs are persisted in Postgres.
5. A send loop claims due jobs and applies suppression, pacing, and warmup rules.
6. The send loop renders content and hands the message to the SMTP relay tier.
7. The relay tier delivers mail to recipient mail servers.
8. Bounce and complaint events are collected and written back to the database.
9. The system updates suppression lists, reputation state, and reporting metrics.

## Current Design Decisions

- Use Java and Spring for the backend.
- Keep the control plane, data plane, and delivery plane logically separated.
- Target AWS EC2 for the first production deployment.
- Use Elastic IPs only on SMTP relay nodes, not API nodes.
- Start with one tenant-level marketing pool.
- Allow multiple verified domains to send through the same tenant pool.
- Start with one IP in the pool when volume is low.
- Add more IPs to the same pool when capacity or isolation requires it.
- Do not use 1 IP per domain as the default model.
- Treat warmup as a sender-behavior problem, not a domain-count problem.
- Do not add Redis, Kafka, RabbitMQ, or S3 until a concrete local or production feature needs them.

## Domain And IP Policy

Default policy:
- one tenant
- one marketing pool
- one or more IPs in the pool
- multiple sender domains in the same pool
- each production outbound IP is an AWS Elastic IP with forward DNS and reverse DNS/PTR

When to split:
- different traffic types
- different complaint risk
- different list quality
- different business units
- different compliance boundaries

What the system must support:
- domain-level tracking
- tenant-level tracking
- IP-pool-level tracking
- automatic throttling
- automatic pause rules
- quarantine of bad domains or campaigns

## State And Coordination Strategy

For the 100,000-per-day target, use Postgres first. That is roughly 1.16 messages per second averaged across the day, and the system should deliberately pace sending rather than blast.

Use Postgres for:
- tenants
- domains
- IP pools
- outbound IPs
- campaigns
- recipients
- message jobs
- delivery attempts
- bounces
- complaints
- unsubscribes
- suppression records

Use database-backed claiming for message jobs first:
- `PENDING`
- `CLAIMED`
- `SENT`
- `FAILED`
- `SUPPRESSED`
- `RETRY_SCHEDULED`

Add Redis only later if:
- multiple worker nodes create enough database contention to matter
- per-domain rate counters need lower-latency distributed coordination
- suppression checks become too hot for indexed Postgres lookups
- delayed retries need a specialized scheduler

## Implementation Plan

### Phase 1: Foundation
- create the Spring Boot backend
- define the database schema
- build tenant and domain onboarding
- implement campaign creation
- implement recipient import
- add domain verification

### Phase 2: Database-Backed Jobs
- introduce recipient storage
- create one message job per recipient
- implement database-backed job claiming
- add basic retry handling
- add rate limiting and pacing using Postgres state

### Phase 3: SMTP Delivery
- configure Postfix or another MTA relay
- route the send loop output to the relay
- bind relay traffic to the correct outbound IP
- persist send results and failure reasons

### Phase 4: Feedback Loop
- process bounce messages
- process complaint signals
- maintain suppression lists
- support unsubscribe handling
- pause tenants or domains when thresholds are exceeded

### Phase 5: Scale And Hardening
- split send workers from the API if required
- add more IPs to the pool when needed
- introduce per-domain and per-provider throttling
- improve dashboards and alerting
- add replay-safe job handling
- add Redis only if the database-backed job and pacing model becomes a measured bottleneck

## What Still Needs To Be Added

- exact database schema
- message job claiming contract
- send loop retry policy
- warmup algorithm
- IP pool assignment policy
- bounce parser specification
- complaint ingestion path
- admin controls for pause/quarantine
- observability dashboards
- deployment manifests
- backup and restore plan

## Operating Rules

- Never rely on bursty send patterns as a protection strategy.
- Never use multiple domains to hide bad list quality.
- Stop or quarantine bad sending behavior early.
- Separate traffic types when reputation must be isolated.
- Keep the tenant, domain, and IP pool relationships explicit in the data model.

## Diagram

See the generated PNG diagram in this repository:

- [architecture-diagram.png](./architecture-diagram.png)

For AWS EC2 deployment constraints and the Elastic IP model, see:

- [AWS_EC2_DEPLOYMENT.md](./AWS_EC2_DEPLOYMENT.md)

For the exact mapping between architecture planes and Java packages, see:

- [ARCHITECTURE_MAPPING.md](./ARCHITECTURE_MAPPING.md)

## Next Step

The next useful artifact is the detailed Java/Spring project structure:
- packages
- modules
- entities
- services
- repositories
- send loop
- relay adapters
- deployment units
