from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    model_dir: Path

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(model_dir=Path(os.getenv("ML_MODEL_DIR", "/service/models")))

