from __future__ import annotations

from copy import deepcopy

import pytest

from app.feature_extractor import (
    build_feature_schema,
    extract_raw_features,
    transform_items,
)


TRAINING_ITEM = {
    "id": "item-1",
    "name": "Doom Branch",
    "baseType": "Spine Bow",
    "rarity": "Rare",
    "ilvl": 86,
    "identified": True,
    "fractured": True,
    "note": "~b/o 100 divine",
    "properties": [
        {"name": "Quality", "values": [["+20%", 1]]},
        {"name": "Physical Damage", "values": [["100-250", 1]]},
        {
            "name": "Elemental Damage",
            "values": [["10-20", 4], ["30-50", 5]],
        },
        {"name": "Critical Strike Chance", "values": [["7.25%", 1]]},
        {"name": "Attacks per Second", "values": [["1.50", 1]]},
    ],
    "requirements": [
        {"name": "Level", "values": [["68", 0]]},
        {"name": "Dex", "values": [["212", 0]]},
    ],
    "sockets": [
        {"group": 0, "sColour": "G"},
        {"group": 0, "sColour": "G"},
        {"group": 1, "sColour": "W"},
    ],
    "explicitMods": [
        {
            "hash": "stat.explicit.stat_1940865751",
            "description": "Adds 47 to 84 Physical Damage",
            "mods": [{"tier": "P1"}],
        },
        {
            "hash": "stat.fractured.stat_3885405204",
            "description": "Bow Attacks fire 2 additional Arrows",
            "mods": [{"tier": "S1"}],
        },
    ],
    "implicitMods": ["24% increased Elemental Damage with Attack Skills"],
    "extended": {
        "dps": 322.5,
        "pdps": 262.5,
        "edps": 60.0,
        "text": "base64 text containing the listing price",
    },
}


def test_extracts_bow_properties_and_mods() -> None:
    raw = extract_raw_features(TRAINING_ITEM)

    assert raw.categorical["base_type"] == "Spine Bow"
    assert raw.numeric["item_level"] == 86
    assert raw.numeric["physical_dps"] == 262.5
    assert raw.numeric["elemental_damage_min"] == 40
    assert raw.numeric["elemental_damage_max"] == 70
    assert raw.numeric["socket_count"] == 3
    assert raw.numeric["max_link_count"] == 2
    assert raw.numeric["white_socket_count"] == 1
    assert raw.numeric["explicit_mod_count"] == 1
    assert raw.numeric["fractured_mod_count"] == 1
    assert raw.numeric["implicit_mod_count"] == 1
    assert len(raw.mods) == 3


def test_object_and_string_mod_formats_share_the_same_feature() -> None:
    schema = build_feature_schema([TRAINING_ITEM], min_mod_frequency=1)
    prediction_item = deepcopy(TRAINING_ITEM)
    prediction_item["explicitMods"] = ["Adds 47 to 84 Physical Damage"]
    prediction_item["fracturedMods"] = ["Bow Attacks fire 2 additional Arrows"]

    training_frame = transform_items([TRAINING_ITEM], schema)
    prediction_frame = transform_items([prediction_item], schema)
    physical_feature = next(
        feature
        for feature in schema.mod_features
        if feature.template == "adds # to # physical damage"
    )
    fractured_feature = next(
        feature
        for feature in schema.mod_features
        if feature.template == "bow attacks fire # additional arrows"
    )

    assert training_frame.loc[0, f"mod_{physical_feature.key}_present"] == 1
    assert prediction_frame.loc[0, f"mod_{physical_feature.key}_present"] == 1
    assert prediction_frame.loc[0, f"mod_{physical_feature.key}_value_0"] == 47
    assert prediction_frame.loc[0, f"mod_{fractured_feature.key}_present"] == 1


def test_price_note_and_extended_text_are_never_features() -> None:
    schema = build_feature_schema([TRAINING_ITEM], min_mod_frequency=1)
    changed = deepcopy(TRAINING_ITEM)
    changed["note"] = "~b/o 1 divine"
    changed["extended"]["text"] = "another encoded listing with another price"

    original_frame = transform_items([TRAINING_ITEM], schema)
    changed_frame = transform_items([changed], schema)

    assert original_frame.equals(changed_frame)
    assert all("price" not in feature for feature in schema.feature_names)


def test_schema_round_trip(tmp_path) -> None:
    schema = build_feature_schema([TRAINING_ITEM], min_mod_frequency=1)
    path = tmp_path / "schema.json"
    schema.save(path)

    loaded = type(schema).load(path)

    assert loaded == schema


def test_rejects_invalid_schema_limits() -> None:
    with pytest.raises(ValueError):
        build_feature_schema([TRAINING_ITEM], min_mod_frequency=0)

