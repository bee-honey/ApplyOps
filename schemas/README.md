# Shared schemas

Payloads that cross the Java ↔ Python boundary through Temporal are defined **once** here as
JSON Schema (draft 2020-12). File naming: `<area>/<Type>.v<N>.json`.

- **Java** records (`orchestrator/`) are checked against these schemas in contract tests
  (`*SchemaContractTest`).
- **Python** Pydantic models (`agents/`, `browser-worker/`) are checked against them in
  `tests/test_schema_contract.py`.

Rules:
- Adding an optional field is allowed in the same version. Removing or renaming a field, or making it
  required, needs a new version file (`.v2.json`) and both sides updated.
- Large binary data (PDFs, screenshots) never goes in a payload; pass a blob-store path instead.
