# poe-pricer

ML-assisted price estimation for Path of Exile items.

This product isn't affiliated with or endorsed by Grinding Gear Games in any way.

## Services

- `api-service` - public Spring Boot API, port `8080`.
- `collector-service` - official Public Stash stream collector, port `8081`.
- `ml-service` - Python 3.12, FastAPI and CatBoost prediction service, port `8000`.
- PostgreSQL 16 - raw market observations and collector checkpoint.

## Start PostgreSQL

```bash
docker compose up -d postgres
```

The database is persisted in the `postgres-data` Docker volume. Collector tables and
Flyway history live in the dedicated `collector` PostgreSQL schema.

## Run the collector

The Public Stash API requires a confidential OAuth client using the
`client_credentials` grant and the `service:psapi` scope. Do not commit credentials.

Required environment variables:

```text
POE_COLLECTOR_ENABLED=true
POE_LEAGUE=Mirage
POE_CLIENT_ID=<registered client id>
POE_CLIENT_SECRET=<registered client secret>
POE_CONTACT=<project contact>
```

For a temporary local smoke test, `POE_ACCESS_TOKEN` can be supplied instead of
`POE_CLIENT_SECRET`. `POE_CLIENT_ID` and `POE_CONTACT` are still required for the
identifiable User-Agent mandated by GGG.

Run from IntelliJ with `CollectorServiceApplication`, or from a terminal:

```bash
cd collector-service
./mvnw spring-boot:run
```

Useful checks:

```sql
select * from collector.collector_cursor;

select league, base_type, price_amount, price_currency, collected_at
from collector.market_item_snapshot
order by collected_at desc
limit 20;
```

The collector stores only priced Rare items by default. Each row contains normalized
identity, price and filtering fields plus the complete source item in `raw_item_json`.
Rate limits are read dynamically from `X-Rate-Limit-*` and `Retry-After` headers.

GGG does not publish one permanent numeric request limit. Limits can change at runtime,
so the collector reads every `client`, `account`, and `ip` bucket, waits before a bucket
is exhausted, honors `Retry-After` on `429`, and retries only network errors, `408`,
`429`, and `5xx`. Public Stash responses are delayed by about five minutes.

- [Path of Exile API rules and rate limits](https://www.pathofexile.com/developer/docs)
- [Public Stash API reference](https://www.pathofexile.com/developer/docs/reference)
- [OAuth scopes and client credentials](https://www.pathofexile.com/developer/docs/authorization)

## Train the bow model

The current model combines completed Trade dataset runs
`mirage-rare-bows-20260714-v2` and `mirage-rare-bows-20260714-v6`. Training excludes
listings above 400 divine, reads PostgreSQL directly, extracts item-only features and
predicts `log1p(price_divine)`.

```bash
docker compose build ml-service
docker compose run --rm ml-service python -m training.train
```

Generated artifacts are written to `ml-service/models/`:

```text
bow_price_model.cbm
bow_feature_schema.json
bow_model_metrics.json
bow_model_metadata.json
```

The feature extractor deliberately ignores `item.note`, `extended.text`, listing data,
the sampling bucket and search rank because they contain or reveal the target price.
Training and inference import the same extractor from `app/feature_extractor.py`.

## Run the ML service

```bash
docker compose up -d ml-service
curl http://localhost:8000/health/ready
curl http://localhost:8000/model-info
```

Prediction accepts one raw Path of Exile item object:

```bash
curl http://localhost:8000/predict \
  -H 'Content-Type: application/json' \
  --data '{
    "item": {
      "baseType": "Spine Bow",
      "ilvl": 86,
      "explicitMods": [
        "Adds 47 to 84 Physical Damage",
        "Bow Attacks fire 2 additional Arrows"
      ]
    }
  }'
```

`bow-mvp-002` loaded 3938 unique examples. All older rows and 70% of the latest run are
used for fitting; validation and test each contain 450 rows sampled only from the
latest run. On that shared test set, the old model produced 45.8% of predictions within
a factor of two and the new model produced 53.1%. The median factor error improved from
2.13 to 1.90.

This is still a pipeline proof of concept, not a reliable price checker. Listing prices
are noisy asking prices, the sample is price-stratified, and accuracy above 100 divine
remains poor.

Run Python tests in the service image:

```bash
docker compose run --rm --no-deps ml-service pytest -q
```
