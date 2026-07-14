from __future__ import annotations

import math
from typing import Sequence

import numpy as np


def regression_metrics(
    actual: Sequence[float],
    predicted: Sequence[float],
) -> dict[str, float]:
    actual_array = np.asarray(actual, dtype=float)
    predicted_array = np.maximum(np.asarray(predicted, dtype=float), 1e-9)
    if actual_array.shape != predicted_array.shape or actual_array.size == 0:
        raise ValueError("actual and predicted must be non-empty arrays with equal shapes")
    if np.any(actual_array <= 0):
        raise ValueError("actual prices must be positive")

    actual_log = np.log1p(actual_array)
    predicted_log = np.log1p(predicted_array)
    log_errors = predicted_log - actual_log
    ratios = np.maximum(
        predicted_array / actual_array,
        actual_array / predicted_array,
    )
    absolute_percentage_errors = np.abs(predicted_array - actual_array) / actual_array

    return {
        "rows": int(actual_array.size),
        "rmse_log": float(math.sqrt(np.mean(np.square(log_errors)))),
        "mae_log": float(np.mean(np.abs(log_errors))),
        "mae_divine": float(np.mean(np.abs(predicted_array - actual_array))),
        "median_absolute_percentage_error": float(
            np.median(absolute_percentage_errors)
        ),
        "median_factor_error": float(np.median(ratios)),
        "within_1_5x": float(np.mean(ratios <= 1.5)),
        "within_2x": float(np.mean(ratios <= 2.0)),
    }


def metrics_by_price_segment(
    actual: Sequence[float],
    predicted: Sequence[float],
) -> dict[str, dict[str, float]]:
    actual_array = np.asarray(actual, dtype=float)
    predicted_array = np.asarray(predicted, dtype=float)
    segments = {
        "1_to_10": (actual_array >= 1) & (actual_array < 10),
        "10_to_25": (actual_array >= 10) & (actual_array < 25),
        "25_to_100": (actual_array >= 25) & (actual_array < 100),
        "100_plus": actual_array >= 100,
    }
    return {
        name: regression_metrics(actual_array[mask], predicted_array[mask])
        for name, mask in segments.items()
        if np.any(mask)
    }

