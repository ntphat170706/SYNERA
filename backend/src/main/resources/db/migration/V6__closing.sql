CREATE TABLE report_period (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 type VARCHAR(255) NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 status VARCHAR(255) NOT NULL,
 closed_by BIGINT,
 locked_at TIMESTAMP(3),
 UNIQUE (type,start_date)
);
CREATE TABLE period_snapshot (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 period_id BIGINT NOT NULL,
 npp_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 debt DECIMAL(18,2) NOT NULL,
 captured_at TIMESTAMP(3) NOT NULL,
 UNIQUE (period_id,npp_id),
 INDEX ix_period_snapshot_scope (npp_id,created_at)
);
CREATE TABLE reconciliation_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 line_id BIGINT,
 kind VARCHAR(255) NOT NULL,
 description TEXT NOT NULL,
 status VARCHAR(255) NOT NULL,
 owner_id BIGINT NOT NULL,
 UNIQUE (order_id,line_id,kind),
 INDEX ix_reconciliation_item_scope (npp_id,created_at)
);
CREATE TABLE adjustment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 original_period_id BIGINT NOT NULL,
 posted_period_id BIGINT,
 npp_id BIGINT NOT NULL,
 delta DECIMAL(18,2) NOT NULL,
 reason TEXT NOT NULL,
 status VARCHAR(255) NOT NULL,
 approved_by BIGINT,
 INDEX ix_adjustment_scope (npp_id,created_at)
);
