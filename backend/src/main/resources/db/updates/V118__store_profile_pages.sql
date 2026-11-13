CREATE INDEX ix_sales_store_page ON sales_order(store_id,rep_id,id);
CREATE INDEX ix_interaction_store_page ON interaction(store_id,id);
CREATE INDEX ix_appointment_store_page ON appointment(store_id,rep_id,id);
CREATE INDEX ix_ticket_store_page ON ticket(store_id,id);
CREATE INDEX ix_display_store_page ON display_audit(store_id,id);
