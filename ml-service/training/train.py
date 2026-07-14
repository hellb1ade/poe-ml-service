from __future__ import annotations

import argparse
import json
import os
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Sequence

import numpy as np
import pandas as pd
from catboost import CatBoostRegressor, Pool
from sklearn.model_selection import train_test_split

from app.feature_extractor import (
    FeatureSchema,
    build_feature_schema,
    transform_items,
)
from app.model_bundle import BowPricePredictor, METADATA_FILE_NAME
from training.data_loader import TrainingExample, load_training_examples
from training.evaluation import metrics_by_price_segment, regression_metrics


MODEL_FILE_NAME = "bow_price_model.cbm"
SCHEMA_FILE_NAME = "bow_feature_schema.json"
METRICS_FILE_NAME = "bow_model_metrics.json"
RANDOM_SEED = 42


def main() -> None:
    arguments = parse_arguments()
    run_keys = resolve_run_keys(arguments.run_keys)
    holdout_run_key = arguments.holdout_run_key or run_keys[-1]
    if holdout_run_key not in run_keys:
        raise ValueError("Holdout run key must also be a training run key")

    examples = load_training_examples(
        database_url=arguments.database_url,
        run_keys=run_keys,
        currency=arguments.currency,
        maximum_price=arguments.maximum_price,
    )
    if len(examples) < 200:
        raise ValueError("At least 200 training rows are required for the baseline")

    train_examples, validation_examples, test_examples = split_examples_by_run(
        examples,
        holdout_run_key,
    )

    schema = build_feature_schema(
        [example.item for example in train_examples],
        min_mod_frequency=arguments.min_mod_frequency,
        max_mod_features=arguments.max_mod_features,
    )
    train_frame = transform_items([example.item for example in train_examples], schema)
    validation_frame = transform_items(
        [example.item for example in validation_examples], schema
    )
    test_frame = transform_items([example.item for example in test_examples], schema)

    train_prices = prices(train_examples)
    validation_prices = prices(validation_examples)
    test_prices = prices(test_examples)
    train_target = np.log1p(train_prices)
    validation_target = np.log1p(validation_prices)
    train_weights = (
        balanced_price_segment_weights(train_prices)
        if arguments.balance_price_segments
        else None
    )
    validation_weights = (
        balanced_price_segment_weights(validation_prices)
        if arguments.balance_price_segments
        else None
    )

    train_pool = Pool(
        train_frame,
        label=train_target,
        weight=train_weights,
        cat_features=list(schema.categorical_features),
    )
    validation_pool = Pool(
        validation_frame,
        label=validation_target,
        weight=validation_weights,
        cat_features=list(schema.categorical_features),
    )

    trained_at = datetime.now(UTC)
    model_version = arguments.model_version or trained_at.strftime(
        "bow-mvp-%Y%m%dT%H%M%SZ"
    )
    model = CatBoostRegressor(
        iterations=arguments.iterations,
        depth=arguments.depth,
        learning_rate=arguments.learning_rate,
        loss_function="RMSE",
        eval_metric="RMSE",
        l2_leaf_reg=8.0,
        random_seed=RANDOM_SEED,
        allow_writing_files=False,
        verbose=arguments.verbose,
        metadata={
            "model_version": model_version,
            "training_run_key": ",".join(run_keys),
            "target_transform": "log1p",
        },
    )
    model.fit(
        train_pool,
        eval_set=validation_pool,
        early_stopping_rounds=arguments.early_stopping_rounds,
        use_best_model=True,
    )

    validation_predictions = inverse_target(model.predict(validation_frame))
    test_predictions = inverse_target(model.predict(test_frame))
    median_baseline = float(np.median(train_prices))
    baseline_predictions = np.full(test_prices.shape, median_baseline)

    metrics: dict[str, Any] = {
        "validation": regression_metrics(
            validation_prices, validation_predictions
        ),
        "test": regression_metrics(test_prices, test_predictions),
        "test_by_price_segment": metrics_by_price_segment(
            test_prices, test_predictions
        ),
        "median_baseline_test": regression_metrics(
            test_prices, baseline_predictions
        ),
    }
    if arguments.comparison_model_dir is not None:
        metrics["previous_model_same_test"] = evaluate_previous_model(
            arguments.comparison_model_dir,
            test_examples,
            test_prices,
        )
    feature_importances = top_feature_importances(model, schema, limit=30)

    model_dir = arguments.model_dir
    model_dir.mkdir(parents=True, exist_ok=True)
    model_path = model_dir / MODEL_FILE_NAME
    schema_path = model_dir / SCHEMA_FILE_NAME
    metrics_path = model_dir / METRICS_FILE_NAME
    metadata_path = model_dir / METADATA_FILE_NAME

    model.save_model(str(model_path))
    schema.save(schema_path)
    write_json(metrics_path, metrics)

    metadata = {
        "model_version": model_version,
        "model_file": MODEL_FILE_NAME,
        "feature_schema_file": SCHEMA_FILE_NAME,
        "metrics_file": METRICS_FILE_NAME,
        "currency": arguments.currency,
        "category": "weapon.bow",
        "target_transform": "log1p",
        "trained_at": trained_at.isoformat(),
        "training_run_key": ",".join(run_keys),
        "training_run_keys": list(run_keys),
        "holdout_run_key": holdout_run_key,
        "training_rows": len(examples),
        "source_rows": dict(sorted(Counter(example.run_key for example in examples).items())),
        "price_filter": {
            "minimum_exclusive": 0,
            "maximum_inclusive": arguments.maximum_price,
        },
        "split": {
            "strategy": "historical_runs_plus_stratified_latest_run",
            "random_seed": RANDOM_SEED,
            "train_rows": len(train_examples),
            "validation_rows": len(validation_examples),
            "test_rows": len(test_examples),
            "validation_run_key": holdout_run_key,
            "test_run_key": holdout_run_key,
        },
        "features": {
            "total": len(schema.feature_names),
            "categorical": list(schema.categorical_features),
            "selected_mod_templates": len(schema.mod_features),
            "min_mod_frequency": arguments.min_mod_frequency,
        },
        "training": {
            "best_iteration": model.get_best_iteration(),
            "tree_count": model.tree_count_,
            "iterations_limit": arguments.iterations,
            "depth": arguments.depth,
            "learning_rate": arguments.learning_rate,
            "balanced_price_segments": arguments.balance_price_segments,
        },
        "metrics": metrics,
        "top_feature_importances": feature_importances,
        "limitations": [
            "Listing prices are asking prices, not completed sale prices.",
            "The dataset is price-stratified and is not a natural market distribution.",
            "Validation and test rows are random stratified samples of the newest collection window.",
            "The model supports rare bows from the Mirage dataset only.",
            f"Listings above {arguments.maximum_price:g} {arguments.currency} are excluded.",
        ],
    }
    write_json(metadata_path, metadata)
    print(json.dumps(metadata, indent=2, sort_keys=True))


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train the rare-bow CatBoost model")
    parser.add_argument(
        "--database-url",
        default=os.getenv(
            "DATABASE_URL",
            "postgresql://postgres:postgres@postgres:5432/poe_pricer",
        ),
    )
    parser.add_argument(
        "--run-key",
        dest="run_keys",
        action="append",
        help="Completed dataset run key; repeat the option to combine runs",
    )
    parser.add_argument("--holdout-run-key", default=os.getenv("ML_HOLDOUT_RUN_KEY"))
    parser.add_argument("--currency", default="divine")
    parser.add_argument(
        "--maximum-price",
        type=float,
        default=float(os.getenv("ML_TRAINING_MAXIMUM_PRICE", "400")),
    )
    parser.add_argument(
        "--model-dir",
        type=Path,
        default=Path(os.getenv("ML_MODEL_DIR", "/service/models")),
    )
    parser.add_argument("--model-version")
    comparison_model_dir = os.getenv("ML_COMPARISON_MODEL_DIR")
    parser.add_argument(
        "--comparison-model-dir",
        type=Path,
        default=Path(comparison_model_dir) if comparison_model_dir else None,
    )
    parser.add_argument("--iterations", type=int, default=1200)
    parser.add_argument("--depth", type=int, default=7)
    parser.add_argument("--learning-rate", type=float, default=0.035)
    parser.add_argument("--early-stopping-rounds", type=int, default=120)
    parser.add_argument("--min-mod-frequency", type=int, default=2)
    parser.add_argument("--max-mod-features", type=int, default=256)
    parser.add_argument(
        "--balance-price-segments",
        action=argparse.BooleanOptionalAction,
        default=False,
    )
    parser.add_argument("--verbose", type=int, default=100)
    return parser.parse_args()


def resolve_run_keys(command_line_run_keys: Sequence[str] | None) -> tuple[str, ...]:
    if command_line_run_keys:
        values = command_line_run_keys
    else:
        configured = os.getenv("ML_TRAINING_RUN_KEYS") or os.getenv(
            "ML_TRAINING_RUN_KEY",
            "mirage-rare-bows-20260714-v2",
        )
        values = configured.split(",")

    normalized = tuple(dict.fromkeys(value.strip() for value in values if value.strip()))
    if not normalized:
        raise ValueError("At least one training run key is required")
    return normalized


def split_examples_by_run(
    examples: Sequence[TrainingExample],
    holdout_run_key: str,
) -> tuple[list[TrainingExample], list[TrainingExample], list[TrainingExample]]:
    historical_examples = [
        example for example in examples if example.run_key != holdout_run_key
    ]
    holdout_examples = [
        example for example in examples if example.run_key == holdout_run_key
    ]
    if len(holdout_examples) < 200:
        raise ValueError("At least 200 rows are required in the holdout run")

    train_indices, validation_indices, test_indices = split_indices(holdout_examples)
    train_examples = historical_examples + select_examples(
        holdout_examples,
        train_indices,
    )
    return (
        train_examples,
        select_examples(holdout_examples, validation_indices),
        select_examples(holdout_examples, test_indices),
    )


def split_indices(
    examples: Sequence[TrainingExample],
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    all_indices = np.arange(len(examples))
    log_prices = np.log1p(prices(examples))
    ranked = pd.Series(log_prices).rank(method="first")
    bins = pd.qcut(ranked, q=10, labels=False).to_numpy()

    train_indices, temporary_indices = train_test_split(
        all_indices,
        test_size=0.30,
        random_state=RANDOM_SEED,
        stratify=bins,
    )
    validation_indices, test_indices = train_test_split(
        temporary_indices,
        test_size=0.50,
        random_state=RANDOM_SEED,
        stratify=bins[temporary_indices],
    )
    return (
        np.sort(train_indices),
        np.sort(validation_indices),
        np.sort(test_indices),
    )


def select_examples(
    examples: Sequence[TrainingExample],
    indices: Sequence[int],
) -> list[TrainingExample]:
    return [examples[int(index)] for index in indices]


def prices(examples: Sequence[TrainingExample]) -> np.ndarray:
    return np.asarray([example.price for example in examples], dtype=float)


def inverse_target(predictions: Sequence[float]) -> np.ndarray:
    return np.maximum(np.expm1(np.asarray(predictions, dtype=float)), 0.0)


def balanced_price_segment_weights(actual_prices: Sequence[float]) -> np.ndarray:
    actual = np.asarray(actual_prices, dtype=float)
    if actual.size == 0 or np.any(actual <= 0):
        raise ValueError("Prices for segment balancing must be positive")

    segment_ids = np.select(
        [actual < 10, actual < 25, actual < 100],
        [0, 1, 2],
        default=3,
    )
    segment_counts = np.bincount(segment_ids, minlength=4)
    present_segments = segment_counts > 0
    segment_weights = np.zeros(4, dtype=float)
    segment_weights[present_segments] = actual.size / (
        int(np.sum(present_segments)) * segment_counts[present_segments]
    )
    return segment_weights[segment_ids]


def evaluate_previous_model(
    model_dir: Path,
    test_examples: Sequence[TrainingExample],
    test_prices: np.ndarray,
) -> dict[str, Any]:
    predictor = BowPricePredictor.load(model_dir)
    predictions = np.asarray(
        [predictor.predict(example.item) for example in test_examples],
        dtype=float,
    )
    return {
        "model_version": predictor.model_version,
        "training_run_key": predictor.metadata.training_run_key,
        "test": regression_metrics(test_prices, predictions),
        "test_by_price_segment": metrics_by_price_segment(test_prices, predictions),
    }


def top_feature_importances(
    model: CatBoostRegressor,
    schema: FeatureSchema,
    limit: int,
) -> list[dict[str, float | str]]:
    importances = model.get_feature_importance()
    pairs = sorted(
        zip(schema.feature_names, importances, strict=True),
        key=lambda pair: float(pair[1]),
        reverse=True,
    )[:limit]
    return [
        {"feature": name, "importance": float(importance)}
        for name, importance in pairs
    ]


def write_json(path: Path, value: Any) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    temporary.replace(path)


if __name__ == "__main__":
    main()
