create table collector.trade_dataset_run (
    id uuid primary key,
    run_key varchar(128) not null unique,
    league varchar(128) not null,
    item_category varchar(64) not null,
    availability varchar(32) not null,
    requested_items integer not null,
    search_id varchar(128),
    total_matches integer,
    selected_items integer,
    stored_items integer not null default 0,
    status varchar(32) not null,
    error_message text,
    started_at timestamptz not null,
    finished_at timestamptz,
    constraint ck_trade_dataset_run_requested_items
        check (requested_items between 1 and 1000),
    constraint ck_trade_dataset_run_status
        check (status in ('RUNNING', 'COMPLETED', 'RATE_LIMITED', 'FAILED'))
);

create table collector.trade_listing_snapshot (
    id bigserial primary key,
    run_id uuid not null references collector.trade_dataset_run (id),
    sample_ordinal integer not null,
    search_result_rank integer not null,
    price_quantile double precision not null,
    search_id varchar(128) not null,
    listing_id varchar(128) not null,
    item_id varchar(128),
    league varchar(128) not null,
    item_name text,
    type_line text,
    base_type text,
    rarity varchar(32),
    item_level integer,
    identified boolean,
    corrupted boolean,
    fractured boolean,
    synthesised boolean,
    mirrored boolean,
    listing_method varchar(32),
    indexed_at timestamptz,
    price_type varchar(32),
    price_amount numeric(30, 10) not null,
    price_currency varchar(64) not null,
    raw_item_json jsonb not null,
    raw_listing_json jsonb not null,
    raw_result_json jsonb not null,
    collected_at timestamptz not null,
    constraint uq_trade_listing_snapshot_listing unique (listing_id),
    constraint uq_trade_listing_snapshot_run_ordinal unique (run_id, sample_ordinal),
    constraint ck_trade_listing_snapshot_price check (price_amount > 0),
    constraint ck_trade_listing_snapshot_quantile
        check (price_quantile >= 0 and price_quantile <= 1)
);

create index idx_trade_listing_snapshot_run
    on collector.trade_listing_snapshot (run_id, sample_ordinal);

create index idx_trade_listing_snapshot_price
    on collector.trade_listing_snapshot (league, price_currency, price_amount);

create index idx_trade_listing_snapshot_base_type
    on collector.trade_listing_snapshot (base_type);

create index idx_trade_listing_snapshot_item_id
    on collector.trade_listing_snapshot (item_id)
    where item_id is not null;
