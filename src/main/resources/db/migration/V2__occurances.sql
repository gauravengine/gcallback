CREATE TABLE OCCURRENCES (
    id bigserial PRIMARY KEY ,
    job_id bigint NOT NULL REFERENCES jobs(id),
    scheduled_for timestamptz NOT NULL,

    state text NOT NULL, -- PENDING, DISPATCHED, IN_PROGRESS, SUCCEEDED, RETRY_SCHEDULED, CANCELLED, FAILED
    attempt_count int NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,

    claimed_by text,
    claimed_at timestamptz,

    updated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_occurrence UNIQUE (job_id, scheduled_for)
);

CREATE INDEX idx_occ_pending ON OCCURRENCES(created_at) WHERE state  = 'PENDING';
CREATE INDEX idx_occ_retry_due ON OCCURRENCES(next_attempt_at) WHERE state = 'RETRY_SCHEDULED';

CREATE INDEX idx_occ_inflight ON OCCURRENCES(updated_at) WHERE state IN ('DISPATCHED', 'IN_PROGRESS');
CREATE INDEX idx_occ_by_job ON OCCURRENCES(job_id, scheduled_for DESC);