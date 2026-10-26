-- Key sent by the client with each checkout attempt: replaying it returns the order already placed
alter table customer_order add column idempotency_key uuid;

create unique index uq_order_idempotency on customer_order (customer_id, idempotency_key);
