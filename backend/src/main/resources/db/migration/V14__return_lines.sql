CREATE TABLE return_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 return_id BIGINT NOT NULL,order_line_id BIGINT NOT NULL,sku_id BIGINT NOT NULL,qty DECIMAL(18,3) NOT NULL,base_factor DECIMAL(18,3) NOT NULL,amount DECIMAL(18,2) NOT NULL,
 UNIQUE(return_id,order_line_id),FOREIGN KEY(return_id) REFERENCES return_note(id),FOREIGN KEY(order_line_id) REFERENCES sell_in_order_line(id)
);
ALTER TABLE sell_in_order ADD received_at TIMESTAMP(3);
