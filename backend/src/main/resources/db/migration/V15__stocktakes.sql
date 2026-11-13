CREATE TABLE stocktake (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 warehouse_id BIGINT NOT NULL,created_by BIGINT NOT NULL,status VARCHAR(30) NOT NULL,reason TEXT NOT NULL,
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
);
CREATE TABLE stocktake_line (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 stocktake_id BIGINT NOT NULL,lot_id BIGINT NOT NULL,baseline_version BIGINT NOT NULL,system_qty DECIMAL(18,3) NOT NULL,counted_qty DECIMAL(18,3) NOT NULL,
 UNIQUE(stocktake_id,lot_id),FOREIGN KEY(stocktake_id) REFERENCES stocktake(id),FOREIGN KEY(lot_id) REFERENCES stock_balance(id)
);
