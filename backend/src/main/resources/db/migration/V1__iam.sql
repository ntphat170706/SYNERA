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
