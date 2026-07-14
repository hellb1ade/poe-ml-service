alter table collector.trade_dataset_run
    drop constraint ck_trade_dataset_run_requested_items;

alter table collector.trade_dataset_run
    add constraint ck_trade_dataset_run_requested_items
        check (requested_items between 1 and 10000);
