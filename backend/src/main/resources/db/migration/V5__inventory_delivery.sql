CREATE TABLE stock_balance (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 warehouse_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 lot_no VARCHAR(255) NOT NULL,
 expiry_date DATE,
 qty_on_hand DECIMAL(18,3) NOT NULL,
 qty_reserved DECIMAL(18,3) NOT NULL,
 UNIQUE (warehouse_id,sku_id,lot_no),
 CHECK(qty_on_hand>=0),
 CHECK(qty_reserved>=0),
 CHECK(qty_reserved<=qty_on_hand)
);
CREATE TABLE stock_movement (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 warehouse_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 lot_no VARCHAR(255) NOT NULL,
 movement_type VARCHAR(255) NOT NULL,
 qty DECIMAL(18,3) NOT NULL,
 reference_type VARCHAR(255) NOT NULL,
 reference_id BIGINT NOT NULL
);
CREATE TABLE stock_allocation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 document_type VARCHAR(255) NOT NULL,
 order_id BIGINT NOT NULL,
 line_id BIGINT NOT NULL,
 lot_id BIGINT NOT NULL,
 qty DECIMAL(18,3) NOT NULL,
 shipped BOOLEAN NOT NULL DEFAULT 0,
 UNIQUE (document_type,line_id,lot_id)
);
CREATE TABLE delivery_trip (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 driver_id BIGINT NOT NULL,
 delivery_date DATE NOT NULL,
 status VARCHAR(255) NOT NULL,
 INDEX ix_delivery_trip_scope (npp_id,created_at)
);
CREATE TABLE return_note (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 npp_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 reason TEXT NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 status VARCHAR(255) NOT NULL,
 INDEX ix_return_note_scope (npp_id,created_at)
);
