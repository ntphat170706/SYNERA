ALTER TABLE budget_allocation ADD COLUMN spent DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE target ADD COLUMN created_by BIGINT;
CREATE UNIQUE INDEX uq_target_period_npp ON target(npp_id,period_id);
