create table collector.collector_cursor (
    stream_key varchar(64) primary key,
    next_change_id varchar(256) not null,
    updated_at timestamptz not null default now()
);

create table collector.market_stash (
    stash_id varchar(128) primary key,
    public boolean not null,
    league varchar(128),
    stash_name text,
    stash_type varchar(64),
    last_change_id varchar(256) not null,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null
);

create table collector.market_item_snapshot (
    id bigserial primary key,
    change_id varchar(256) not null,
    stash_id varchar(128) not null references collector.market_stash (stash_id),
    item_key varchar(128) not null,
    item_id varchar(128),
    league varchar(128) not null,
    realm varchar(32),
    item_name text,
    type_line text,
    base_type text,
    rarity varchar(32) not null,
    item_level integer,
    identified boolean not null,
    corrupted boolean not null,
    fractured boolean not null,
    synthesised boolean not null,
    elder boolean not null,
    shaper boolean not null,
    searing boolean not null,
    tangled boolean not null,
    width smallint,
    height smallint,
    x smallint,
    y smallint,
    price_amount numeric(30, 10),
    price_currency varchar(64),
    price_type varchar(16),
    price_source varchar(16),
    price_note text,
    raw_item_json jsonb not null,
    collected_at timestamptz not null,
    constraint uq_market_item_snapshot unique (change_id, stash_id, item_key),
    constraint ck_market_item_snapshot_price check (
        (price_amount is null
            and price_currency is null
            and price_type is null
            and price_source is null
            and price_note is null)
        or
        (price_amount > 0
            and price_currency is not null
            and price_type is not null
            and price_source in ('item', 'stash')
            and price_note is not null)
    )
);

create index idx_market_item_snapshot_league_collected
    on collector.market_item_snapshot (league, collected_at);

create index idx_market_item_snapshot_item_id
    on collector.market_item_snapshot (item_id)
    where item_id is not null;

create index idx_market_item_snapshot_base_type
    on collector.market_item_snapshot (base_type);

create index idx_market_item_snapshot_currency
    on collector.market_item_snapshot (league, price_currency, collected_at);
