create table customer_order (
    id             uuid primary key,
    reference      varchar(20)    not null unique,
    customer_id    varchar(64)    not null,
    status         varchar(20)    not null,
    total_amount   numeric(19, 4) not null,
    total_currency varchar(3)     not null,
    -- shipping address snapshot, independent of later address changes
    full_name      varchar(200)   not null,
    line1          varchar(200)   not null,
    line2          varchar(200),
    postal_code    varchar(20)    not null,
    city           varchar(100)   not null,
    country        varchar(2)     not null,
    phone          varchar(30),
    created_at     timestamptz    not null,
    updated_at     timestamptz    not null,
    -- unpaid orders past this instant are cancelled and their stock released
    expires_at     timestamptz,
    version        bigint         not null
);

create index idx_order_customer on customer_order (customer_id, created_at desc);
create index idx_order_status_expiry on customer_order (status, expires_at);

create table order_line (
    id                  uuid primary key,
    order_id            uuid           not null references customer_order (id),
    product_id          uuid           not null,
    sku                 varchar(64)    not null,
    name                varchar(200)   not null,
    unit_price_amount   numeric(19, 4) not null,
    unit_price_currency varchar(3)     not null,
    quantity            integer        not null check (quantity > 0)
);

create index idx_order_line_order on order_line (order_id);
