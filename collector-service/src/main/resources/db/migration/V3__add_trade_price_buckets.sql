create table collector.trade_dataset_search (
    run_id uuid not null references collector.trade_dataset_run (id),
    price_bucket varchar(32) not null,
    search_id varchar(128) not null,
    price_currency varchar(64) not null,
    minimum_price numeric(30, 10) not null,
    maximum_price numeric(30, 10),
    total_matches integer not null,
    selected_items integer not null,
    created_at timestamptz not null,
    primary key (run_id, price_bucket),
    constraint ck_trade_dataset_search_prices check (
        minimum_price > 0
        and (maximum_price is null or maximum_price > minimum_price)
    )
);

alter table collector.trade_listing_snapshot
    add column price_bucket varchar(32) not null default 'unbucketed';

alter table collector.trade_listing_snapshot
    alter column price_bucket drop default;

create index idx_trade_listing_snapshot_bucket
    on collector.trade_listing_snapshot (run_id, price_bucket, sample_ordinal);
