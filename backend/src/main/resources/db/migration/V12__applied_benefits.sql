CREATE TABLE applied_benefit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 created_at TIMESTAMP(3) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 document_type VARCHAR(30) NOT NULL,
 document_id BIGINT NOT NULL,
 promotion_id BIGINT NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 released BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(document_type,document_id,promotion_id),
 CONSTRAINT fk_applied_promotion FOREIGN KEY(promotion_id) REFERENCES promotion(id)
);
