# ADR-0002: Java orchestrator, Python agent and browser workers

- Status: Accepted
- Date: 2026-11-18

## Context
The author's core strength is Java backend engineering, which we want to show. The AI and browser parts have the strongest ecosystem in Python (LLM SDKs, Pydantic structured outputs, python-docx, Playwright for Python, eval tooling). Temporal lets workers in different languages poll different task queues within the same workflow.

## Decision
- **Java 21 + Spring Boot** for workflows, Gmail ingestion, normalization/dedup, persistence, Tracker API and Telegram Gateway (`orchestrator-tq`).
- **Python 3.12** for the agents worker (`agents-tq`) and browser worker (`browser-tq`).
- Data shared across languages is defined once as versioned **JSON Schema** in `schemas/`; Java records and Pydantic models are validated against it. Large files go through a blob store; only references go through Temporal.

## Consequences
- ✅ Each language does what it's best at; shows polyglot distributed design.
- ✅ Workflow logic (the deterministic, critical part) lives in strongly typed Java.
- ⚠️ Two build systems, two CI jobs, two sets of dependencies.
- ⚠️ Schema drift risk across languages → contract tests in CI.
- ⚠️ Local dev needs both toolchains (mitigated with Docker Compose + Makefile).

## Alternatives considered
- **All Python:** fastest MVP, one toolchain; loses the Java story.
- **All Java:** Playwright Java exists, but LLM/eval/document tooling is weaker.
- **All TypeScript:** one language across the stack, but not the author's strength.
