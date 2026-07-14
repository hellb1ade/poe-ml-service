from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from catboost import CatBoostRegressor

from app.feature_extractor import FeatureSchema, transform_items


METADATA_FILE_NAME = "bow_model_metadata.json"


@dataclass(frozen=True)
class ModelMetadata:
    model_version: str
    model_file: str
    feature_schema_file: str
    currency: str
    category: str
    target_transform: str
    trained_at: str
    training_run_key: str
    training_rows: int
    metrics: Mapping[str, Any]

    @property
    def training_run_keys(self) -> tuple[str, ...]:
        return tuple(
            key.strip() for key in self.training_run_key.split(",") if key.strip()
        )

    @classmethod
    def from_dict(cls, value: Mapping[str, Any]) -> "ModelMetadata":
        return cls(
            model_version=str(value["model_version"]),
            model_file=str(value["model_file"]),
            feature_schema_file=str(value["feature_schema_file"]),
            currency=str(value["currency"]),
            category=str(value["category"]),
            target_transform=str(value["target_transform"]),
            trained_at=str(value["trained_at"]),
            training_run_key=str(value["training_run_key"]),
            training_rows=int(value["training_rows"]),
            metrics=value.get("metrics", {}),
        )


class BowPricePredictor:
    def __init__(
        self,
        model: CatBoostRegressor,
        schema: FeatureSchema,
        metadata: ModelMetadata,
    ) -> None:
        self._model = model
        self._schema = schema
        self._metadata = metadata

    @property
    def model_version(self) -> str:
        return self._metadata.model_version

    @property
    def currency(self) -> str:
        return self._metadata.currency

    @property
    def metadata(self) -> ModelMetadata:
        return self._metadata

    @classmethod
    def load(cls, model_dir: Path) -> "BowPricePredictor":
        metadata_path = model_dir / METADATA_FILE_NAME
        if not metadata_path.is_file():
            raise FileNotFoundError(f"Model metadata not found: {metadata_path}")

        metadata = ModelMetadata.from_dict(
            json.loads(metadata_path.read_text(encoding="utf-8"))
        )
        schema = FeatureSchema.load(model_dir / metadata.feature_schema_file)
        model = CatBoostRegressor()
        model.load_model(str(model_dir / metadata.model_file))

        trained_feature_names = tuple(model.feature_names_)
        if trained_feature_names and trained_feature_names != schema.feature_names:
            raise ValueError("Model feature names do not match the stored feature schema")
        return cls(model=model, schema=schema, metadata=metadata)

    def predict(self, item: Mapping[str, Any]) -> float:
        if not item:
            raise ValueError("Item must not be empty")
        base_type = item.get("baseType") or item.get("typeLine")
        if not isinstance(base_type, str) or "bow" not in base_type.lower():
            raise ValueError("The current model supports bows only")

        features = transform_items([item], self._schema)
        prediction_log = float(self._model.predict(features)[0])
        prediction = math.expm1(prediction_log)
        if not math.isfinite(prediction):
            raise ValueError("Model produced a non-finite prediction")
        return max(0.0, prediction)
