from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import create_app
from app.model_bundle import ModelMetadata


class FakePredictor:
    model_version = "test-model"
    currency = "divine"
    metadata = ModelMetadata(
        model_version="test-model",
        model_file="test.cbm",
        feature_schema_file="schema.json",
        currency="divine",
        category="weapon.bow",
        target_transform="log1p",
        trained_at="2026-07-14T00:00:00+00:00",
        training_run_key="test-run",
        training_rows=1000,
        metrics={"test": {"within_2x": 0.5}},
    )

    def predict(self, item) -> float:
        if not item:
            raise ValueError("Item must not be empty")
        return 12.34567


def test_predict_endpoint_returns_model_contract() -> None:
    app = create_app(predictor=FakePredictor())

    with TestClient(app) as client:
        response = client.post(
            "/predict",
            json={"item": {"baseType": "Spine Bow", "ilvl": 86}},
        )

    assert response.status_code == 200
    assert response.json() == {
        "predictedPrice": 12.3457,
        "currency": "divine",
        "modelVersion": "test-model",
    }


def test_health_and_model_info_endpoints() -> None:
    app = create_app(predictor=FakePredictor())

    with TestClient(app) as client:
        health = client.get("/health/ready")
        info = client.get("/model-info")

    assert health.status_code == 200
    assert health.json()["modelLoaded"] is True
    assert info.json()["trainingRunKey"] == "test-run"
    assert info.json()["trainingRunKeys"] == ["test-run"]


def test_predict_rejects_empty_item() -> None:
    app = create_app(predictor=FakePredictor())

    with TestClient(app) as client:
        response = client.post("/predict", json={"item": {}})

    assert response.status_code == 422
    assert response.json()["detail"] == "Item must not be empty"
