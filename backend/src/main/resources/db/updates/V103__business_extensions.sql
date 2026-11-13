CREATE TABLE catalog_reference (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(40) NOT NULL, code VARCHAR(80) NOT NULL,
 name VARCHAR(255) NOT NULL, parent_id BIGINT, npp_id BIGINT, active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE(kind,code), FOREIGN KEY(parent_id) REFERENCES catalog_reference(id), FOREIGN KEY(npp_id) REFERENCES organization(id)
);
CREATE TABLE quotation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, npp_id BIGINT NOT NULL, store_id BIGINT NOT NULL, rep_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL, valid_until DATE NOT NULL, total DECIMAL(18,2) NOT NULL,
 request_json TEXT NOT NULL, quote_json TEXT NOT NULL, reason TEXT, sales_order_id BIGINT,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(store_id) REFERENCES store(id), FOREIGN KEY(rep_id) REFERENCES app_user(id), INDEX ix_quote_scope(npp_id,status,created_at)
);
CREATE TABLE store_stock_survey (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, npp_id BIGINT NOT NULL, store_id BIGINT NOT NULL, rep_id BIGINT NOT NULL,
 survey_date DATE NOT NULL, lines_json TEXT NOT NULL, note TEXT,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(store_id) REFERENCES store(id), INDEX ix_survey_scope(npp_id,survey_date)
);
CREATE TABLE campaign_recipient (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, campaign_id BIGINT NOT NULL, npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL, rep_id BIGINT NOT NULL, task_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE(campaign_id,store_id), FOREIGN KEY(campaign_id) REFERENCES campaign(id), FOREIGN KEY(task_id) REFERENCES task(id)
);
CREATE TABLE stock_transfer (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, source_warehouse_id BIGINT NOT NULL, destination_warehouse_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL, qty_base DECIMAL(18,3) NOT NULL, status VARCHAR(30) NOT NULL, reason TEXT NOT NULL,
 created_by BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(source_warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(destination_warehouse_id) REFERENCES warehouse(id),
 CHECK(qty_base>0), CHECK(source_warehouse_id<>destination_warehouse_id)
);
CREATE TABLE delivery_stop (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, trip_id BIGINT NOT NULL, channel VARCHAR(20) NOT NULL,
 order_id BIGINT NOT NULL, sequence_no INT NOT NULL, status VARCHAR(30) NOT NULL,
 proof_attachment_id BIGINT, note TEXT, delivered_at TIMESTAMP(3),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL,
 UNIQUE(channel,order_id), FOREIGN KEY(trip_id) REFERENCES delivery_trip(id)
);
CREATE TABLE policy_document (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, npp_id BIGINT, title VARCHAR(255) NOT NULL,
 body TEXT NOT NULL, attachment_id BIGINT, status VARCHAR(30) NOT NULL, published_at TIMESTAMP(3),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL
);
CREATE TABLE policy_ack (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL, user_id BIGINT NOT NULL, acknowledged_at TIMESTAMP(3) NOT NULL,
 UNIQUE(document_id,user_id), FOREIGN KEY(document_id) REFERENCES policy_document(id)
);
CREATE TABLE ticket_comment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, ticket_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 body TEXT NOT NULL, attachment_id BIGINT, created_at TIMESTAMP(3) NOT NULL,
 FOREIGN KEY(ticket_id) REFERENCES ticket(id)
);
CREATE TABLE ticket_routing (
 category VARCHAR(50) PRIMARY KEY, department_role VARCHAR(60) NOT NULL, sla_hours INT NOT NULL,
 last_owner_id BIGINT, version BIGINT NOT NULL DEFAULT 0, CHECK(sla_hours>0)
);
INSERT INTO ticket_routing(category,department_role,sla_hours) VALUES
 ('DELIVERY_SHORT','CUSTOMER_SERVICE',24),('QUALITY','CUSTOMER_SERVICE',12),('CREDIT','ACCOUNTANT_NSX',24),
 ('POLICY','TRADE_MARKETING',48),('OTHER','CUSTOMER_SERVICE',48);
CREATE TABLE trade_settlement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT NOT NULL UNIQUE, npp_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL, reference_no VARCHAR(100) NOT NULL, attachment_id BIGINT, approved_by BIGINT NOT NULL,
 reason TEXT NOT NULL, created_at TIMESTAMP(3) NOT NULL, FOREIGN KEY(request_id) REFERENCES trade_request(id), CHECK(amount>=0)
);
CREATE TABLE reward_payout (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, target_id BIGINT NOT NULL UNIQUE, npp_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL, reference_no VARCHAR(100) NOT NULL, approved_by BIGINT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL, FOREIGN KEY(target_id) REFERENCES target(id), CHECK(amount>0)
);
CREATE TABLE operational_alert (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, alert_key VARCHAR(150) NOT NULL UNIQUE, category VARCHAR(50) NOT NULL,
 npp_id BIGINT, warehouse_id BIGINT, sku_id BIGINT, store_id BIGINT, detail TEXT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'OPEN', detected_at TIMESTAMP(3) NOT NULL, resolved_at TIMESTAMP(3)
);
CREATE TABLE report_export (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, npp_id BIGINT, report_type VARCHAR(40) NOT NULL,
 format VARCHAR(10) NOT NULL, status VARCHAR(30) NOT NULL, filters_json TEXT NOT NULL,
 object_key VARCHAR(255), error_message VARCHAR(1000), attempts INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(3) NOT NULL, finished_at TIMESTAMP(3), expires_at TIMESTAMP(3), INDEX ix_export_owner(user_id,created_at)
);
CREATE TABLE report_schedule (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, npp_id BIGINT, email VARCHAR(255) NOT NULL,
 report_type VARCHAR(40) NOT NULL, cadence VARCHAR(20) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 next_run TIMESTAMP(3) NOT NULL, last_run TIMESTAMP(3), created_at TIMESTAMP(3) NOT NULL
);
CREATE TABLE external_delivery (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, channel VARCHAR(20) NOT NULL, destination VARCHAR(255) NOT NULL,
 subject VARCHAR(255) NOT NULL, body TEXT NOT NULL, status VARCHAR(30) NOT NULL, dedup_key VARCHAR(150) NOT NULL UNIQUE,
 attempts INT NOT NULL DEFAULT 0, available_at TIMESTAMP(3) NOT NULL, created_at TIMESTAMP(3) NOT NULL,
 last_error VARCHAR(1000), delivered_at TIMESTAMP(3)
);
CREATE TABLE integration_callback (
 provider VARCHAR(40) NOT NULL, event_id VARCHAR(100) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL, PRIMARY KEY(provider,event_id)
);
CREATE TABLE ai_job (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, audit_id BIGINT NOT NULL UNIQUE, status VARCHAR(30) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, available_at TIMESTAMP(3) NOT NULL, last_error VARCHAR(1000),
 created_at TIMESTAMP(3) NOT NULL, finished_at TIMESTAMP(3), FOREIGN KEY(audit_id) REFERENCES display_audit(id)
);
ALTER TABLE attachment ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE attachment ADD COLUMN scan_result VARCHAR(100);
ALTER TABLE display_audit ADD COLUMN model_version VARCHAR(100);
ALTER TABLE approval_request ADD COLUMN due_at TIMESTAMP(3);
ALTER TABLE approval_request ADD COLUMN escalated BOOLEAN NOT NULL DEFAULT FALSE;
