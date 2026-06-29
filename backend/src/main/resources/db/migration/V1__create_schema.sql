CREATE TABLE tenant (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE sending_domain (
    id                   UUID        PRIMARY KEY,
    tenant_id            UUID        NOT NULL REFERENCES tenant(id),
    domain_name          VARCHAR(255) NOT NULL,
    verification_status  VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    verification_token   VARCHAR(255),
    dkim_selector        VARCHAR(100),
    dkim_public_key      TEXT,
    dkim_private_key_pem TEXT,
    created_at           TIMESTAMPTZ  NOT NULL,
    verified_at          TIMESTAMPTZ
);

CREATE TABLE ip_pool (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenant(id),
    name         VARCHAR(255) NOT NULL,
    traffic_type VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE outbound_ip (
    id                     UUID        PRIMARY KEY,
    tenant_id              UUID        NOT NULL REFERENCES tenant(id),
    ip_pool_id             UUID        NOT NULL REFERENCES ip_pool(id),
    public_ip_address      VARCHAR(50) NOT NULL,
    elastic_allocation_id  VARCHAR(100),
    reverse_dns_name       VARCHAR(255),
    status                 VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ NOT NULL
);

CREATE TABLE campaign (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenant(id),
    domain_id       UUID         NOT NULL REFERENCES sending_domain(id),
    name            VARCHAR(255) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    body            TEXT         NOT NULL,
    recipient_count INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE recipient (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    campaign_id UUID         NOT NULL REFERENCES campaign(id),
    email       VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE message_job (
    id              UUID         PRIMARY KEY,
    campaign_id     UUID         NOT NULL REFERENCES campaign(id),
    tenant_id       UUID         NOT NULL,
    domain_id       UUID         NOT NULL,
    recipient_id    UUID         NOT NULL,
    recipient_email VARCHAR(500) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    scheduled_at    TIMESTAMPTZ  NOT NULL,
    claimed_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX message_job_pending ON message_job (campaign_id, scheduled_at)
    WHERE status = 'PENDING';

CREATE TABLE suppression_record (
    id         UUID         PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    email      VARCHAR(500) NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX suppression_lookup ON suppression_record (tenant_id, lower(email));

CREATE TABLE outbound_message (
    id                 UUID         PRIMARY KEY,
    message_job_id     UUID,
    campaign_id        UUID,
    tenant_id          UUID,
    domain_id          UUID,
    ip_pool_id         UUID,
    outbound_ip_id     UUID,
    outbound_ip_address VARCHAR(50),
    recipient_email    VARCHAR(500),
    subject            VARCHAR(500),
    body               TEXT,
    delivery_status    VARCHAR(50),
    sent_at            TIMESTAMPTZ  NOT NULL
);
