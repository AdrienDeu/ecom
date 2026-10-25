-- customer_id is the Cognito "sub" claim: customer identities live in Cognito
create table address (
    id          uuid primary key,
    customer_id varchar(64)  not null,
    full_name   varchar(200) not null,
    line1       varchar(200) not null,
    line2       varchar(200),
    postal_code varchar(20)  not null,
    city        varchar(100) not null,
    country     varchar(2)   not null,
    phone       varchar(30),
    created_at  timestamptz  not null
);

create index idx_address_customer on address (customer_id);
