"""profile.example.yaml must always satisfy profile.schema.json (the tailor relies on it)."""

import json

import jsonschema
import pytest
import yaml


@pytest.fixture
def schema(repo_root):
    return json.loads((repo_root / "profile" / "profile.schema.json").read_text())


@pytest.fixture
def example(repo_root):
    return yaml.safe_load((repo_root / "profile" / "profile.example.yaml").read_text())


def test_example_profile_is_valid(schema, example):
    jsonschema.validate(example, schema)


def test_bullet_ids_are_unique(example):
    ids = [b["id"] for exp in example["experience"] for b in exp["bullets"]]
    ids += [e["id"] for e in example["experience"]]
    assert len(ids) == len(set(ids))


def test_bullet_without_id_is_rejected(schema, example):
    example["experience"][0]["bullets"][0].pop("id")
    with pytest.raises(jsonschema.ValidationError):
        jsonschema.validate(example, schema)
