# Local Setup Guide

This document covers the smallest useful local environment for the current phase of the mail engine project.

The goal is not production delivery yet. The goal is to get a working local loop where you can:

- start the backend
- verify a domain
- create a test campaign
- create a captured local message
- send it to a local SMTP capture service
- inspect the captured email

The app defaults to local behavior through application properties. No separate local-only code path is required. By default, messages are captured in the in-memory outbox instead of being handed to a real SMTP relay.

## Current Assumptions

You already have:

- JDK 25
- IntelliJ IDEA or VS Code
- Docker Desktop or another container runtime
- a Spring Boot capable development environment

You do not yet have:

- Mailpit or MailHog
- the actual application code

## Recommended Local Stack

Use these components locally:

- Java backend: Spring Boot
- Database: Postgres
- SMTP capture: Mailpit or MailHog
- Optional object store: MinIO

Redis is not required for the current local target. Add it only when we implement a feature that needs distributed low-latency coordination.

## Important Note About Java Version

JDK 25 is installed on your machine, but the backend should be coded against a version that is compatible with the Spring Boot release you choose.

Practical recommendation:

- keep JDK 25 installed for the machine
- use the project’s configured Java version in the build tool
- for the current scaffold, the project targets Java 25 and has been verified under that JDK
- if you later want to backport to an older JDK, do that as a deliberate compatibility step

Do not let the local JDK version drive the architecture. Let the Spring Boot compatibility and build tooling drive it.

## What We Need For The First Runnable Version

The first runnable local version should support:

1. tenant creation
2. domain registration
3. domain verification record storage
4. campaign creation
5. recipient list upload
6. one database-backed message job per recipient
7. one send loop claiming due jobs
8. SMTP handoff to Mailpit or MailHog
9. captured email visible in the local SMTP UI

## Local Development Steps

### Step 1: Install or verify the required tools

Make sure the following are available:

- `java -version`
- `docker version`
- `git --version`
- IDE tooling

### Step 2: Run local infrastructure

Start these containers or services locally:

- Postgres
- Mailpit or MailHog
- optional MinIO

### Step 3: Create the Spring Boot project

Create a new backend project with:

- Spring Web
- Spring Validation
- Spring Data JPA
- a Postgres driver
- a test stack

Keep the first version small and modular.

### Step 4: Build the first domain model

Add the core entities:

- Tenant
- SendingDomain
- Campaign
- Recipient
- MessageJob
- DeliveryAttempt
- SuppressionEntry
- BounceEvent

### Step 5: Add the basic API

Add endpoints for:

- create tenant
- add domain
- verify domain
- create campaign
- upload recipients
- start campaign
- pause campaign
- fetch campaign status

### Step 6: Add the local job flow

The first send loop should:

- claim due message jobs from Postgres
- render a message body
- check suppression
- send to the SMTP capture service
- mark the job as delivered locally

### Step 7: Connect to the SMTP capture service

Configure the send loop to send mail to Mailpit or MailHog instead of the public internet.

This lets you validate:

- message content
- headers
- recipient handling
- job execution
- failure handling

### Step 8: Add basic persistence

Persist the following in Postgres:

- tenant records
- domain records
- campaign records
- message job status
- delivery attempts
- suppression entries

### Step 9: Verify the end-to-end path

Your first end-to-end test should prove:

- tenant created
- domain stored
- campaign created
- recipients imported
- job persisted
- send loop processed job
- email appears in Mailpit or MailHog

## Suggested Project Layout

```text
mail-engine/
  backend/
    src/main/java/...
    src/main/resources/
    src/test/java/...
  infra/
    docker-compose.yml
  docs/
    LOCAL_SETUP.md
```

## Initial Development Order

1. Bring up Postgres locally.
2. Add the Spring Boot backend.
3. Add the database schema.
4. Add domain verification storage.
5. Add campaign and recipient import.
6. Add database-backed jobs and the send loop.
7. Connect Mailpit or MailHog.
8. Confirm one local email delivery path.

## What To Defer For Now

Do not start with:

- real outbound IPs
- real DNS automation
- bounce processing from live inboxes
- reputation warmup logic
- multi-IP routing
- production monitoring

Those come after the basic local loop works.

## Next Step After This

After the local loop works, we should add:

- a `docker-compose.yml`
- the Spring Boot project skeleton
- the first database migration
- the first send loop
- local SMTP wiring
