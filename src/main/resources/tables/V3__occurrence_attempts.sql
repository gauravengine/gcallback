CREATE TABLE occurrence_attempts (
    id bigserial,
    occurrence_id bigint NOT NULL ,
    attempt_number int not null ,

    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    outcome text NOT NULL ,
    status_code int,
    latency_ms int,
    response_body text,
    error_message text,
    worker_id text,

    PRIMARY KEY (id, started_at),
    UNIQUE (occurrence_id, attempt_number , started_at)
);

  CREATE INDEX idx_attempts_by_occurrence ON occurrence_attempts( occurrence_id, attempt_number);

