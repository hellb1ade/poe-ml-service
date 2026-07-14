from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class PredictRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    item: dict[str, Any] = Field(description="Raw Path of Exile item object")


class PredictResponse(BaseModel):
    predictedPrice: float
    currency: str
    modelVersion: str


class HealthResponse(BaseModel):
    status: str
    modelLoaded: bool
    modelVersion: str | None = None


class ModelInfoResponse(BaseModel):
    modelVersion: str
    category: str
    currency: str
    trainedAt: str
    trainingRunKey: str
    trainingRunKeys: list[str]
    trainingRows: int
    testMetrics: dict[str, Any]
