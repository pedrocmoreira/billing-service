CREATE TABLE budgets (
    id                   BIGSERIAL PRIMARY KEY,
    service_order_id     BIGINT         NOT NULL UNIQUE,
    service_order_number VARCHAR(30)    NOT NULL,
    public_token         VARCHAR(36)    NOT NULL UNIQUE,
    customer_name        VARCHAR(150)   NOT NULL,
    customer_email       VARCHAR(150),
    vehicle_description  VARCHAR(255),
    diagnosis_notes      TEXT,
    status               VARCHAR(20)    NOT NULL,
    total_amount         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMP      NOT NULL,
    decided_at           TIMESTAMP
);

CREATE TABLE budget_items (
    id          BIGSERIAL PRIMARY KEY,
    budget_id   BIGINT         NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    type        VARCHAR(10)    NOT NULL,
    description VARCHAR(255)   NOT NULL,
    quantity    INTEGER        NOT NULL,
    unit_price  NUMERIC(10, 2) NOT NULL
);

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    budget_id           BIGINT         NOT NULL UNIQUE REFERENCES budgets(id),
    provider            VARCHAR(30)    NOT NULL,
    preference_id       VARCHAR(255),
    checkout_url        VARCHAR(500),
    external_payment_id VARCHAR(100),
    status              VARCHAR(20)    NOT NULL,
    status_detail       VARCHAR(100),
    amount              NUMERIC(12, 2) NOT NULL,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP
);
CREATE INDEX idx_payments_external ON payments(external_payment_id);
