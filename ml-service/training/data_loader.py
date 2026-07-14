from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Mapping, Sequence

import psycopg
from psycopg.rows import dict_row


@dataclass(frozen=True)
class TrainingExample:
    run_key: str
    item_id: str
    listing_id: str
    item: Mapping[str, Any]
    price: float


TRAINING_QUERY = """
    select
        r.run_key,
        s.item_id,
        s.listing_id,
        s.raw_item_json,
        s.price_amount::double precision as price_amount
    from collector.trade_listing_snapshot s
    join collector.trade_dataset_run r on r.id = s.run_id
    where r.run_key = any(%s::varchar[])
      and r.status = 'COMPLETED'
      and r.item_category = 'weapon.bow'
      and s.rarity = 'Rare'
      and s.price_currency = %s
      and s.price_amount > 0
      and s.price_amount <= %s
    order by r.started_at, s.sample_ordinal
"""


def load_training_examples(
    database_url: str,
    run_keys: Sequence[str],
    currency: str = "divine",
    maximum_price: float = 400.0,
) -> list[TrainingExample]:
    normalized_run_keys = tuple(dict.fromkeys(run_keys))
    if not normalized_run_keys:
        raise ValueError("At least one training run key is required")
    if maximum_price <= 0:
        raise ValueError("Maximum training price must be positive")

    with psycopg.connect(database_url, row_factory=dict_row) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                TRAINING_QUERY,
                (list(normalized_run_keys), currency, maximum_price),
            )
            rows = cursor.fetchall()

    examples: list[TrainingExample] = []
    seen_items: set[str] = set()
    for row in rows:
        raw_item_id = row["item_id"]
        if raw_item_id is None:
            continue
        item_id = str(raw_item_id)
        if item_id in seen_items:
            continue
        raw_item = row["raw_item_json"]
        if isinstance(raw_item, str):
            raw_item = json.loads(raw_item)
        if not isinstance(raw_item, Mapping):
            continue
        examples.append(
            TrainingExample(
                run_key=str(row["run_key"]),
                item_id=item_id,
                listing_id=str(row["listing_id"]),
                item=raw_item,
                price=float(row["price_amount"]),
            )
        )
        seen_items.add(item_id)

    if not examples:
        raise ValueError(
            "No completed rare-bow training rows found for runs "
            f"{', '.join(normalized_run_keys)}"
        )
    return examples
