# AWS EC2 Deployment Notes

This document captures the target AWS EC2 deployment shape for the mail engine.

## Important AWS Constraints

Before using EC2 for outbound email, account for these AWS rules:

- EC2 outbound traffic on port 25 to public addresses is blocked by default. You must request removal of this restriction before sending real SMTP traffic from EC2.
- Elastic IP addresses are static public IPv4 addresses. They are allocated to your AWS account until you release them.
- AWS charges for public IPv4 addresses, including Elastic IPs.
- The default Elastic IP quota is 5 per Region. Request a quota increase before you need more.
- For email sending, AWS recommends provisioning Elastic IPs and assigning static reverse DNS records to the Elastic IPs used for sending.
- Reverse DNS requires a matching forward DNS `A` record that points to the Elastic IP.

References:

- [EC2 service quotas and port 25 restriction](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-resource-limits.html)
- [Elastic IP address basics, pricing, and quota](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/elastic-ip-addresses-eip.html)
- [Create reverse DNS for email on EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Elastic_Addressing_Reverse_DNS.html)

## Target EC2 Architecture

Use separate deployment roles:

- API EC2 instances for the Spring Boot control plane
- optional worker EC2 instances for campaign expansion, throttling, and delivery orchestration when the API node is no longer enough
- SMTP relay EC2 instances running Postfix, Exim, or OpenSMTPD
- RDS Postgres for durable state
- optional ElastiCache Redis only if database-backed pacing and job claiming become a measured bottleneck
- optional S3 for imports, exports, and future campaign assets
- CloudWatch plus Prometheus/Grafana/Loki-style observability

## SMTP Relay Layer

The SMTP relay nodes are the only machines that should own outbound sending IPs.

Recommended pattern:

```text
Spring send loop -> internal SMTP relay hostname -> Postfix relay node -> Elastic IP -> recipient MX
```

Keep public SMTP delivery on the relay tier. The API creates tenants, domains, campaigns, and policies. The send loop claims database-backed jobs and hands messages to the relay.

## Elastic IP Model

Model Elastic IPs as outbound IPs inside a tenant IP pool.

For a tenant:

```text
tenant
  ip pool: marketing-primary
    outbound ip: 203.0.113.10 / eipalloc-...
    outbound ip: 203.0.113.11 / eipalloc-...
  domains:
    mail.customer.com
    offers.customer.com
```

Do not default to one IP per domain. Default to one tenant marketing pool, then add IPs when volume or isolation requires it.

## DNS Requirements Per Sending IP

For every Elastic IP used for SMTP delivery:

1. Create a forward `A` record, for example:

   ```text
   mail1.customer-sending.example.com -> 203.0.113.10
   ```

2. Set the Elastic IP reverse DNS/PTR to the same hostname.
3. Ensure Postfix uses a matching SMTP hostname.
4. Add SPF, DKIM, and DMARC for the customer sending domain.

## What The Current Code Now Represents

The current local code has these concepts:

- tenant
- verified sending domain
- tenant IP pool
- outbound IP with Elastic IP allocation ID and reverse DNS name
- campaign
- in-memory outbox

Campaign creation now requires:

- a verified domain
- at least one active outbound IP for the tenant

This keeps the local scaffold aligned with the future EC2 relay model without requiring real EC2 infrastructure locally.

## Next Infrastructure Step

Add local Docker services:

- Postgres
- Mailpit or MailHog

Then replace the in-memory store with Postgres-backed repositories while keeping the same API model.
