-- One row per pair of users, canonically ordered so that (A,B) and (B,A) are the same row.
--
-- Canonical ordering plus the unique constraint resolves the simultaneous-mutual-request race at
-- the database rather than in application code: when two users request each other at the same
-- moment, the second insert fails and the application reads that failure as an acceptance.
--
-- The symmetric fact (these two are connected) has exactly one row; the asymmetric facts (who
-- asked, who blocked) are separate columns, so no state transition has to keep two halves in sync.

CREATE TABLE friendship (
    id                    BINARY(16)  NOT NULL,
    -- Always the numerically lower of the two identifiers.
    user_a_id             BINARY(16)  NOT NULL,
    user_b_id             BINARY(16)  NOT NULL,
    status                VARCHAR(12) NOT NULL,
    initiated_by_user_id  BINARY(16)  NOT NULL,
    blocked_by_user_id    BINARY(16)  NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT pk_friendship PRIMARY KEY (id),
    CONSTRAINT uq_friendship_pair UNIQUE (user_a_id, user_b_id),
    CONSTRAINT fk_friendship_user_a FOREIGN KEY (user_a_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_friendship_user_b FOREIGN KEY (user_b_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED', 'BLOCKED')),
    CONSTRAINT ck_friendship_distinct CHECK (user_a_id <> user_b_id),
    -- blocked_by is set if and only if the pair is blocked.
    CONSTRAINT ck_friendship_blocked_by CHECK (
        (status = 'BLOCKED' AND blocked_by_user_id IS NOT NULL)
        OR (status <> 'BLOCKED' AND blocked_by_user_id IS NULL))
) ENGINE = InnoDB;

CREATE INDEX ix_friendship_a_status ON friendship (user_a_id, status);
CREATE INDEX ix_friendship_b_status ON friendship (user_b_id, status);
