INSERT INTO workflow_definition(created_at,updated_at,version,entity_type,step_no,approver_role,min_amount,sla_hours) VALUES
(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'SELL_IN',1,'ORDER_APPROVE',0,24),
(CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'SELL_IN',2,'NSX_DIRECTOR',500000000,24);
