ALTER TABLE role ADD COLUMN base_role VARCHAR(50);
ALTER TABLE role ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE role ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_token ADD COLUMN workspace_key VARCHAR(1200);
ALTER TABLE audit_log ADD COLUMN client_ip VARCHAR(100);
ALTER TABLE audit_log ADD COLUMN before_json TEXT;
ALTER TABLE audit_log ADD COLUMN after_json TEXT;
CREATE TABLE catalog_membership (
 reference_id BIGINT NOT NULL, member_type VARCHAR(20) NOT NULL, member_id BIGINT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL, PRIMARY KEY(reference_id,member_type,member_id),
 FOREIGN KEY(reference_id) REFERENCES catalog_reference(id)
);
