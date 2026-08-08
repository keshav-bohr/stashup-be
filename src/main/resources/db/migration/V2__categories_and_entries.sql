-- Categories and financial entries.
--
-- Two choices here are load-bearing and easy to undo by accident:
--
-- 1. entry_date is DATE, not DATETIME. The date a user assigns to a transaction is a fact about
--    their calendar, not a point on a timeline. Storing it without a time component removes
--    timezone ambiguity from period membership entirely: a month is a plain BETWEEN, with no
--    conversion and no dependence on the server's zone or the user's current setting.
--
-- 2. amount_minor is always POSITIVE. Direction carries the sign. A signed amount column invites
--    a missing ABS() somewhere to silently flip a deposit into a withdrawal.

CREATE TABLE category (
    id         BINARY(16)  NOT NULL,
    -- System categories are owned by the nil UUID, not NULL. MySQL treats NULLs as distinct in a
    -- unique index, so a nullable owner would permit duplicate system category names.
    user_id    BINARY(16)  NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_owner_type_name UNIQUE (user_id, entry_type, name),
    CONSTRAINT ck_category_entry_type CHECK (
        entry_type IN ('INCOME', 'EXPENSE', 'SAVING', 'INVESTMENT', 'DEDUCTION'))
) ENGINE = InnoDB;

CREATE INDEX ix_category_owner_type ON category (user_id, entry_type);

CREATE TABLE financial_entry (
    id           BINARY(16)   NOT NULL,
    user_id      BINARY(16)   NOT NULL,
    entry_type   VARCHAR(16)  NOT NULL,
    direction    VARCHAR(12)  NOT NULL,
    amount_minor BIGINT       NOT NULL,
    currency     CHAR(3)      NOT NULL,
    entry_date   DATE         NOT NULL,
    category_id  BINARY(16)   NOT NULL,
    note         VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_financial_entry PRIMARY KEY (id),
    CONSTRAINT fk_entry_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_entry_category FOREIGN KEY (category_id)
        REFERENCES category (id),
    CONSTRAINT ck_entry_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_entry_type CHECK (
        entry_type IN ('INCOME', 'EXPENSE', 'SAVING', 'INVESTMENT', 'DEDUCTION')),
    CONSTRAINT ck_entry_direction CHECK (direction IN ('CONTRIBUTION', 'WITHDRAWAL')),
    -- Withdrawals only make sense for savings and investments (FR-009).
    CONSTRAINT ck_entry_withdrawal_type CHECK (
        direction = 'CONTRIBUTION' OR entry_type IN ('SAVING', 'INVESTMENT'))
) ENGINE = InnoDB;

-- Period aggregation and keyset pagination. Leads with user_id so no query can be written that
-- scans across owners.
CREATE INDEX ix_entry_user_date ON financial_entry (user_id, entry_date DESC, id DESC);
CREATE INDEX ix_entry_user_type_date ON financial_entry (user_id, entry_type, entry_date);
CREATE INDEX ix_entry_user_category_date ON financial_entry (user_id, category_id, entry_date);

-- System categories, owned by the nil UUID.
SET @system_owner = UNHEX(REPEAT('0', 32));

INSERT INTO category (id, user_id, entry_type, name, created_at) VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INCOME',     'Salary',            NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INCOME',     'Freelance',         NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INCOME',     'Bonus',             NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INCOME',     'Interest',          NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INCOME',     'Gift',              NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INCOME',     'Other Income',      NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Groceries',         NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Rent',              NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Utilities',         NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Transport',         NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Dining Out',        NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Healthcare',        NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Entertainment',     NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Shopping',          NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'EXPENSE',    'Other Expense',     NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'SAVING',     'Emergency Fund',    NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'SAVING',     'Savings Account',   NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'SAVING',     'Fixed Deposit',     NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INVESTMENT', 'Mutual Funds',      NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INVESTMENT', 'Stocks',            NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INVESTMENT', 'Retirement',        NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'INVESTMENT', 'Other Investment',  NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'DEDUCTION',  'Income Tax',        NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'DEDUCTION',  'Provident Fund',    NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'DEDUCTION',  'Loan Repayment',    NOW(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), @system_owner, 'DEDUCTION',  'Insurance Premium', NOW(6));
