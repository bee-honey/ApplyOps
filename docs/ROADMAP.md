# ApplyOps — Roadmap

Each milestone delivers something demoable that you can use yourself. Milestones are done one at a time; tasks get broken down further when a milestone starts.

| # | Milestone | Demo at the end |
|---|---|---|
| M0 | Foundations | `docker compose up` brings up Temporal + Postgres; hello-world Java workflow calls a Python step |
| M1 | Job discovery | New LinkedIn/Jobright alert emails show up as deduplicated jobs in the DB |
| M2 | Job analysis | Each job has a structured JobSpec + match score |
| M3 | Grounded tailoring | Tailored résumé PDF per job, 0 unsupported claims on the eval set |
| M4 | Human in the loop | Approve/edit/skip documents from Telegram; status visible in Temporal UI |
| M5 | Browser apply | End-to-end Greenhouse application with Telegram Q&A and submit approval |
| M6 | Hardening and polish | Chaos tests pass, reconciliation works, dashboard, 5 outside users |

---

## M0 — Foundations
**Tasks**
- Monorepo layout (`orchestrator/`, `telegram-gateway/`, `agents/`, `browser-worker/`, `schemas/`, `profile/`, `infra/`, `evals/`)
- `infra/docker-compose.yml`: Temporal (auto-setup) + Temporal UI + Postgres
- Java: Gradle multi-module, Spring Boot 3, Temporal Java SDK, Flyway baseline migration
- Python: `pyproject.toml` project (venv + pip; uv-compatible), Temporal Python SDK, Pydantic, ruff + pytest
- `schemas/` JSON Schemas + codegen/validation approach for both languages
- `profile/profile.schema.json` + `profile.example.yaml`; real `profile.yaml` gitignored
- Polyglot smoke test: `HelloWorkflow` (Java) → `echo` step on `agents-tq` (Python)
- GitHub Actions: build + unit tests for both languages
- `.env.example`, `.gitignore` (data/, profile.yaml, .env)

**Acceptance**
- `make up && make smoke` passes locally and in CI
- Temporal UI shows the completed HelloWorkflow

## M1 — Job discovery
**Tasks**
- Gmail OAuth setup script (read-only scope), token stored locally
- `fetchNewAlertEmails` with `historyId` checkpointing + full-sync fallback
- `LinkedInAlertParser`, `JobrightAlertParser` with fixture-based tests (saved real emails, PII removed)
- Link resolver: follow redirects to the final ATS URL; detect `ats_type`; compute `canonical_key`
- `job`, `job_source` tables + upsert
- `JobDiscoveryWorkflow` on a Temporal Schedule; starts `ApplicationWorkflow` stub for new jobs

**Acceptance**
- 100 real jobs discovered; running the poll twice adds 0 new rows
- The same job from both providers ends up as 1 `job` row with 2 `job_source` rows

## M2 — Job analysis
**Tasks**
- `analyzeJob` step: fetch the JD (ATS public pages / Greenhouse & Lever public job-board APIs where available) → `JobSpec`
- Match scoring: hard filters (location, sponsorship, excluded companies, salary floor) + LLM relevance score with reasons
- Treat JD as untrusted input in prompts (injection test cases)
- `SKIPPED_LOW_MATCH` path in the workflow

**Acceptance**
- 20 hand-labeled jobs: score ranking agrees with your labels on ≥ 80%
- Prompt-injection fixtures don't change the output schema or behavior

## M3 — Grounded résumé tailoring
**Tasks**
- Write the real `profile.yaml` (from your current résumé)
- Select → rephrase → validate → render pipeline (DESIGN §7)
- DOCX template from your existing résumé; PDF via LibreOffice headless
- Change-summary generator
- `evals/tailoring/`: 10 JDs, assertions for provenance, keyword coverage, length
- `document_version` table with provenance JSON

**Acceptance**
- Eval suite: 0 unsupported claims across all cases; runs in CI (mocked LLM) and on demand (real LLM)
- You'd send the generated PDF as-is for ≥ 8/10 eval JDs

## M4 — Human in the loop (Telegram)
**Tasks**
- Telegram bot (long polling) in `telegram-gateway`; chat allowlist; HMAC callback data
- `human_request` table; send doc-approval message with PDF + summary
- Signals: `docDecision` (approve / edit with text / skip); edit loop re-tailors
- Expiry timers + reminder; `/status`, `/pending`, `/resume`, `/skip`
- Search attributes for the Temporal UI (`AppState`, `Company`, `MatchScore`)

**Acceptance**
- From your phone: approve, edit and skip each work end-to-end
- Double-tapping Approve sends exactly one signal (tested)
- Killing all workers while waiting for approval, then restarting → the approval still works

## M5 — Browser apply
**Tasks**
- Browser worker with per-application `BrowserContext` + persisted `storageState`
- `GreenhouseAdapter` (field extraction, fill, upload, review screenshot); HTML fixture tests
- Field resolution: profile → standard_answers → answer_memory → ask user
- Question signals + `answer_memory` "save as default"
- CAPTCHA/login detection → `NEEDS_MANUAL`
- Submit approval with screenshot; submit step with `maximumAttempts=1` + idempotency key
- `LeverAdapter`, `AshbyAdapter`
- LLM fallback extractor with bounded tools (no submit)

**Acceptance**
- 3 real applications submitted (one per ATS) with you approving each
- An unknown question goes to Telegram; your answer is used and optionally remembered
- A test form with a fake CAPTCHA → `NEEDS_MANUAL`, no bypass attempt

## M6 — Hardening and polish
**Tasks**
- Chaos tests: kill the browser worker during fill, and during submit; Temporal server restart
- `checkSubmissionStatus` reconciliation (confirmation page + confirmation email search)
- Tracker REST API + minimal dashboard (Kanban by state)
- Daily Telegram digest
- OpenTelemetry metrics (optional)
- Onboarding doc for outside users; collect feedback from 5 people

**Acceptance**
- 0 duplicate submissions across 50 crash-injection runs against a local mock ATS
- All MVP success criteria in the README checked

---

## Post-MVP ideas
- Recruiter reply / interview / rejection tracking from Gmail
- Workday adapter (multi-page, account creation; likely `NEEDS_MANUAL` heavy)
- Embedding-based answer memory and match scoring
- Multi-user (Keycloak), hosted deployment
- Pull out generic "human-in-the-loop agent workflow" building blocks into a library

## Agentic development process
Building ApplyOps is also an experiment in **shipping fast with coding agents** (Claude Code, Codex):
- **Spec first:** each task starts with a short spec + acceptance criteria in its issue/PR description.
- **Isolated worktrees:** one git worktree per agent task; agents never share a working tree.
- **Tests as the contract:** fixtures and acceptance tests written (or reviewed) by the human before agent implementation where practical.
- **Independent review:** a second agent or `/code-review` pass on every PR, then a human review.
- **Engineering log** (`docs/dev-log.md`): per task, record time taken, agent used, mistakes the agent made, and how they were caught. This feeds the "how I use AI to ship" interview story.
