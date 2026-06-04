-- ============================================================
--  BangKu Banking System — Database Initialization
-- ============================================================

-- SCHEMA
CREATE SCHEMA IF NOT EXISTS bangku;

-- ─── ACCOUNTS ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(20)    UNIQUE NOT NULL,
    owner_name      VARCHAR(100)   NOT NULL,
    email           VARCHAR(150)   UNIQUE NOT NULL,
    phone           VARCHAR(20),
    balance         NUMERIC(18,2)  NOT NULL DEFAULT 0.00,
    account_type    VARCHAR(20)    NOT NULL CHECK (account_type IN ('SAVINGS','CHECKING','BUSINESS')),
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ─── TRANSACTIONS ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
    id                  BIGSERIAL PRIMARY KEY,
    transaction_ref     VARCHAR(36)   UNIQUE NOT NULL,
    from_account_id     BIGINT        REFERENCES accounts(id),
    to_account_id       BIGINT        REFERENCES accounts(id),
    amount              NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    transaction_type    VARCHAR(30)   NOT NULL CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER','PAYMENT')),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCESS','FAILED','REVERSED')),
    description         TEXT,
    fee                 NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP
);

-- ─── AUDIT LOG ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    changed_by  VARCHAR(100),
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── INDEXES ───────────────────────────────────────────────────
CREATE INDEX idx_transactions_from_account  ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_account    ON transactions(to_account_id);
CREATE INDEX idx_transactions_type_status   ON transactions(transaction_type, status);
CREATE INDEX idx_transactions_created_at    ON transactions(created_at DESC);
CREATE INDEX idx_audit_entity               ON audit_logs(entity_type, entity_id);

-- ─── SEED DATA ─────────────────────────────────────────────────
INSERT INTO accounts (account_number, owner_name, email, phone, balance, account_type) VALUES
  ('BK-0001-2024', 'Budi Santoso',    'budi@example.com',    '081234567890', 5000000.00,  'SAVINGS'),
  ('BK-0002-2024', 'Siti Rahayu',     'siti@example.com',    '081234567891', 12500000.00, 'CHECKING'),
  ('BK-0003-2024', 'Ahmad Wijaya',    'ahmad@example.com',   '081234567892', 750000.00,   'SAVINGS'),
  ('BK-0004-2024', 'Dewi Anggraini',  'dewi@example.com',    '081234567893', 99000000.00, 'BUSINESS'),
  ('BK-0005-2024', 'Riko Pratama',    'riko@example.com',    '081234567894', 3200000.00,  'SAVINGS');

INSERT INTO transactions (transaction_ref, from_account_id, to_account_id, amount, transaction_type, status, description, fee, processed_at) VALUES
  (gen_random_uuid()::text, NULL, 1, 1000000.00, 'DEPOSIT',    'SUCCESS', 'Initial deposit',          0.00, NOW()),
  (gen_random_uuid()::text, NULL, 2, 5000000.00, 'DEPOSIT',    'SUCCESS', 'Initial deposit',          0.00, NOW()),
  (gen_random_uuid()::text, 1,    2, 500000.00,  'TRANSFER',   'SUCCESS', 'Transfer to Siti',      2500.00, NOW()),
  (gen_random_uuid()::text, 2,    3, 200000.00,  'TRANSFER',   'SUCCESS', 'Transfer to Ahmad',     1000.00, NOW()),
  (gen_random_uuid()::text, 1,    NULL,150000.00,'WITHDRAWAL', 'SUCCESS', 'ATM withdrawal',           0.00, NOW());

-- ─── USEFUL NATIVE SQL VIEWS (used by repositories) ────────────
CREATE OR REPLACE VIEW v_account_summary AS
SELECT
    a.id,
    a.account_number,
    a.owner_name,
    a.email,
    a.balance,
    a.account_type,
    a.status,
    COUNT(DISTINCT t_out.id)            AS total_outgoing,
    COUNT(DISTINCT t_in.id)             AS total_incoming,
    COALESCE(SUM(t_out.amount), 0)      AS total_debited,
    COALESCE(SUM(t_in.amount),  0)      AS total_credited,
    COALESCE(SUM(t_out.fee),    0)      AS total_fees_paid
FROM accounts a
LEFT JOIN transactions t_out ON t_out.from_account_id = a.id AND t_out.status = 'SUCCESS'
LEFT JOIN transactions t_in  ON t_in.to_account_id    = a.id AND t_in.status  = 'SUCCESS'
GROUP BY a.id;
