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
