-- Fresh MySQL installations only. Historical H2 migrations are preserved.
-- Source: V1__iam.sql
CREATE TABLE app_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 username VARCHAR(255) NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 display_name VARCHAR(255) NOT NULL,
 role VARCHAR(255) NOT NULL,
 npp_id BIGINT,
 region VARCHAR(255),
 enabled BOOLEAN NOT NULL DEFAULT 0,
 token_version BIGINT,
 failed_attempts INT NOT NULL DEFAULT 0,
 locked_until TIMESTAMP(3),
 otp_required BOOLEAN NOT NULL DEFAULT 0,
 UNIQUE (username),
 INDEX ix_app_user_scope (npp_id,created_at)
);
CREATE TABLE refresh_token (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 user_id BIGINT NOT NULL,
 token_hash VARCHAR(255) NOT NULL,
 expires_at TIMESTAMP(3) NOT NULL,
 revoked_at TIMESTAMP(3),
 UNIQUE (token_hash)
);
CREATE TABLE otp_challenge (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 user_id BIGINT NOT NULL,
 code_hash VARCHAR(255) NOT NULL,
 expires_at TIMESTAMP(3) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 consumed BOOLEAN NOT NULL DEFAULT 0
);
CREATE TABLE idempotency_record (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,idem_key VARCHAR(100) NOT NULL,request_hash VARCHAR(64) NOT NULL,response_json TEXT NOT NULL,created_at TIMESTAMP(3) NOT NULL,UNIQUE(user_id,idem_key));
CREATE TABLE audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,entity_type VARCHAR(100) NOT NULL,entity_id BIGINT,action VARCHAR(100) NOT NULL,payload TEXT NOT NULL,request_id VARCHAR(100),created_at TIMESTAMP(3) NOT NULL);
CREATE TABLE role (code VARCHAR(50) PRIMARY KEY,name VARCHAR(150) NOT NULL);
CREATE TABLE permission (code VARCHAR(80) PRIMARY KEY);
CREATE TABLE role_permission (role_code VARCHAR(50),permission_code VARCHAR(80),PRIMARY KEY(role_code,permission_code));
CREATE TABLE user_role_scope (user_id BIGINT NOT NULL,role_code VARCHAR(50) NOT NULL,scope_type VARCHAR(30) NOT NULL,scope_id VARCHAR(150) NOT NULL,PRIMARY KEY(user_id,role_code,scope_type,scope_id));
CREATE TABLE system_param (param_key VARCHAR(100) PRIMARY KEY,param_value VARCHAR(2000) NOT NULL);


-- Source: V2__catalog.sql
CREATE TABLE organization (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 code VARCHAR(255) NOT NULL,
 name VARCHAR(255) NOT NULL,
 type VARCHAR(255) NOT NULL,
 parent_id BIGINT,
 region VARCHAR(255) NOT NULL,
 UNIQUE (code)
);
CREATE TABLE sku (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 code VARCHAR(255) NOT NULL,
 name VARCHAR(255) NOT NULL,
 category VARCHAR(255) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT 0,
 min_order DECIMAL(18,3) NOT NULL,
 base_unit VARCHAR(255) NOT NULL,
 UNIQUE (code)
);
CREATE TABLE sku_uom (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 sku_id BIGINT NOT NULL,
 code VARCHAR(255) NOT NULL,
 factor_to_base DECIMAL(18,3) NOT NULL,
 multiple DECIMAL(18,3) NOT NULL,
 UNIQUE (sku_id,code)
);
CREATE TABLE store (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 route_id BIGINT,
 name VARCHAR(255) NOT NULL,
 owner VARCHAR(255),
 phone VARCHAR(255) NOT NULL,
 address VARCHAR(255) NOT NULL,
 latitude DOUBLE PRECISION,
 longitude DOUBLE PRECISION,
 group_name VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL,
 UNIQUE (npp_id,phone),
 INDEX ix_store_scope (npp_id,created_at)
);
CREATE TABLE warehouse (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 org_id BIGINT NOT NULL,
 code VARCHAR(255) NOT NULL,
 name VARCHAR(255) NOT NULL,
 type VARCHAR(255) NOT NULL,
 UNIQUE (code)
);


-- Source: V3__policy.sql
CREATE TABLE price_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT,
 sku_id BIGINT NOT NULL,
 uom VARCHAR(255) NOT NULL,
 price DECIMAL(18,2) NOT NULL,
 valid_from DATE NOT NULL,
 valid_to DATE NOT NULL,
 INDEX ix_price_item_scope (npp_id,created_at)
);
CREATE TABLE promotion (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(255) NOT NULL,
 npp_id BIGINT,
 sku_id BIGINT,
 min_qty DECIMAL(18,3) NOT NULL,
 min_amount DECIMAL(18,2) NOT NULL,
 percent_off DECIMAL(18,2) NOT NULL,
 priority INT NOT NULL DEFAULT 0,
 exclusion_group VARCHAR(255),
 budget DECIMAL(18,2) NOT NULL,
 used_budget DECIMAL(18,2) NOT NULL,
 status VARCHAR(255) NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 INDEX ix_promotion_scope (npp_id,created_at)
);


-- Source: V4__sellin_credit.sql
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


-- Source: V5__inventory_delivery.sql
CREATE TABLE stock_balance (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 warehouse_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 lot_no VARCHAR(255) NOT NULL,
 expiry_date DATE,
 qty_on_hand DECIMAL(18,3) NOT NULL,
 qty_reserved DECIMAL(18,3) NOT NULL,
 UNIQUE (warehouse_id,sku_id,lot_no),
 CHECK(qty_on_hand>=0),
 CHECK(qty_reserved>=0),
 CHECK(qty_reserved<=qty_on_hand)
);
CREATE TABLE stock_movement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 warehouse_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 lot_no VARCHAR(255) NOT NULL,
 movement_type VARCHAR(255) NOT NULL,
 qty DECIMAL(18,3) NOT NULL,
 reference_type VARCHAR(255) NOT NULL,
 reference_id BIGINT NOT NULL
);
CREATE TABLE stock_allocation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 document_type VARCHAR(255) NOT NULL,
 order_id BIGINT NOT NULL,
 line_id BIGINT NOT NULL,
 lot_id BIGINT NOT NULL,
 qty DECIMAL(18,3) NOT NULL,
 shipped BOOLEAN NOT NULL DEFAULT 0,
 UNIQUE (document_type,line_id,lot_id)
);
CREATE TABLE delivery_trip (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 driver_id BIGINT NOT NULL,
 delivery_date DATE NOT NULL,
 status VARCHAR(255) NOT NULL,
 INDEX ix_delivery_trip_scope (npp_id,created_at)
);
CREATE TABLE return_note (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 reason TEXT NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 status VARCHAR(255) NOT NULL,
 INDEX ix_return_note_scope (npp_id,created_at)
);


-- Source: V6__closing.sql
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


-- Source: V7__demand_ticket.sql
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


-- Source: V8__crm_route.sql
CREATE TABLE `lead` (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL,
 phone VARCHAR(255) NOT NULL,
 address VARCHAR(255) NOT NULL,
 latitude DOUBLE PRECISION,
 longitude DOUBLE PRECISION,
 status VARCHAR(255) NOT NULL,
 store_id BIGINT,
 reason TEXT,
 INDEX ix_lead_scope (npp_id,created_at)
);
CREATE TABLE interaction (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 kind VARCHAR(255) NOT NULL,
 note TEXT NOT NULL,
 occurred_at TIMESTAMP(3) NOT NULL,
 INDEX ix_interaction_scope (npp_id,created_at)
);
CREATE TABLE appointment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL,
 due_at TIMESTAMP(3) NOT NULL,
 remind_at TIMESTAMP(3) NOT NULL,
 status VARCHAR(255) NOT NULL,
 reminded BOOLEAN NOT NULL DEFAULT 0,
 INDEX ix_appointment_scope (npp_id,created_at)
);
CREATE TABLE campaign (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL,
 group_name VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL,
 message TEXT NOT NULL,
 INDEX ix_campaign_scope (npp_id,created_at)
);
CREATE TABLE route (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL,
 INDEX ix_route_scope (npp_id,created_at)
);
CREATE TABLE route_stop (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 route_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 sequence_no INT NOT NULL DEFAULT 0,
 weekday INT NOT NULL DEFAULT 0,
 UNIQUE (route_id,store_id)
);
CREATE TABLE visit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 business_day DATE NOT NULL,
 status VARCHAR(255) NOT NULL,
 checked_in_at TIMESTAMP(3),
 checked_out_at TIMESTAMP(3),
 latitude DOUBLE PRECISION,
 longitude DOUBLE PRECISION,
 distance_metres DOUBLE PRECISION,
 outside_radius BOOLEAN NOT NULL DEFAULT 0,
 reason TEXT,
 UNIQUE (store_id,rep_id,business_day),
 INDEX ix_visit_scope (npp_id,created_at)
);
CREATE TABLE handover (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 from_rep_id BIGINT NOT NULL,
 to_rep_id BIGINT NOT NULL,
 reason TEXT NOT NULL,
 INDEX ix_handover_scope (npp_id,created_at)
);


-- Source: V9__salesorder.sql
CREATE TABLE sales_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 code VARCHAR(255) NOT NULL,
 client_id VARCHAR(255) NOT NULL,
 idempotency_key VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 subtotal DECIMAL(18,2) NOT NULL,
 discount DECIMAL(18,2) NOT NULL,
 total DECIMAL(18,2) NOT NULL,
 client_created_at TIMESTAMP(3) NOT NULL,
 delivered_at TIMESTAMP(3),
 UNIQUE (code),
 UNIQUE (rep_id,idempotency_key),
 UNIQUE (rep_id,client_id),
 INDEX ix_sales_order_scope (npp_id,created_at)
);
CREATE TABLE sales_order_line (
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
 price DECIMAL(18,2) NOT NULL,
 discount DECIMAL(18,2) NOT NULL,
 base_factor DECIMAL(18,3) NOT NULL,
 UNIQUE (order_id,sku_id,uom)
);
CREATE TABLE daily_report (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 business_day DATE NOT NULL,
 visits INT NOT NULL DEFAULT 0,
 orders INT NOT NULL DEFAULT 0,
 amount DECIMAL(18,2) NOT NULL,
 note TEXT,
 status VARCHAR(255) NOT NULL,
 UNIQUE (rep_id,business_day),
 INDEX ix_daily_report_scope (npp_id,created_at)
);


-- Source: V10__display_notify.sql
CREATE TABLE display_criteria (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(255) NOT NULL,
 rules_json TEXT NOT NULL,
 status VARCHAR(255) NOT NULL
);
CREATE TABLE display_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 criteria_id BIGINT NOT NULL,
 attachment_id BIGINT NOT NULL,
 status VARCHAR(255) NOT NULL,
 ai_score DOUBLE PRECISION,
 confidence DOUBLE PRECISION,
 result_json TEXT,
 final_score DOUBLE PRECISION,
 reviewed_by BIGINT,
 provider VARCHAR(255) NOT NULL,
 INDEX ix_display_audit_scope (npp_id,created_at)
);
CREATE TABLE notification (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 user_id BIGINT NOT NULL,
 npp_id BIGINT,
 type VARCHAR(255) NOT NULL,
 payload TEXT NOT NULL,
 event_key VARCHAR(255) NOT NULL,
 read_at TIMESTAMP(3),
 UNIQUE (user_id,event_key),
 INDEX ix_notification_scope (npp_id,created_at)
);
CREATE TABLE task (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 store_id BIGINT,
 assignee_id BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL,
 due_at TIMESTAMP(3) NOT NULL,
 status VARCHAR(255) NOT NULL,
 reference_type VARCHAR(255) NOT NULL,
 reference_id BIGINT NOT NULL,
 INDEX ix_task_scope (npp_id,created_at)
);
CREATE TABLE workflow_definition (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 entity_type VARCHAR(255) NOT NULL,
 step_no INT NOT NULL DEFAULT 0,
 approver_role VARCHAR(255) NOT NULL,
 min_amount DECIMAL(18,2) NOT NULL,
 sla_hours INT NOT NULL DEFAULT 0,
 UNIQUE (entity_type,step_no)
);
CREATE TABLE attachment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 owner_id BIGINT NOT NULL,
 object_key VARCHAR(255) NOT NULL,
 filename VARCHAR(255) NOT NULL,
 content_type VARCHAR(255) NOT NULL,
 size_bytes BIGINT NOT NULL,
 status VARCHAR(255) NOT NULL,
 UNIQUE (object_key),
 INDEX ix_attachment_scope (npp_id,created_at)
);
CREATE TABLE domain_event_outbox (id BIGINT AUTO_INCREMENT PRIMARY KEY,event_id VARCHAR(36) NOT NULL UNIQUE,event_type VARCHAR(100) NOT NULL,aggregate_id BIGINT NOT NULL,npp_id BIGINT,user_id BIGINT,payload TEXT NOT NULL,status VARCHAR(20) NOT NULL,attempts INT NOT NULL DEFAULT 0,available_at TIMESTAMP(3) NOT NULL,created_at TIMESTAMP(3) NOT NULL,last_error VARCHAR(1000));
CREATE TABLE processed_event (consumer VARCHAR(100) NOT NULL,event_id VARCHAR(36) NOT NULL,processed_at TIMESTAMP(3) NOT NULL,PRIMARY KEY(consumer,event_id));
CREATE TABLE shedlock (name VARCHAR(64) PRIMARY KEY,lock_until TIMESTAMP(3) NOT NULL,locked_at TIMESTAMP(3) NOT NULL,locked_by VARCHAR(255) NOT NULL);
CREATE TABLE job_run_log (id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100) NOT NULL,started_at TIMESTAMP(3) NOT NULL,finished_at TIMESTAMP(3),record_count INT NOT NULL DEFAULT 0,error_message VARCHAR(1000));


-- Source: V11__constraints.sql

ALTER TABLE app_user ADD CONSTRAINT fk_user_npp FOREIGN KEY(npp_id) REFERENCES organization(id);
ALTER TABLE store ADD CONSTRAINT fk_store_npp FOREIGN KEY(npp_id) REFERENCES organization(id);
ALTER TABLE store ADD CONSTRAINT fk_store_rep FOREIGN KEY(rep_id) REFERENCES app_user(id);
ALTER TABLE warehouse ADD CONSTRAINT fk_warehouse_org FOREIGN KEY(org_id) REFERENCES organization(id);
ALTER TABLE sku_uom ADD CONSTRAINT fk_uom_sku FOREIGN KEY(sku_id) REFERENCES sku(id);
ALTER TABLE price_item ADD CONSTRAINT fk_price_sku FOREIGN KEY(sku_id) REFERENCES sku(id);
ALTER TABLE sell_in_order ADD CONSTRAINT fk_sellin_npp FOREIGN KEY(npp_id) REFERENCES organization(id);
ALTER TABLE sell_in_order_line ADD CONSTRAINT fk_sellin_line_order FOREIGN KEY(order_id) REFERENCES sell_in_order(id);
ALTER TABLE sell_in_order_line ADD CONSTRAINT fk_sellin_line_sku FOREIGN KEY(sku_id) REFERENCES sku(id);
ALTER TABLE sales_order ADD CONSTRAINT fk_sales_store FOREIGN KEY(store_id) REFERENCES store(id);
ALTER TABLE sales_order ADD CONSTRAINT fk_sales_rep FOREIGN KEY(rep_id) REFERENCES app_user(id);
ALTER TABLE sales_order_line ADD CONSTRAINT fk_sales_line_order FOREIGN KEY(order_id) REFERENCES sales_order(id);
ALTER TABLE stock_balance ADD CONSTRAINT fk_stock_warehouse FOREIGN KEY(warehouse_id) REFERENCES warehouse(id);
ALTER TABLE stock_balance ADD CONSTRAINT fk_stock_sku FOREIGN KEY(sku_id) REFERENCES sku(id);
ALTER TABLE period_snapshot ADD CONSTRAINT fk_snapshot_period FOREIGN KEY(period_id) REFERENCES report_period(id);
ALTER TABLE target ADD CONSTRAINT fk_target_period FOREIGN KEY(period_id) REFERENCES report_period(id);
ALTER TABLE trade_request ADD CONSTRAINT fk_trade_budget FOREIGN KEY(budget_id) REFERENCES budget_allocation(id);
ALTER TABLE appointment ADD CONSTRAINT fk_appointment_store FOREIGN KEY(store_id) REFERENCES store(id);
ALTER TABLE visit ADD CONSTRAINT fk_visit_store FOREIGN KEY(store_id) REFERENCES store(id);
ALTER TABLE refresh_token ADD CONSTRAINT fk_refresh_user FOREIGN KEY(user_id) REFERENCES app_user(id);


-- Source: V12__applied_benefits.sql
CREATE TABLE applied_benefit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 document_type VARCHAR(30) NOT NULL,
 document_id BIGINT NOT NULL,
 promotion_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 released BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(document_type,document_id,promotion_id),
 CONSTRAINT fk_applied_promotion FOREIGN KEY(promotion_id) REFERENCES promotion(id)
);


-- Source: V13__approval_workflows.sql
CREATE TABLE approval_request (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 entity_type VARCHAR(50) NOT NULL,
 entity_id BIGINT NOT NULL,
 npp_id BIGINT,
 step_no INT NOT NULL,
 approver_role VARCHAR(60) NOT NULL,
 status VARCHAR(30) NOT NULL,
 approved_by BIGINT,
 approved_at TIMESTAMP(3),
 reason TEXT,
 UNIQUE(entity_type,entity_id,step_no)
);
INSERT INTO workflow_definition(created_at,updated_at,version,entity_type,step_no,approver_role,min_amount,sla_hours) VALUES
 (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'TRADE_REQUEST',1,'TRADE_MARKETING',0,24),
 (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'TRADE_REQUEST',2,'REGION_MANAGER',50000000,24),
 (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'TRADE_REQUEST',3,'NSX_DIRECTOR',200000000,24),
 (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'ADJUSTMENT',1,'ACCOUNTANT_NSX',0,24),
 (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'ADJUSTMENT',2,'NSX_DIRECTOR',100000000,24),
 (CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'POLICY',1,'TRADE_MARKETING',0,24);


-- Source: V14__return_lines.sql
CREATE TABLE return_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 return_id BIGINT NOT NULL,order_line_id BIGINT NOT NULL,sku_id BIGINT NOT NULL,qty DECIMAL(18,3) NOT NULL,base_factor DECIMAL(18,3) NOT NULL,amount DECIMAL(18,2) NOT NULL,
 UNIQUE(return_id,order_line_id),FOREIGN KEY(return_id) REFERENCES return_note(id),FOREIGN KEY(order_line_id) REFERENCES sell_in_order_line(id)
);
ALTER TABLE sell_in_order ADD received_at TIMESTAMP(3);


-- Source: V15__stocktakes.sql
CREATE TABLE stocktake (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 warehouse_id BIGINT NOT NULL,created_by BIGINT NOT NULL,status VARCHAR(30) NOT NULL,reason TEXT NOT NULL,
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
);
CREATE TABLE stocktake_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 stocktake_id BIGINT NOT NULL,lot_id BIGINT NOT NULL,baseline_version BIGINT NOT NULL,system_qty DECIMAL(18,3) NOT NULL,counted_qty DECIMAL(18,3) NOT NULL,
 UNIQUE(stocktake_id,lot_id),FOREIGN KEY(stocktake_id) REFERENCES stocktake(id),FOREIGN KEY(lot_id) REFERENCES stock_balance(id)
);


-- Source: V16__operational_calendar.sql
CREATE TABLE business_holiday (holiday_date DATE PRIMARY KEY,description VARCHAR(255) NOT NULL);
ALTER TABLE sell_in_order ADD receipt_reminded_at TIMESTAMP(3);


-- Source: V17__password_recovery.sql
CREATE TABLE password_reset_token (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 user_id BIGINT NOT NULL,token_hash VARCHAR(64) NOT NULL UNIQUE,expires_at TIMESTAMP(3) NOT NULL,consumed BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(user_id) REFERENCES app_user(id),INDEX ix_reset_user_time(user_id,created_at)
);


-- Source: V18__report_calendar.sql
CREATE TABLE report_calendar (id BIGINT PRIMARY KEY);
INSERT INTO report_calendar(id) VALUES (1);
