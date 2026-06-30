CREATE TABLE api_key (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenant(id),
    name         VARCHAR(255) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix   VARCHAR(20)  NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX api_key_hash_idx ON api_key (key_hash);
