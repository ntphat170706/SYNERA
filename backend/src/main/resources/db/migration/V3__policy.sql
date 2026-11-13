CREATE TABLE price_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT,
 sku_id BIGINT NOT NULL,
 uom VARCHAR(255) NOT NULL,
 price DECIMAL(18,2) NOT NULL,
 valid_from DATE NOT NULL,
 valid_to DATE NOT NULL,
 INDEX ix_price_item_scope (npp_id,created_at)
);
CREATE TABLE promotion (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(255) NOT NULL,
 npp_id BIGINT,
 sku_id BIGINT,
 min_qty DECIMAL(18,3) NOT NULL,
 min_amount DECIMAL(18,2) NOT NULL,
 percent_off DECIMAL(18,2) NOT NULL,
 priority INT NOT NULL DEFAULT 0,
 exclusion_group VARCHAR(255),
 budget DECIMAL(18,2) NOT NULL,
 used_budget DECIMAL(18,2) NOT NULL,
 status VARCHAR(255) NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 INDEX ix_promotion_scope (npp_id,created_at)
);
