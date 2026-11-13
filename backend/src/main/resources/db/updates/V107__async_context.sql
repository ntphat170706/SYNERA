ALTER TABLE integration_callback ADD COLUMN response_json TEXT;
CREATE TABLE integration_callback_guard (id BIGINT PRIMARY KEY);
INSERT INTO integration_callback_guard(id) VALUES(1);
ALTER TABLE report_export ADD COLUMN workspace_key VARCHAR(500);
ALTER TABLE report_schedule ADD COLUMN workspace_key VARCHAR(500);
