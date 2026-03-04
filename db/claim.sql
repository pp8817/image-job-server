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
    locked_until = NOW() + (:lease_seconds || ' seconds')::interval
FROM cte
WHERE j.id = cte.id
    RETURNING j.*;

-- =========================
-- B) Requeue stale running jobs (lease expired)
-- =========================
-- Params:
-- :now (timestamp) - optional, can use NOW()
-- :batch_size (int)
UPDATE job
SET
    status = 'QUEUED',
    locked_by = NULL,
    locked_until = NULL,
    attempt_count = attempt_count + 1
WHERE status = 'RUNNING'
  AND locked_until IS NOT NULL
  AND locked_until < NOW()
  AND attempt_count < 3
    RETURNING *;

-- =========================
-- C) Extend lease for a running job (heartbeat)
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