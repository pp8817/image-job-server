-- db/schema.sql

-- =========================
-- 1) job table
-- =========================
CREATE TABLE IF NOT EXISTS job (
                                   id                 UUID PRIMARY KEY,
                                   status             VARCHAR(32) NOT NULL, -- RECEIVED|QUEUED|RUNNING|SUCCEEDED|FAILED

    image_url           TEXT NOT NULL,

    idempotency_key     VARCHAR(128) NULL,
    fingerprint         VARCHAR(64)  NULL,

    external_job_id     VARCHAR(128) NULL, -- Mock Worker jobId

    attempt_count       INT NOT NULL DEFAULT 0,

    locked_by           VARCHAR(128) NULL,
    locked_until        TIMESTAMPTZ NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Idempotency key uniqueness (nullable)
CREATE UNIQUE INDEX IF NOT EXISTS ux_job_idempotency_key
    ON job (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Fingerprint uniqueness (nullable)
CREATE UNIQUE INDEX IF NOT EXISTS ux_job_fingerprint
    ON job (fingerprint)
    WHERE fingerprint IS NOT NULL;

-- Index for worker polling
CREATE INDEX IF NOT EXISTS ix_job_status_created_at
    ON job (status, created_at);

-- Index for stale running jobs
CREATE INDEX IF NOT EXISTS ix_job_status_locked_until
    ON job (status, locked_until);

-- =========================
-- 2) job_result table
-- =========================
CREATE TABLE IF NOT EXISTS job_result (
                                          job_id             UUID PRIMARY KEY REFERENCES job(id) ON DELETE CASCADE,
    result_payload     TEXT NULL,

    error_code         VARCHAR(64) NULL,
    error_message      TEXT NULL,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Ensure result is either success or failure (optional)
-- success: result_payload NOT NULL AND error_code IS NULL
-- failure: result_payload IS NULL AND error_code IS NOT NULL
-- (Enforced at application level for portability)

-- =========================
-- 3) updated_at trigger (optional)
-- =========================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_job_updated_at ON job;

CREATE TRIGGER trg_job_updated_at
    BEFORE UPDATE ON job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();