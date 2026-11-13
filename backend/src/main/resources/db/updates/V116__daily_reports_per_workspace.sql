CREATE TABLE daily_report_scoped (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at TIMESTAMP(3) NOT NULL,updated_at TIMESTAMP(3) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,npp_id BIGINT NOT NULL,rep_id BIGINT NOT NULL,business_day DATE NOT NULL,
 visits INT NOT NULL DEFAULT 0,orders INT NOT NULL DEFAULT 0,amount DECIMAL(18,2) NOT NULL,
 note TEXT,status VARCHAR(255) NOT NULL,UNIQUE(rep_id,npp_id,business_day),INDEX ix_scoped_daily_npp(npp_id,business_day)
);
INSERT INTO daily_report_scoped(id,created_at,updated_at,version,npp_id,rep_id,business_day,visits,orders,amount,note,status)
SELECT id,created_at,updated_at,version,npp_id,rep_id,business_day,visits,orders,amount,note,status FROM daily_report;
