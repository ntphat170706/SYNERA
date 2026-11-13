CREATE TABLE credit_limit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 limit_amount DECIMAL(18,2) NOT NULL,
 balance DECIMAL(18,2) NOT NULL,
 overdue_amount DECIMAL(18,2) NOT NULL,
 overdue_days_threshold INT NOT NULL DEFAULT 0,
 UNIQUE (npp_id),
 INDEX ix_credit_limit_scope (npp_id,created_at),
 CHECK(limit_amount>=0)
);
CREATE TABLE invoice (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 order_id BIGINT,
 amount DECIMAL(18,2) NOT NULL,
 remaining DECIMAL(18,2) NOT NULL,
 due_date DATE NOT NULL,
 status VARCHAR(255) NOT NULL,
 UNIQUE (order_id),
 INDEX ix_invoice_scope (npp_id,created_at)
);
CREATE TABLE payment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 reference VARCHAR(255) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 status VARCHAR(255) NOT NULL,
 UNIQUE (npp_id,reference),
 INDEX ix_payment_scope (npp_id,created_at)
);
CREATE TABLE credit_ledger (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 doc_type VARCHAR(255) NOT NULL,
 doc_id BIGINT NOT NULL,
 debit DECIMAL(18,2) NOT NULL,
 credit DECIMAL(18,2) NOT NULL,
 balance_after DECIMAL(18,2) NOT NULL,
 posted_at TIMESTAMP(3) NOT NULL,
 UNIQUE (doc_type,doc_id),
 INDEX ix_credit_ledger_scope (npp_id,created_at)
);
CREATE TABLE sell_in_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 code VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 subtotal DECIMAL(18,2) NOT NULL,
 discount DECIMAL(18,2) NOT NULL,
 total DECIMAL(18,2) NOT NULL,
 hold_reason VARCHAR(255),
 expected_delivery DATE,
 submitted_at TIMESTAMP(3),
 delivered_at TIMESTAMP(3),
 idempotency_key VARCHAR(255) NOT NULL,
 UNIQUE (code),
 UNIQUE (npp_id,idempotency_key),
 INDEX ix_sell_in_order_scope (npp_id,created_at)
);
CREATE TABLE sell_in_order_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 order_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 uom VARCHAR(255) NOT NULL,
 qty_ordered DECIMAL(18,3) NOT NULL,
 qty_confirmed DECIMAL(18,3) NOT NULL,
 qty_shipped DECIMAL(18,3) NOT NULL,
 qty_received DECIMAL(18,3) NOT NULL,
 qty_damaged DECIMAL(18,3) NOT NULL,
 qty_returned DECIMAL(18,3) NOT NULL,
 price DECIMAL(18,2) NOT NULL,
 discount DECIMAL(18,2) NOT NULL,
 base_factor DECIMAL(18,3) NOT NULL,
 UNIQUE (order_id,sku_id,uom)
);
CREATE TABLE order_status_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 order_id BIGINT NOT NULL,
 status VARCHAR(255) NOT NULL,
 actor_id BIGINT NOT NULL,
 note TEXT,
 occurred_at TIMESTAMP(3) NOT NULL
);
