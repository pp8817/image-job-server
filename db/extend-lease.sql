UPDATE job
SET
    locked_until = NOW() + (:lease_seconds || ' seconds')::interval
WHERE id = :job_id
  AND status = 'RUNNING'
  AND locked_by = :worker_id
RETURNING *;
