CREATE TABLE lead (
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
