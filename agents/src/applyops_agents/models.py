"""Pydantic models mirroring schemas/ (checked by tests/test_schema_contract.py)."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class EchoRequest(BaseModel):
    """schemas/echo/EchoRequest.v1.json"""

    model_config = ConfigDict(extra="forbid")

    message: str = Field(min_length=1)


class EchoResult(BaseModel):
    """schemas/echo/EchoResult.v1.json"""

    model_config = ConfigDict(extra="forbid")

    message: str
    worker: str
    language: Literal["python", "java"]
