ALTER TABLE report_export ADD COLUMN available_at TIMESTAMP(3);
ALTER TABLE report_export ADD COLUMN started_at TIMESTAMP(3);
ALTER TABLE report_export ADD COLUMN email VARCHAR(255);
ALTER TABLE report_schedule ADD COLUMN last_error VARCHAR(1000);
