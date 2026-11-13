CREATE TABLE sales_replacement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 order_id BIGINT NOT NULL,
 npp_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 rep_id BIGINT NOT NULL,
 warehouse_id BIGINT NOT NULL,
 created_by BIGINT NOT NULL,
 approved_by BIGINT,
 status VARCHAR(30) NOT NULL,
 due_at TIMESTAMP NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 receipt_note VARCHAR(2000),
 received_at TIMESTAMP,
 notified_at TIMESTAMP,
 notified_by BIGINT,
 customer_feedback VARCHAR(2000),
 CONSTRAINT fk_replacement_order FOREIGN KEY (order_id) REFERENCES sales_order(id)
);
CREATE INDEX ix_replacement_scope ON sales_replacement(npp_id,rep_id,order_id);
CREATE INDEX ix_replacement_order ON sales_replacement(order_id,id);
CREATE TABLE sales_replacement_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 replacement_id BIGINT NOT NULL,
 order_line_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 uom VARCHAR(50) NOT NULL,
 base_factor DECIMAL(18,3) NOT NULL,
 qty DECIMAL(18,3) NOT NULL,
 qty_received DECIMAL(18,3) NOT NULL DEFAULT 0,
 CONSTRAINT fk_replacement_line_note FOREIGN KEY(replacement_id) REFERENCES sales_replacement(id),
 CONSTRAINT fk_replacement_line_original FOREIGN KEY(order_line_id) REFERENCES sales_order_line(id),
 CONSTRAINT uq_replacement_line UNIQUE(replacement_id,order_line_id)
);
CREATE INDEX ix_replacement_line_original ON sales_replacement_line(order_line_id,replacement_id);
