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
    attempt_count = j.attempt_count + 1
FROM stale
WHERE j.id = stale.id
RETURNING j.*;
