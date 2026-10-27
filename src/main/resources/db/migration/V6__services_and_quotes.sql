-- Consulting services are sold like products but have no stock: always available, nothing reserved
alter table product add column kind varchar(20) not null default 'PRODUCT';
alter table stock add column tracked boolean not null default true;

create index idx_product_kind on product (kind);

-- Requests for a custom consulting quote, sent from the public site
create table quote_request (
    id         uuid primary key,
    full_name  varchar(200)  not null,
    email      varchar(254)  not null,
    company    varchar(200),
    phone      varchar(30),
    -- service the request is about, when sent from its page
    topic      varchar(200),
    message    varchar(5000) not null,
    status     varchar(20)   not null,
    created_at timestamptz   not null,
    updated_at timestamptz   not null,
    version    bigint        not null
);

create index idx_quote_request_status on quote_request (status, created_at desc);
