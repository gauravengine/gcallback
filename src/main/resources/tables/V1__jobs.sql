CREATE TABLE jobs (
    id bigserial PRIMARY KEY,
    name text,
    schedule_type text NOT NULL , -- ONE_OFF | CRON
    cron_expression text, -- 6 field spring; NULL for one off
    timezone text NOT NULL , -- IANA Zone like "Asia/Kolkara"
    run_at timestamptz, -- ONE_OFF ONLY

    http_method text NOT NULL,
    http_url text NOT NULL ,
    http_headers jsonb NOT NULL DEFAULT '{}',
    http_query_params jsonb NOT NULL DEFAULT '{}',
    http_body text,

    max_attempts int NOT NULL DEFAULT 3,
    timeout_ms int NOT NULL DEFAULT 5000,

    status text NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | PAUSED | DELETED
    next_fire_time timestamptz, -- NULL once terminal
    last_fire_time timestamptz,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()

);

CREATE INDEX idx_jobs_due ON jobs (next_fire_time)
    WHERE status = 'ACTIVE' AND next_fire_time IS NOT NULL;