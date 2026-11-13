ALTER TABLE credit_limit ADD COLUMN temporary_limit DECIMAL(18,2);
ALTER TABLE credit_limit ADD COLUMN temporary_until TIMESTAMP(3);
ALTER TABLE market_feedback ADD COLUMN created_by BIGINT;
ALTER TABLE display_criteria ADD COLUMN npp_id BIGINT;
