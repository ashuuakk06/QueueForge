CREATE TABLE job_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_name VARCHAR(160) NOT NULL,
    event VARCHAR(60) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_events_job_user_time
    ON job_events(job_id, user_id, occurred_at DESC);
CREATE INDEX idx_job_events_user_time
    ON job_events(user_id, occurred_at DESC);

CREATE INDEX idx_jobs_dispatched_queue
    ON jobs(dispatched_at)
    WHERE status = 'QUEUED' AND dispatched_at IS NOT NULL;
