ALTER TABLE price_item ADD COLUMN npp_group_id BIGINT;
ALTER TABLE price_item ADD COLUMN store_group_id BIGINT;
ALTER TABLE promotion ADD COLUMN npp_group_id BIGINT;
ALTER TABLE promotion ADD COLUMN store_group_id BIGINT;
INSERT INTO system_param(param_key,param_value) VALUES('price-cache-version','1');
