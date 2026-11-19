-- M1: job discovery (docs/DESIGN.md §5).

CREATE TABLE job (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- sha256 of normalized company|title: cross-source dedup when no ATS id is known.
    fingerprint   TEXT        NOT NULL UNIQUE,
    -- e.g. greenhouse:acme:4012345 once the posting is resolved to its ATS.
    ats_key       TEXT UNIQUE,
    ats_type      TEXT        NOT NULL DEFAULT 'unknown',
    ats_url       TEXT,
    company       TEXT        NOT NULL,
    title         TEXT        NOT NULL,
    location      TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every sighting of a job in an alert email. One job can have many sources.
CREATE TABLE job_source (
    id               BIGSERIAL PRIMARY KEY,
    job_id           UUID        NOT NULL REFERENCES job (id),
    provider         TEXT        NOT NULL,   -- linkedin | jobright
    external_id      TEXT        NOT NULL,   -- provider's job id
    source_url       TEXT,
    email_message_id TEXT,
    seen_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, external_id)
);

CREATE INDEX job_source_job_idx ON job_source (job_id);

-- One application per job. Created in the same transaction as the job, so
-- "job exists but its workflow was never started" is always recoverable
-- (workflow_started_at IS NULL acts as an outbox).
CREATE TABLE application (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID        NOT NULL UNIQUE REFERENCES job (id),
    workflow_id         TEXT        NOT NULL UNIQUE,
    state               TEXT        NOT NULL,
    state_changed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    workflow_started_at TIMESTAMPTZ
);

CREATE INDEX application_unstarted_idx ON application (created_at) WHERE workflow_started_at IS NULL;

-- Gmail incremental sync position.
CREATE TABLE gmail_checkpoint (
    account    TEXT PRIMARY KEY,
    history_id TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
