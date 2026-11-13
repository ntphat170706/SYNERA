ALTER TABLE promotion ADD COLUMN required_skus_json TEXT;
ALTER TABLE promotion ADD COLUMN max_uses BIGINT;
ALTER TABLE promotion ADD COLUMN used_uses BIGINT NOT NULL DEFAULT 0;
