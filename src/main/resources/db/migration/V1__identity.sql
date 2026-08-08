-- Identity, session, and idempotency tables.
-- Forward-only: never edit this file after it has been applied anywhere.
--
-- Identifiers are BINARY(16) holding UUIDv7. UUIDv7 is time-ordered, so the InnoDB clustered
-- index stays near-sequential -- the insert locality of an auto-increment without exposing a
-- guessable, enumerable integer to clients.

CREATE TABLE app_user (
    id                 BINARY(16)   NOT NULL,
    email              VARCHAR(320) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    display_name       VARCHAR(50)  NOT NULL,
    -- Immutable after registration: changing it would silently reinterpret every stored amount.
    base_currency      CHAR(3)      NOT NULL,
    timezone           VARCHAR(64)  NOT NULL,
    -- Lockout state lives here, not in process memory, so it is exact across all instances.
    failed_login_count INT          NOT NULL DEFAULT 0,
    locked_until       DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_failed_login CHECK (failed_login_count >= 0)
) ENGINE = InnoDB;

CREATE INDEX ix_app_user_display_name ON app_user (display_name);

CREATE TABLE refresh_token (
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    -- Only the hash is stored; the token itself never touches the database.
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id, revoked_at);
CREATE INDEX ix_refresh_token_expiry ON refresh_token (expires_at);

CREATE TABLE idempotency_record (
    id                   BINARY(16)  NOT NULL,
    user_id              BINARY(16)  NOT NULL,
    idempotency_key      VARCHAR(64) NOT NULL,
    -- Same key + same fingerprint replays; same key + different fingerprint is a 409.
    request_fingerprint  CHAR(64)    NOT NULL,
    response_status      SMALLINT    NOT NULL,
    response_body        JSON        NULL,
    created_at           DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX ix_idempotency_created ON idempotency_record (created_at);
