from __future__ import annotations

import pytest

from training.evaluation import metrics_by_price_segment, regression_metrics


def test_regression_metrics_report_factor_accuracy() -> None:
    metrics = regression_metrics([10.0, 20.0, 100.0], [10.0, 30.0, 50.0])

    assert metrics["rows"] == 3
    assert metrics["within_2x"] == 1.0
    assert metrics["within_1_5x"] == pytest.approx(2 / 3)
    assert metrics["median_factor_error"] == 1.5


def test_metrics_are_split_into_price_segments() -> None:
    metrics = metrics_by_price_segment(
        [2.0, 12.0, 50.0, 200.0],
        [2.0, 12.0, 50.0, 200.0],
    )

    assert set(metrics) == {"1_to_10", "10_to_25", "25_to_100", "100_plus"}
    assert all(segment["within_1_5x"] == 1.0 for segment in metrics.values())


def test_metrics_reject_non_positive_targets() -> None:
    with pytest.raises(ValueError):
        regression_metrics([0.0], [1.0])

