package dev.applyops.orchestrator;

/** Temporal task queue names shared with the Python workers (see docs/DESIGN.md §3.3). */
public final class TaskQueues {
    /** Java: workflows, Gmail, normalization, DB writes, Telegram send. */
    public static final String ORCHESTRATOR = "orchestrator-tq";
    /** Python: LLM agents (JD analysis, tailoring). */
    public static final String AGENTS = "agents-tq";
    /** Python: Playwright browser worker. */
    public static final String BROWSER = "browser-tq";

    private TaskQueues() {}
}
