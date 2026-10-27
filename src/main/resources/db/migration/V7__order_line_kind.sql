-- What each order line sold: shipped products and consulting services follow different fulfilment steps
alter table order_line add column kind varchar(20) not null default 'PRODUCT';

update order_line ol set kind = p.kind from product p where p.id = ol.product_id;
