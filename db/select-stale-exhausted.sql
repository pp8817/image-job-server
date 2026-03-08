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
