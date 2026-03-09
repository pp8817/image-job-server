-- db/claim.sql

-- =========================
-- A) Claim queued jobs
-- =========================
-- Params:
-- :worker_id (string)
-- :lease_seconds (int)
-- :batch_size (int)
WITH cte AS (
    SELECT id
    FROM job
    WHERE status = 'QUEUED'
    ORDER BY created_at ASC
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
            )
UPDATE job j
SET
    status = 'RUNNING',
    locked_by = :worker_id,
    locked_until = NOW() + (:lease_seconds || ' seconds')::interval,
    next_poll_at = NULL,
    processing_started_at = COALESCE(j.processing_started_at, NOW())
FROM cte
WHERE j.id = cte.id
    RETURNING j.*;

-- =========================
-- B) Requeue stale running jobs (lease expired)
-- =========================
-- Params:
-- :batch_size (int)
-- :max_attempts (int)
WITH stale AS (
    SELECT id
    FROM job
    WHERE status = 'RUNNING'
      AND locked_until IS NOT NULL
      AND locked_until < NOW()
      AND attempt_count < :max_attempts
    ORDER BY locked_until ASC
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE job j
SET
    status = 'QUEUED',
    locked_by = NULL,
    locked_until = NULL,
    next_poll_at = NULL,
    attempt_count = j.attempt_count + 1
FROM stale
WHERE j.id = stale.id
    RETURNING j.*;

-- =========================
-- C) Claim RUNNING jobs ready for next poll
-- =========================
-- Params:
-- :worker_id (string)
-- :lease_seconds (int)
-- :batch_size (int)
WITH due AS (
    SELECT id
    FROM job
    WHERE status = 'RUNNING'
      AND locked_by IS NULL
      AND locked_until IS NULL
      AND next_poll_at IS NOT NULL
      AND next_poll_at <= NOW()
    ORDER BY next_poll_at ASC
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
UPDATE job j
SET
    locked_by = :worker_id,
    locked_until = NOW() + (:lease_seconds || ' seconds')::interval,
    next_poll_at = NULL
FROM due
WHERE j.id = due.id
    RETURNING j.*;

-- =========================
-- D) Extend lease for a running job (heartbeat)
-- =========================
-- Params:
-- :job_id (uuid)
-- :worker_id (string)
-- :lease_seconds (int)
UPDATE job
SET
    locked_until = NOW() + (:lease_seconds || ' seconds')::interval
WHERE id = :job_id
  AND status = 'RUNNING'
  AND locked_by = :worker_id
    RETURNING *;

-- =========================
-- E) Select stale running jobs that exceeded max attempts
-- =========================
-- Params:
-- :batch_size (int)
-- :max_attempts (int)
WITH exhausted AS (
    SELECT id
    FROM job
    WHERE status = 'RUNNING'
      AND locked_until IS NOT NULL
      AND locked_until < NOW()
      AND attempt_count >= :max_attempts
    ORDER BY locked_until ASC
    LIMIT :batch_size
    FOR UPDATE SKIP LOCKED
)
SELECT j.*
FROM job j
JOIN exhausted e ON j.id = e.id;
