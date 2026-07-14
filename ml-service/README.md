# PoE Pricer ML service

This service contains both offline CatBoost training and online FastAPI inference for
the first rare-bow model.

## Layout

```text
app/feature_extractor.py  shared train/predict feature extraction
app/model_bundle.py       model, schema and metadata loading
app/main.py               FastAPI endpoints
training/data_loader.py   completed-run PostgreSQL loader
training/train.py         split, CatBoost fit and artifact export
training/evaluation.py    product-oriented regression metrics
models/                   generated local artifacts
tests/                    extractor, metrics and API tests
```

## Environment

```text
DATABASE_URL
ML_TRAINING_RUN_KEYS
ML_HOLDOUT_RUN_KEY
ML_TRAINING_MAXIMUM_PRICE
ML_MODEL_DIR
```

Defaults are configured by the repository `docker-compose.yml`.

## Endpoints

```text
GET  /health/live
GET  /health/ready
GET  /model-info
POST /predict
```

The model accepts bows only. The request must wrap a raw PoE item object in the `item`
field. Both object-shaped Trade Fetch mods and string-shaped item mods are supported.

Training accepts repeated `--run-key` options. Older runs are used only for fitting;
validation and test rows are stratified samples from `--holdout-run-key`. This lets an
existing and a candidate model be compared on the same unseen collection window.

## Current evaluation caveats

- The target is an asking price, not a completed sale price.
- The examples were deliberately balanced across price ranges.
- All examples came from one league and two nearby collection windows.
- The newest collection window uses a random price-stratified split because historical
  sale outcomes do not exist.
- The model should be replaced after Public Stash data and temporal evaluation exist.
