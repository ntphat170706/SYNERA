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
