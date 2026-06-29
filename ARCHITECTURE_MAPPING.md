# Architecture Mapping

This file maps the architecture terms to the current Java code.

## Current State

The project is still an early scaffold. The planes exist, but they are intentionally lightweight and configuration-driven:

- default configuration: runs fully on your machine with in-memory state and local captured delivery
- deployment configuration: changes runtime behavior through environment variables or application properties

## Control Plane

The control plane is the user/admin API surface.

Current code:

- `backend/src/main/java/com/mailengine/api`
- `backend/src/main/java/com/mailengine/service`

Responsibilities currently implemented:

- health/runtime inspection
- tenant creation
- sending domain registration
- domain verification
- tenant IP pool registration
- outbound IP registration
- campaign creation
- suppression list registration
- outbox inspection

## Data Plane

The data plane is the state layer plus the send-execution path. In the code, that is split into data state, domain records, worker logic, and delivery handoff.

Current code:

- `backend/src/main/java/com/mailengine/data`
- `backend/src/main/java/com/mailengine/domain`

Current implementation:

- `PlatformStateStore`
- `InMemoryPlatformStateStore`

Current state records:

- tenants
- sending domains
- IP pools
- outbound IPs
- campaigns
- recipients
- message jobs
- suppression records
- outbound messages

Planned next implementation:

- Postgres-backed repositories
- database-backed message jobs
- delivery attempts
- suppression records
- bounce and complaint records

Redis is not part of the current required data plane. It is deferred until there is a measured coordination bottleneck.

## Worker Plane

The worker plane is the send loop that processes campaign/message work.

Current code:

- `backend/src/main/java/com/mailengine/worker`

Current implementation:

- `CampaignSendLoop`
- `DefaultCampaignSendLoop`

In the current scaffold, this runs synchronously so the app is easy to run and test locally. The flow is still worker-shaped:

```text
Campaign -> Recipient records -> MessageJob records -> worker claim -> suppression check -> delivery gateway
```

Planned next implementation:

- database-backed message-job claiming
- retry scheduling
- pacing/warmup rules
- optional split into a separate Spring Boot worker process

## Delivery Plane

The delivery plane is the final handoff to the delivery mechanism.

Current code:

- `backend/src/main/java/com/mailengine/delivery`

Current implementation:

- `DeliveryGateway`
- `ConfigurableDeliveryGateway`

Behavior is controlled by `mail-engine.runtime.delivery-mode`:

- `local-outbox`: capture messages locally
- `aws-smtp-relay`: mark messages as queued for the future EC2/Postfix relay path

Planned production implementation:

```text
CampaignSendLoop -> ConfigurableDeliveryGateway -> internal SMTP relay -> Postfix EC2 -> Elastic IP -> recipient MX
```

## Why The Code Is Split This Way

The default local configuration should stay cheap and runnable:

```text
API -> in-memory state store -> local send loop -> local outbox
```

The deployment configuration should evolve without changing the business API:

```text
API -> Postgres -> send loop -> SMTP relay adapter -> Postfix on EC2
```

That is why the current code uses interfaces around the send loop and delivery gateway even though the current local implementation is still simple. It should not require separate local-only and AWS-only business code.
