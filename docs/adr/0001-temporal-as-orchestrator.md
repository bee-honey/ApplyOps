# ADR-0001: Temporal as the workflow orchestrator

- Status: Accepted
- Date: 2026-11-18

## Context
Each job application is a long-running process. It may wait hours or days for the user (document approval, answers, submit approval), it calls unreliable external systems (LLMs, ATS websites, Telegram), and it must survive crashes without losing progress or repeating side effects. We need a durable execution engine plus a UI showing what's happening with every application. Candidates: Temporal, Kestra (author has professional experience), Airflow/Prefect, or a hand-rolled DB state machine.

## Decision
Use **Temporal** (self-hosted through Docker Compose) with one `ApplicationWorkflow` per canonical job and a scheduled `JobDiscoveryWorkflow`.

## Consequences
- ✅ Signals and durable timers fit human-in-the-loop waits well; waiting workflows use no worker resources.
- ✅ Event-sourced history + replay gives crash recovery and a full audit trail.
- ✅ Workers in different languages share task queues (enables ADR-0002).
- ✅ Workflow IDs give natural dedup (`app-<canonicalKey>`, REJECT_DUPLICATE).
- ✅ New technology for the author's portfolio; strong interview depth (determinism, replay, versioning).
- ⚠️ Workflow code must be deterministic; workflow versioning needs care when changing in-flight workflows.
- ⚠️ The Temporal UI is execution-focused, not business-focused → a small tracker dashboard is still needed (M6).
- ⚠️ More moving parts than a DB state machine for a single-user app.

## Alternatives considered
- **Kestra:** declarative YAML, good visual UI, pause/resume, faster for the author. Rejected: less natural for per-entity long-lived workflows with many signal round-trips, and adds less to the portfolio.
- **Airflow/Prefect:** batch/DAG-oriented; poor fit for per-application human waits.
- **Hand-rolled Postgres state machine + job queue:** simplest infrastructure, but we'd rebuild timers, retries, and history ourselves.
