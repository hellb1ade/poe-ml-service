from __future__ import annotations

import hashlib
import json
import math
import re
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

import numpy as np
import pandas as pd


SCHEMA_VERSION = "bow-features-v1"
MOD_VALUE_SLOTS = 4

FIXED_NUMERIC_FEATURES = (
    "item_level",
    "quality",
    "physical_damage_min",
    "physical_damage_max",
    "elemental_damage_min",
    "elemental_damage_max",
    "critical_strike_chance",
    "attacks_per_second",
    "dps",
    "physical_dps",
    "elemental_dps",
    "requirement_level",
    "requirement_strength",
    "requirement_dexterity",
    "requirement_intelligence",
    "socket_count",
    "max_link_count",
    "white_socket_count",
    "explicit_mod_count",
    "crafted_mod_count",
    "fractured_mod_count",
    "implicit_mod_count",
    "enchant_mod_count",
    "veiled_mod_count",
    "influence_count",
    "identified",
    "corrupted",
    "fractured",
    "synthesised",
    "mirrored",
    "split",
    "veiled",
    "elder",
    "shaper",
    "memory_item",
    "duplicated",
)

CATEGORICAL_FEATURES = (
    "base_type",
    "influence_signature",
)

MOD_ARRAYS = (
    ("explicitMods", "explicit"),
    ("craftedMods", "crafted"),
    ("fracturedMods", "fractured"),
    ("implicitMods", "implicit"),
    ("enchantMods", "enchant"),
    ("veiledMods", "veiled"),
)

KNOWN_MOD_KINDS = {
    "explicit",
    "crafted",
    "fractured",
    "implicit",
    "enchant",
    "veiled",
}

NUMBER_PATTERN = re.compile(r"(?<![\d.])[-+]?\d+(?:\.\d+)?")
SPACE_PATTERN = re.compile(r"\s+")


@dataclass(frozen=True)
class ModObservation:
    key: str
    kind: str
    template: str
    values: tuple[float, ...]
    tier: float
    stat_hashes: tuple[str, ...]


@dataclass(frozen=True)
class RawItemFeatures:
    numeric: Mapping[str, float]
    categorical: Mapping[str, str]
    mods: Mapping[str, ModObservation]


@dataclass(frozen=True)
class ModFeature:
    key: str
    kind: str
    template: str
    stat_hashes: tuple[str, ...]
    frequency: int


@dataclass(frozen=True)
class FeatureSchema:
    version: str
    fixed_numeric_features: tuple[str, ...]
    categorical_features: tuple[str, ...]
    mod_features: tuple[ModFeature, ...]
    mod_value_slots: int = MOD_VALUE_SLOTS

    @property
    def dynamic_numeric_features(self) -> tuple[str, ...]:
        names: list[str] = []
        for mod in self.mod_features:
            names.append(f"mod_{mod.key}_present")
            names.append(f"mod_{mod.key}_tier")
            for index in range(self.mod_value_slots):
                names.append(f"mod_{mod.key}_value_{index}")
        return tuple(names)

    @property
    def numeric_features(self) -> tuple[str, ...]:
        return self.fixed_numeric_features + self.dynamic_numeric_features

    @property
    def feature_names(self) -> tuple[str, ...]:
        return self.numeric_features + self.categorical_features

    def to_dict(self) -> dict[str, Any]:
        return {
            "version": self.version,
            "fixed_numeric_features": list(self.fixed_numeric_features),
            "categorical_features": list(self.categorical_features),
            "mod_value_slots": self.mod_value_slots,
            "mod_features": [asdict(feature) for feature in self.mod_features],
        }

    @classmethod
    def from_dict(cls, value: Mapping[str, Any]) -> "FeatureSchema":
        return cls(
            version=str(value["version"]),
            fixed_numeric_features=tuple(value["fixed_numeric_features"]),
            categorical_features=tuple(value["categorical_features"]),
            mod_value_slots=int(value.get("mod_value_slots", MOD_VALUE_SLOTS)),
            mod_features=tuple(
                ModFeature(
                    key=str(feature["key"]),
                    kind=str(feature["kind"]),
                    template=str(feature["template"]),
                    stat_hashes=tuple(feature.get("stat_hashes", ())),
                    frequency=int(feature["frequency"]),
                )
                for feature in value["mod_features"]
            ),
        )

    def save(self, path: Path) -> None:
        path.write_text(
            json.dumps(self.to_dict(), indent=2, sort_keys=True),
            encoding="utf-8",
        )

    @classmethod
    def load(cls, path: Path) -> "FeatureSchema":
        return cls.from_dict(json.loads(path.read_text(encoding="utf-8")))


def build_feature_schema(
    items: Sequence[Mapping[str, Any]],
    min_mod_frequency: int = 2,
    max_mod_features: int = 256,
) -> FeatureSchema:
    if min_mod_frequency < 1:
        raise ValueError("min_mod_frequency must be positive")
    if max_mod_features < 1:
        raise ValueError("max_mod_features must be positive")

    frequencies: Counter[str] = Counter()
    observations: dict[str, ModObservation] = {}
    hashes: dict[str, set[str]] = {}

    for item in items:
        raw = extract_raw_features(item)
        frequencies.update(raw.mods.keys())
        for key, observation in raw.mods.items():
            observations.setdefault(key, observation)
            hashes.setdefault(key, set()).update(observation.stat_hashes)

    selected_keys = [
        key
        for key, frequency in sorted(
            frequencies.items(),
            key=lambda entry: (-entry[1], entry[0]),
        )
        if frequency >= min_mod_frequency
    ][:max_mod_features]

    mod_features = tuple(
        ModFeature(
            key=key,
            kind=observations[key].kind,
            template=observations[key].template,
            stat_hashes=tuple(sorted(hashes.get(key, set()))),
            frequency=frequencies[key],
        )
        for key in selected_keys
    )
    return FeatureSchema(
        version=SCHEMA_VERSION,
        fixed_numeric_features=FIXED_NUMERIC_FEATURES,
        categorical_features=CATEGORICAL_FEATURES,
        mod_features=mod_features,
    )


def transform_items(
    items: Sequence[Mapping[str, Any]],
    schema: FeatureSchema,
) -> pd.DataFrame:
    if schema.version != SCHEMA_VERSION:
        raise ValueError(
            f"Unsupported feature schema '{schema.version}', expected '{SCHEMA_VERSION}'"
        )

    rows: list[dict[str, float | str]] = []
    for item in items:
        raw = extract_raw_features(item)
        row: dict[str, float | str] = {
            name: _finite_or_nan(raw.numeric.get(name, math.nan))
            for name in schema.fixed_numeric_features
        }
        for feature in schema.mod_features:
            observation = raw.mods.get(feature.key)
            row[f"mod_{feature.key}_present"] = 1.0 if observation else 0.0
            row[f"mod_{feature.key}_tier"] = (
                _finite_or_nan(observation.tier) if observation else math.nan
            )
            for index in range(schema.mod_value_slots):
                value = (
                    observation.values[index]
                    if observation and index < len(observation.values)
                    else math.nan
                )
                row[f"mod_{feature.key}_value_{index}"] = _finite_or_nan(value)
        for name in schema.categorical_features:
            value = raw.categorical.get(name)
            row[name] = value if value else "unknown"
        rows.append(row)

    return pd.DataFrame(rows, columns=list(schema.feature_names))


def extract_raw_features(item: Mapping[str, Any]) -> RawItemFeatures:
    properties = _named_values(item.get("properties"))
    requirements = _named_values(item.get("requirements"))
    extended = _mapping(item.get("extended"))
    sockets = item.get("sockets") if isinstance(item.get("sockets"), list) else []
    mods = _extract_mods(item)

    physical_values = _numbers_from_values(properties.get("Physical Damage", ()))
    elemental_values = _numbers_from_values(properties.get("Elemental Damage", ()))
    attacks_per_second = _first_number(properties.get("Attacks per Second", ()))

    physical_min, physical_max = _range_totals(physical_values)
    elemental_min, elemental_max = _range_totals(elemental_values)
    physical_dps = _number(extended.get("pdps"))
    elemental_dps = _number(extended.get("edps"))
    total_dps = _number(extended.get("dps"))

    if math.isnan(physical_dps) and not math.isnan(attacks_per_second):
        physical_dps = _average_range(physical_min, physical_max) * attacks_per_second
    if math.isnan(elemental_dps) and not math.isnan(attacks_per_second):
        elemental_dps = _average_range(elemental_min, elemental_max) * attacks_per_second
    if math.isnan(total_dps):
        components = [value for value in (physical_dps, elemental_dps) if not math.isnan(value)]
        total_dps = sum(components) if components else math.nan

    socket_groups = Counter(
        socket.get("group")
        for socket in sockets
        if isinstance(socket, Mapping) and isinstance(socket.get("group"), int)
    )
    influences = _influences(item)
    mod_kind_counts = Counter(observation.kind for observation in mods.values())

    numeric = {
        "item_level": _number(item.get("ilvl", item.get("itemLevel"))),
        "quality": _first_number(properties.get("Quality", ())),
        "physical_damage_min": physical_min,
        "physical_damage_max": physical_max,
        "elemental_damage_min": elemental_min,
        "elemental_damage_max": elemental_max,
        "critical_strike_chance": _first_number(
            properties.get("Critical Strike Chance", ())
        ),
        "attacks_per_second": attacks_per_second,
        "dps": total_dps,
        "physical_dps": physical_dps,
        "elemental_dps": elemental_dps,
        "requirement_level": _first_number(requirements.get("Level", ())),
        "requirement_strength": _first_number(requirements.get("Str", ())),
        "requirement_dexterity": _first_number(requirements.get("Dex", ())),
        "requirement_intelligence": _first_number(requirements.get("Int", ())),
        "socket_count": float(len(sockets)),
        "max_link_count": float(max(socket_groups.values(), default=0)),
        "white_socket_count": float(
            sum(
                1
                for socket in sockets
                if isinstance(socket, Mapping) and socket.get("sColour") == "W"
            )
        ),
        "explicit_mod_count": float(mod_kind_counts["explicit"]),
        "crafted_mod_count": float(mod_kind_counts["crafted"]),
        "fractured_mod_count": float(mod_kind_counts["fractured"]),
        "implicit_mod_count": float(mod_kind_counts["implicit"]),
        "enchant_mod_count": float(mod_kind_counts["enchant"]),
        "veiled_mod_count": float(mod_kind_counts["veiled"]),
        "influence_count": float(len(influences)),
        "identified": _boolean_number(item.get("identified"), default=True),
        "corrupted": _boolean_number(item.get("corrupted")),
        "fractured": _boolean_number(item.get("fractured")),
        "synthesised": _boolean_number(item.get("synthesised")),
        "mirrored": _boolean_number(item.get("mirrored")),
        "split": _boolean_number(item.get("split")),
        "veiled": _boolean_number(item.get("veiled")),
        "elder": _boolean_number(item.get("elder")),
        "shaper": _boolean_number(item.get("shaper")),
        "memory_item": _boolean_number(item.get("memoryItem")),
        "duplicated": _boolean_number(item.get("duplicated")),
    }
    base_type = item.get("baseType") or item.get("typeLine") or "unknown"
    categorical = {
        "base_type": str(base_type),
        "influence_signature": "+".join(influences) if influences else "none",
    }
    return RawItemFeatures(numeric=numeric, categorical=categorical, mods=mods)


def _extract_mods(item: Mapping[str, Any]) -> dict[str, ModObservation]:
    observations: dict[str, ModObservation] = {}
    for field, default_kind in MOD_ARRAYS:
        values = item.get(field)
        if not isinstance(values, list):
            continue
        for value in values:
            observation = _mod_observation(value, default_kind)
            if observation is None:
                continue
            existing = observations.get(observation.key)
            observations[observation.key] = (
                observation if existing is None else _merge_observations(existing, observation)
            )
    return observations


def _mod_observation(value: Any, default_kind: str) -> ModObservation | None:
    stat_hash: str | None = None
    tier = math.nan
    if isinstance(value, str):
        description = value
        kind = default_kind
    elif isinstance(value, Mapping):
        description_value = value.get("description") or value.get("text")
        if not isinstance(description_value, str):
            return None
        description = description_value
        stat_hash_value = value.get("hash")
        stat_hash = stat_hash_value if isinstance(stat_hash_value, str) else None
        kind = _kind_from_hash(stat_hash) or default_kind
        tier = _tier_from_mod_details(value.get("mods"))
    else:
        return None

    template = _normalize_mod_template(description)
    if not template:
        return None
    digest = hashlib.sha1(f"{kind}|{template}".encode("utf-8")).hexdigest()[:16]
    return ModObservation(
        key=f"{kind}_{digest}",
        kind=kind,
        template=template,
        values=tuple(_numbers(description)[:MOD_VALUE_SLOTS]),
        tier=tier,
        stat_hashes=(stat_hash,) if stat_hash else (),
    )


def _merge_observations(
    left: ModObservation,
    right: ModObservation,
) -> ModObservation:
    values = left.values if len(left.values) >= len(right.values) else right.values
    tiers = [tier for tier in (left.tier, right.tier) if not math.isnan(tier)]
    return ModObservation(
        key=left.key,
        kind=left.kind,
        template=left.template,
        values=values,
        tier=min(tiers) if tiers else math.nan,
        stat_hashes=tuple(sorted(set(left.stat_hashes + right.stat_hashes))),
    )


def _kind_from_hash(stat_hash: str | None) -> str | None:
    if not stat_hash:
        return None
    for component in stat_hash.lower().split("."):
        if component in KNOWN_MOD_KINDS:
            return component
    return None


def _tier_from_mod_details(value: Any) -> float:
    if not isinstance(value, list):
        return math.nan
    tiers: list[float] = []
    for detail in value:
        if not isinstance(detail, Mapping):
            continue
        tier = detail.get("tier")
        if not isinstance(tier, str):
            continue
        numbers = NUMBER_PATTERN.findall(tier)
        if numbers:
            tiers.append(float(numbers[0]))
    return min(tiers) if tiers else math.nan


def _normalize_mod_template(value: str) -> str:
    lowered = value.lower().replace("\n", " ")
    without_numbers = NUMBER_PATTERN.sub("#", lowered)
    return SPACE_PATTERN.sub(" ", without_numbers).strip()


def _named_values(value: Any) -> dict[str, tuple[str, ...]]:
    result: dict[str, tuple[str, ...]] = {}
    if not isinstance(value, list):
        return result
    for entry in value:
        if not isinstance(entry, Mapping) or not isinstance(entry.get("name"), str):
            continue
        raw_values = entry.get("values")
        strings: list[str] = []
        if isinstance(raw_values, list):
            for raw_value in raw_values:
                if (
                    isinstance(raw_value, list)
                    and raw_value
                    and isinstance(raw_value[0], str)
                ):
                    strings.append(raw_value[0])
        result[entry["name"]] = tuple(strings)
    return result


def _numbers_from_values(values: Iterable[str]) -> list[float]:
    result: list[float] = []
    for value in values:
        result.extend(_numbers(value))
    return result


def _numbers(value: str) -> list[float]:
    return [float(match) for match in NUMBER_PATTERN.findall(value.replace(",", ""))]


def _first_number(values: Iterable[str]) -> float:
    numbers = _numbers_from_values(values)
    return numbers[0] if numbers else math.nan


def _range_totals(values: Sequence[float]) -> tuple[float, float]:
    if not values:
        return math.nan, math.nan
    if len(values) == 1:
        return values[0], values[0]
    minima = values[0::2]
    maxima = values[1::2]
    return sum(minima), sum(maxima)


def _average_range(minimum: float, maximum: float) -> float:
    if math.isnan(minimum) or math.isnan(maximum):
        return 0.0
    return (minimum + maximum) / 2.0


def _mapping(value: Any) -> Mapping[str, Any]:
    return value if isinstance(value, Mapping) else {}


def _number(value: Any) -> float:
    if isinstance(value, bool):
        return float(value)
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        numbers = _numbers(value)
        return numbers[0] if numbers else math.nan
    return math.nan


def _boolean_number(value: Any, default: bool = False) -> float:
    return 1.0 if (value if isinstance(value, bool) else default) else 0.0


def _influences(item: Mapping[str, Any]) -> tuple[str, ...]:
    names: set[str] = set()
    raw = item.get("influences")
    if isinstance(raw, Mapping):
        names.update(str(name).lower() for name, enabled in raw.items() if enabled)
    for name in ("elder", "shaper", "searing", "tangled", "crusader", "hunter", "redeemer", "warlord"):
        if item.get(name) is True:
            names.add(name)
    return tuple(sorted(names))


def _finite_or_nan(value: float) -> float:
    return float(value) if np.isfinite(value) else math.nan
