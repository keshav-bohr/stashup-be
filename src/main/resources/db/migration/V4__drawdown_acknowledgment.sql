-- A user's confirmation that a period's gap is explained by money held before the period.
--
-- acknowledged_gap_minor records the gap AT THE MOMENT OF ACKNOWLEDGMENT, not an open licence.
-- The user acknowledged a 50,000 drawdown, not an unlimited one: if the gap later grows beyond
-- what they confirmed, the period flags again (FR-028).

CREATE TABLE drawdown_acknowledgment (
    id                     BINARY(16)  NOT NULL,
    user_id                BINARY(16)  NOT NULL,
    period_start           DATE        NOT NULL,
    acknowledged_gap_minor BIGINT      NOT NULL,
    acknowledged_at        DATETIME(6) NOT NULL,
    CONSTRAINT pk_drawdown_acknowledgment PRIMARY KEY (id),
    CONSTRAINT uq_drawdown_user_period UNIQUE (user_id, period_start),
    CONSTRAINT fk_drawdown_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_drawdown_gap_non_negative CHECK (acknowledged_gap_minor >= 0)
) ENGINE = InnoDB;
