create table category (
    id   uuid primary key,
    slug varchar(100) not null unique,
    name varchar(200) not null
);

create table product (
    id             uuid primary key,
    sku            varchar(64)    not null unique,
    slug           varchar(200)   not null unique,
    name           varchar(200)   not null,
    description    text,
    price_amount   numeric(19, 4) not null check (price_amount >= 0),
    price_currency varchar(3)     not null,
    image_url      varchar(1000),
    category_id    uuid references category (id),
    active         boolean        not null,
    created_at     timestamptz    not null,
    updated_at     timestamptz    not null,
    version        bigint         not null
);

create index idx_product_category on product (category_id);

-- on_hand: physical units; reserved: units held by unpaid orders
create table stock (
    product_id uuid primary key references product (id),
    on_hand    integer not null check (on_hand >= 0),
    reserved   integer not null check (reserved >= 0),
    version    bigint  not null,
    constraint stock_reserved_le_on_hand check (reserved <= on_hand)
);
