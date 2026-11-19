-- M0 baseline. Domain tables (job, application, ...) arrive with their milestones (docs/DESIGN.md §5).

-- Append-only audit trail of everything that happens to an application.
CREATE TABLE event_log (
    id             BIGSERIAL PRIMARY KEY,
    application_id UUID,
    at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    type           TEXT        NOT NULL,
    data           JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX event_log_application_idx ON event_log (application_id, at);
