-- Materialised monthly totals and the derived score.
--
-- Granularity is ALWAYS one month. Yearly figures are computed on read by summing these rows --
-- never stored, and never derived by averaging monthly scores. Averaging twelve percentages
-- weights a month with 500 of income equally against a month with 50,000, which is a different
-- and wrong number.
--
-- Why materialise at all: the comparison view is the deciding case. Fifty friends plus the user,
-- computed on read, is 51 aggregations over financial_entry on the product's most-visited screen.
-- Materialised, it is one indexed range scan over 51 small rows.
--
-- The score columns are nullable from the outset so that adding scoring (User Story 2) and
-- reconciliation (User Story 3) needs no schema change.

CREATE TABLE period_summary (
    id                   BINARY(16)  NOT NULL,
    user_id              BINARY(16)  NOT NULL,
    -- First day of the month this row summarises.
    period_start         DATE        NOT NULL,
    currency             CHAR(3)     NOT NULL,

    money_in_minor       BIGINT      NOT NULL DEFAULT 0,
    expense_minor        BIGINT      NOT NULL DEFAULT 0,
    -- Net of withdrawals, so these two may legitimately be negative.
    saving_net_minor     BIGINT      NOT NULL DEFAULT 0,
    investment_net_minor BIGINT      NOT NULL DEFAULT 0,
    deduction_minor      BIGINT      NOT NULL DEFAULT 0,

    -- max(0, saving_net + investment_net): a negative net month contributes nothing, never a
    -- negative score.
    stashed_minor        BIGINT      NOT NULL DEFAULT 0,
    outflow_minor        BIGINT      NOT NULL DEFAULT 0,
    -- max(0, outflow - money_in). Non-zero means the period does not account for itself.
    gap_minor            BIGINT      NOT NULL DEFAULT 0,

    -- Basis points, 0-10000. Ranking uses this rather than the rounded score so that two users
    -- at 30.4% and 30.6% -- both displaying 30 -- still order deterministically.
    proportion_bp        INT         NULL,
    -- SMALLINT rather than TINYINT UNSIGNED: the range saving is irrelevant and MySQL's
    -- unsigned tinyint does not round-trip cleanly to a Java short, which Hibernate rejects at
    -- schema validation. The CHECK below enforces the real 0..100 range.
    score                SMALLINT    NULL,
    band                 VARCHAR(16) NULL,
    completeness         VARCHAR(20) NOT NULL,

    entry_count          INT         NOT NULL DEFAULT 0,
    computed_at          DATETIME(6) NOT NULL,

    CONSTRAINT pk_period_summary PRIMARY KEY (id),
    CONSTRAINT uq_period_summary_user_period UNIQUE (user_id, period_start),
    CONSTRAINT fk_period_summary_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_period_summary_proportion CHECK (
        proportion_bp IS NULL OR (proportion_bp >= 0 AND proportion_bp <= 10000)),
    CONSTRAINT ck_period_summary_score CHECK (
        score IS NULL OR (score >= 0 AND score <= 100)),
    CONSTRAINT ck_period_summary_completeness CHECK (
        completeness IN ('COMPLETE', 'UNRECONCILED', 'INSUFFICIENT_DATA')),
    CONSTRAINT ck_period_summary_stashed CHECK (stashed_minor >= 0),
    CONSTRAINT ck_period_summary_gap CHECK (gap_minor >= 0)
) ENGINE = InnoDB;

-- The unique key above serves the owner's single-period read, the comparison view's
-- (user_id IN (...) AND period_start = ?), and the streak lookback's
-- (user_id IN (...) AND period_start BETWEEN ? AND ?).
