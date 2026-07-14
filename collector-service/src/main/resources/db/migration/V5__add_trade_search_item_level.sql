alter table collector.trade_dataset_search
    add column minimum_item_level integer,
    add column maximum_item_level integer;

alter table collector.trade_dataset_search
    add constraint ck_trade_dataset_search_item_levels check (
        (minimum_item_level is null or minimum_item_level >= 0)
        and (maximum_item_level is null or maximum_item_level >= 0)
        and (
            minimum_item_level is null
            or maximum_item_level is null
            or maximum_item_level >= minimum_item_level
        )
    );
