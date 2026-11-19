"""Python side of the cross-language contract: models must match schemas/."""

import json

import jsonschema
import pytest

from applyops_agents.models import EchoRequest, EchoResult


def load(repo_root, rel):
    return json.loads((repo_root / "schemas" / rel).read_text())


def test_echo_request_matches_schema(repo_root):
    payload = EchoRequest(message="hi").model_dump(mode="json")
    jsonschema.validate(payload, load(repo_root, "echo/EchoRequest.v1.json"))


def test_echo_result_matches_schema(repo_root):
    payload = EchoResult(message="hi", worker="host-1", language="python").model_dump(mode="json")
    jsonschema.validate(payload, load(repo_root, "echo/EchoResult.v1.json"))


def test_payload_from_java_parses(repo_root):
    """What Jackson sends for EchoRequest must be accepted by the Pydantic model."""
    java_payload = {"message": "Hello, ApplyOps"}
    jsonschema.validate(java_payload, load(repo_root, "echo/EchoRequest.v1.json"))
    assert EchoRequest.model_validate(java_payload).message == "Hello, ApplyOps"


def test_schema_rejects_missing_fields(repo_root):
    with pytest.raises(jsonschema.ValidationError):
        jsonschema.validate({"message": "hi"}, load(repo_root, "echo/EchoResult.v1.json"))
