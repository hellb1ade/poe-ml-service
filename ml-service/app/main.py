from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncIterator

from fastapi import FastAPI, HTTPException

from app.config import Settings
from app.model_bundle import BowPricePredictor
from app.schemas import (
    HealthResponse,
    ModelInfoResponse,
    PredictRequest,
    PredictResponse,
)


LOGGER = logging.getLogger(__name__)


def create_app(
    predictor: BowPricePredictor | None = None,
    model_dir: Path | None = None,
) -> FastAPI:
    settings = Settings.from_environment()
    resolved_model_dir = model_dir or settings.model_dir

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        loaded_predictor = predictor
        if loaded_predictor is None:
            try:
                loaded_predictor = BowPricePredictor.load(resolved_model_dir)
                LOGGER.info("Loaded model version %s", loaded_predictor.model_version)
            except (FileNotFoundError, ValueError) as exception:
                LOGGER.warning("ML model is not ready: %s", exception)
        application.state.predictor = loaded_predictor
        yield

    application = FastAPI(
        title="PoE Pricer ML Service",
        version="0.1.0",
        lifespan=lifespan,
    )

    @application.get("/health/live", response_model=HealthResponse)
    def live() -> HealthResponse:
        current = application.state.predictor
        return HealthResponse(
            status="ok",
            modelLoaded=current is not None,
            modelVersion=current.model_version if current else None,
        )

    @application.get("/health/ready", response_model=HealthResponse)
    def ready() -> HealthResponse:
        current = _require_predictor(application)
        return HealthResponse(
            status="ready",
            modelLoaded=True,
            modelVersion=current.model_version,
        )

    @application.get("/model-info", response_model=ModelInfoResponse)
    def model_info() -> ModelInfoResponse:
        current = _require_predictor(application)
        metadata = current.metadata
        test_metrics = metadata.metrics.get("test", {})
        return ModelInfoResponse(
            modelVersion=metadata.model_version,
            category=metadata.category,
            currency=metadata.currency,
            trainedAt=metadata.trained_at,
            trainingRunKey=metadata.training_run_key,
            trainingRunKeys=list(metadata.training_run_keys),
            trainingRows=metadata.training_rows,
            testMetrics=dict(test_metrics),
        )

    @application.post("/predict", response_model=PredictResponse)
    def predict(request: PredictRequest) -> PredictResponse:
        current = _require_predictor(application)
        try:
            price = current.predict(request.item)
        except ValueError as exception:
            raise HTTPException(status_code=422, detail=str(exception)) from exception
        return PredictResponse(
            predictedPrice=round(price, 4),
            currency=current.currency,
            modelVersion=current.model_version,
        )

    return application


def _require_predictor(application: FastAPI) -> BowPricePredictor:
    predictor = application.state.predictor
    if predictor is None:
        raise HTTPException(status_code=503, detail="ML model is not loaded")
    return predictor


app = create_app()
