# API Contract (Our Server)

Base path: `/`

## 1) Create Job
POST `/jobs`

Headers:
- `Idempotency-Key` (optional, preferred)

Request JSON:
```json
{
  "imageUrl": "https://..."
}
```

Response (201):
```json
{
  "jobId": "uuid",
  "status": "RECEIVED",
  "deduped": false
}
```

If deduped (duplicate request):
•	still 201 (or 200) but deduped: true and returns existing jobId.

## 2) Get Job Status

GET /jobs/{jobId}

Response (200):
```json
{
  "jobId": "uuid",
  "status": "QUEUED|RUNNING|SUCCEEDED|FAILED",
  "progress": 0,
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "attemptCount": 0
}
```
Progress mapping:
•	RECEIVED=0, QUEUED=10, RUNNING=50, SUCCEEDED=100, FAILED=100

## 3) Get Job Result

GET /jobs/{jobId}/result

If not finished:
•	202 Accepted (or 409 Conflict) with body:
```json
{
  "status": "RUNNING"
}
```
If succeeded (200):
```json
{
  "result": "string"
}
```
If failed (200 or 422):
```json
{
  "errorCode": "MOCK_WORKER_FAILED|TIMEOUT|RATE_LIMITED|UNAUTHORIZED|BAD_REQUEST|INTERNAL",
  "message": "string"
}
```

## 4) List Jobs

GET /jobs?page=0&size=20&status=RUNNING

Response (200):
```json
{
  "page": 0,
  "size": 20,
  "total": 100,
  "items": [
    {
      "jobId": "uuid",
      "status": "RUNNING",
      "progress": 50,
      "createdAt": "ISO-8601",
      "updatedAt": "ISO-8601"
    }
  ]
}
```
