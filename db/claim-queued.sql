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
