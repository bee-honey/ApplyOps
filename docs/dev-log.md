# Engineering log

What was built, with which agent, and where the agent got things wrong. Feeds the "shipping with coding agents" story (see [ROADMAP](ROADMAP.md#agentic-development-process)).

| Date | Milestone | Agent | Human role |
|---|---|---|---|
| 2026-11-18 | Planning docs | Claude Code (Opus 5.5) | Decisions: Temporal, Java+Python, doc scope; reviewed and approved |
| 2026-11-18 | M0 foundations | Claude Code (Opus 5.5) | Approved plan and commit |
| 2026-11-18 | M1 discovery (code complete) | Claude Code (Opus 5.5) | Google Cloud OAuth setup; review |
| 2026-11-19 | M1 real-mailbox validation, parser fixes, real fixtures | Claude Code (Opus 5.5) | Gmail consent; review |

Times weren't tracked precisely for these first sessions. From M2 on, record wall-clock time per task.

## Agent mistakes and how they were caught

| Milestone | Mistake | Caught by | Fix / lesson |
|---|---|---|---|
| M0 | Line over the ruff length limit in a test | CI-equivalent lint (`make test-python`) | Run lint locally before reporting done |
| M1 | First link resolver used `HttpClient` auto-redirects, so a tracking link redirecting **to LinkedIn** would still have sent a request there (violates ADR-0003) | Agent self-review before tests | Follow redirects manually and check each hop; added a test proving linkedin.com is never requested |
| M1 | Parser beans had two constructors, so Spring couldn't create them. Unit tests passed; the app crashed on startup in Docker | `make up` + `make smoke` (end-to-end) | Added `OrchestratorApplicationTest`: boots the full context with Testcontainers Postgres + in-process Temporal |
| M1 | Test helper imported across packages from a package-private class | Compiler | Shared `testing/Fixtures` helper |
| M1 | Used the deprecated `GoogleNetHttpTransport` | `-Xlint:deprecation` | Use `NetHttpTransport` |
| M1 | Parsers built from **synthetic** emails found 0 jobs in 100 real ones: real cards wrap everything in an outer link, so "longest link text = title" picked the whole card; Jobright puts the company *before* the title and an optional salary before the location | First run against the real mailbox | Title = innermost job link; per-provider layout hook; fixtures are now real, scrubbed emails |
| M1 | Scrubber's automated checks passed but the LinkedIn footer still held the user's profile headline (employer) | Human-style review of the fixture's visible text | Scrubber always rewrites the "intended for …" footer; read visible text before committing any fixture |
| M1 | `.env` at repo root was never read: Compose looks for `.env` next to the compose file | Checking the schedule actually turned on | Makefile passes `--env-file .env` when present |

**Pattern so far:** unit tests alone missed a wiring bug that only the end-to-end run caught, and synthetic fixtures gave false confidence until real data arrived. Keep `make smoke` as a required gate, and build parser/adapter tests from real (scrubbed) inputs as early as possible: the same will apply to ATS form fixtures in M5.
