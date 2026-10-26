create table payment (
    id                 uuid primary key,
    order_id           uuid           not null references customer_order (id),
    customer_id        varchar(64)    not null,
    provider           varchar(32)    not null,
    status             varchar(20)    not null,
    amount             numeric(19, 4) not null,
    currency           varchar(3)     not null,
    -- provider-side identifiers: PayPal order id, then capture id once captured
    provider_reference varchar(64),
    capture_id         varchar(64),
    approval_url       varchar(500),
    failure_reason     varchar(100),
    created_at         timestamptz    not null,
    updated_at         timestamptz    not null,
    -- last time the reconciliation job asked the provider about this payment
    checked_at         timestamptz,
    version            bigint         not null
);

-- At most one live payment per order: failed or cancelled attempts can be retried, a captured order cannot be paid twice
create unique index uq_payment_live_order on payment (order_id) where status not in ('FAILED', 'CANCELLED');
create unique index uq_payment_provider_reference on payment (provider, provider_reference);
create index idx_payment_capture on payment (provider, capture_id);
create index idx_payment_status_updated on payment (status, updated_at);

create table payment_refund (
    payment_id         uuid           not null references payment (id),
    provider_refund_id varchar(64)    not null,
    amount             numeric(19, 4) not null,
    currency           varchar(3)     not null,
    created_at         timestamptz    not null,
    primary key (payment_id, provider_refund_id)
);

-- Audit log of every call made to a provider, with the idempotency key it carried
create table payment_attempt (
    id              uuid primary key,
    payment_id      uuid         not null references payment (id),
    operation       varchar(20)  not null,
    -- null for read-only calls, which are naturally idempotent
    idempotency_key varchar(100),
    outcome         varchar(20)  not null,
    detail          varchar(200),
    created_at      timestamptz  not null
);

create index idx_payment_attempt_payment on payment_attempt (payment_id, created_at);

-- Webhooks are delivered at least once: the event id deduplicates them
create table webhook_event (
    id          uuid primary key,
    provider    varchar(32)  not null,
    event_id    varchar(100) not null,
    event_type  varchar(100) not null,
    received_at timestamptz  not null,
    unique (provider, event_id)
);
