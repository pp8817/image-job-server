# AGENTS.md

> **Never assume behavior not defined in repository documents.**

## Repository Context Loading (MANDATORY)

Before implementing or modifying any code, you MUST read all documentation files in this repository to understand the complete project context.

Read the following files first, in this order:

1. `AGENTS.md`
2. `PLAYBOOK.md`
3. `PROGRESS.md`
4. `docs/architecture.md`
5. `docs/api.md`
6. `docs/failure-and-retry.md`
7. `db/schema.sql`
8. `db/claim.sql`

Rules:

- Do not start implementation until all documents above have been read.
- These files are the **source of truth** for system behavior.
- If implementation conflicts with these documents, **the documents take precedence**.
- If the session restarts or thread changes, reload all documents before continuing.

---

# 0. Purpose

This repository implements an **asynchronous image processing job server** (Kotlin + Spring Boot) that orchestrates work to an external **Mock Worker**.

Codex must implement the system strictly according to:
- `docs/architecture.md` (source of truth for design)
- `docs/api.md` (REST contract for our server)
- `docs/failure-and-retry.md` (failure mapping + retry policy)
- `db/schema.sql` (DDL + indexes)
- `db/claim.sql` (worker claim SQL using SKIP LOCKED)

---

# 1. Hard Requirements (do not deviate)

1) Language/Framework:
- Kotlin + Spring Boot
- Gradle build

2) External integration:

Mock Worker Base URL:

```
https://dev.realteeth.ai/mock
```

Endpoints:

- `POST /mock/auth/issue-key`
- `POST /mock/process`
- `GET /mock/process/{job_id}`

Uses header `X-API-KEY` for authenticated requests.

3) Asynchronous processing:

- API request must return quickly after persisting an internal Job.
- Actual processing must be performed by a background worker (scheduled polling + threadpool).

4) Persistence:

- Job state must be persisted in DB (no in-memory job state).
- Idempotency and dedup must be enforced by DB constraints.

5) Duplicate request handling:

- Require `Idempotency-Key` header on `POST /jobs`.
- On duplicates, return existing `jobId` without creating a new job.

6) State model:

Internal states:

```
RECEIVED
QUEUED
RUNNING
SUCCEEDED
FAILED
```

Allowed transitions:

```
RECEIVED -> QUEUED
QUEUED -> RUNNING
RUNNING -> SUCCEEDED
RUNNING -> FAILED
RUNNING -> QUEUED (only for stale lease recovery)
```

Final states (immutable):

```
SUCCEEDED
FAILED
```

External state mapping (Mock Worker):

```
PROCESSING -> RUNNING
COMPLETED  -> SUCCEEDED
FAILED     -> FAILED
```

7) Worker claim and concurrency:

- Worker must claim jobs using SQL in `db/claim.sql`.
- Must use `FOR UPDATE SKIP LOCKED` to avoid contention.
- Use lease fields `locked_by`, `locked_until` and extend lease while running.
- Stale jobs: RUNNING with expired lease must be re-queued with attempt_count increment.

8) Retry policy:

- Follow `docs/failure-and-retry.md`.
- Retry only on: network errors, timeouts, 5xx, 429.
- Do NOT retry on: 400, 401, validation errors.

9) Container execution:

- Must run via docker compose without external credentials.
- Include Postgres in compose.

10) Tests required:

Unit tests:
- state transition validation
- idempotency key logic

Integration tests:
- worker processing with mocked Mock Worker (WireMock/MockWebServer)

Concurrency tests:
- duplicate request race test (multi-thread)
- ensure single job created

---

# 2. Project Structure (mandatory)

Implement packages under:

```
src/main/kotlin/ai/realteeth/imagejobserver/
```

Modules (packages):

```
job
worker
client.mockworker
global
```

Details:

- `job` → controller, service, domain, repository
- `worker` → scheduler + execution + lease handling
- `client.mockworker` → HTTP client for Mock Worker
- `global` → configuration, exception handling, utilities

Test packages must mirror main packages.

---

# 3. Output Expectations

- All endpoints in `docs/api.md` implemented.
- All DB objects in `db/schema.sql` created and used.
- Worker uses `db/claim.sql`.
- README explains:
    - state model
    - retry strategy
    - idempotency strategy
    - restart behavior
    - bottlenecks
    - run instructions.

---

# 4. Non-goals (avoid scope creep)

- No UI
- No cancel endpoint unless explicitly added later
- No additional infrastructure beyond Postgres (no Redis required)

---

# 5. Git Commit Convention

- Commit message format: `{type}: {message}`
- Do not prepend branch name to commit message.
- Use Korean for commit message content.
- Keep message concise and focused on actual change.
- Recommended types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.

Example:
- `docs: API 계약 문서 정합성 수정`

---

# Progress Tracking

The current implementation state is tracked in:

```
PROGRESS.md
```

Rules:

1. Before implementing a phase, read `PROGRESS.md`.
2. After completing a task, update `PROGRESS.md`.
3. Do not skip phases defined in `PLAYBOOK.md`.
4. If the thread restarts, resume from the first unchecked item in `PROGRESS.md`.
