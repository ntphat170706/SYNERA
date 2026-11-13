CREATE TABLE demand_plan (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 plan_month DATE NOT NULL,
 status VARCHAR(255) NOT NULL,
 lines_json TEXT NOT NULL,
 note TEXT,
 INDEX ix_demand_plan_scope (npp_id,created_at)
);
CREATE TABLE sell_out_daily (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 business_day DATE NOT NULL,
 source VARCHAR(255) NOT NULL,
 qty DECIMAL(18,3) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 UNIQUE (npp_id,sku_id,business_day,source),
 INDEX ix_sell_out_daily_scope (npp_id,created_at)
);
CREATE TABLE ticket (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT,
 order_id BIGINT,
 line_id BIGINT,
 title VARCHAR(255) NOT NULL,
 category VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL,
 priority VARCHAR(255) NOT NULL,
 owner_id BIGINT NOT NULL,
 due_at TIMESTAMP(3) NOT NULL,
 escalated BOOLEAN NOT NULL DEFAULT 0,
 description TEXT NOT NULL,
 resolution TEXT,
 INDEX ix_ticket_scope (npp_id,created_at)
);
CREATE TABLE ticket_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 ticket_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 status VARCHAR(255) NOT NULL,
 note TEXT,
 occurred_at TIMESTAMP(3) NOT NULL
);
CREATE TABLE budget_allocation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(255) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 committed DECIMAL(18,2) NOT NULL,
 period_start DATE NOT NULL,
 period_end DATE NOT NULL
);
CREATE TABLE trade_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 budget_id BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL,
 requested DECIMAL(18,2) NOT NULL,
 approved DECIMAL(18,2) NOT NULL,
 status VARCHAR(255) NOT NULL,
 reason TEXT,
 INDEX ix_trade_request_scope (npp_id,created_at)
);
CREATE TABLE market_feedback (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 topic VARCHAR(255) NOT NULL,
 description TEXT NOT NULL,
 ticket_id BIGINT,
 INDEX ix_market_feedback_scope (npp_id,created_at)
);
CREATE TABLE target (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 period_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 reward_pct DECIMAL(18,2) NOT NULL,
 UNIQUE (npp_id,period_id),
 INDEX ix_target_scope (npp_id,created_at)
);
