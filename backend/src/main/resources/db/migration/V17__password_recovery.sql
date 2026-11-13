CREATE TABLE password_reset_token (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 user_id BIGINT NOT NULL,token_hash VARCHAR(64) NOT NULL UNIQUE,expires_at TIMESTAMP(3) NOT NULL,consumed BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(user_id) REFERENCES app_user(id),INDEX ix_reset_user_time(user_id,created_at)
);
