ALTER TABLE ticket ADD COLUMN assigned_to BIGINT;
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_assigned FOREIGN KEY(assigned_to) REFERENCES app_user(id);
CREATE INDEX ix_ticket_assignment ON ticket(assigned_to,status,due_at);
