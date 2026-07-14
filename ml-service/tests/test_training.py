from __future__ import annotations

import numpy as np
import pytest

from training.data_loader import TrainingExample
from training.train import (
    balanced_price_segment_weights,
    resolve_run_keys,
    split_examples_by_run,
)


def example(run_key: str, ordinal: int, price: float) -> TrainingExample:
    return TrainingExample(
        run_key=run_key,
        item_id=f"{run_key}-item-{ordinal}",
        listing_id=f"{run_key}-listing-{ordinal}",
        item={"baseType": "Spine Bow", "ilvl": 86},
        price=price,
    )


def test_split_uses_historical_rows_only_for_training() -> None:
    historical = [example("old", ordinal, ordinal + 1.0) for ordinal in range(5)]
    latest = [example("latest", ordinal, ordinal + 1.0) for ordinal in range(200)]

    train, validation, test = split_examples_by_run(
        historical + latest,
        "latest",
    )

    assert len(train) == 145
    assert len(validation) == 30
    assert len(test) == 30
    assert {row.item_id for row in historical} <= {row.item_id for row in train}
    assert all(row.run_key == "latest" for row in validation + test)
    assert not ({row.item_id for row in train} & {row.item_id for row in test})


def test_resolve_run_keys_supports_comma_separated_environment(
    monkeypatch,
) -> None:
    monkeypatch.setenv("ML_TRAINING_RUN_KEYS", "old, latest,old")

    assert resolve_run_keys(None) == ("old", "latest")


def test_balanced_weights_give_each_price_segment_equal_total_weight() -> None:
    prices = np.asarray([2.0, 3.0, 12.0, 50.0, 60.0, 70.0, 200.0])

    weights = balanced_price_segment_weights(prices)

    segment_totals = [
        weights[prices < 10].sum(),
        weights[(prices >= 10) & (prices < 25)].sum(),
        weights[(prices >= 25) & (prices < 100)].sum(),
        weights[prices >= 100].sum(),
    ]
    assert segment_totals == pytest.approx([1.75, 1.75, 1.75, 1.75])
    assert weights.mean() == pytest.approx(1.0)
