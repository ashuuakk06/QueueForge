# QueueForge

QueueForge is a runnable, full-stack distributed job execution engine. It includes a React dashboard, a Spring Boot REST API, a PostgreSQL job store, Redis queue coordination, MongoDB execution events, JWT authentication, and a configurable worker pool.

## Start the complete stack

Requirements: Docker Desktop with Docker Compose.

From this folder:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Open [http://localhost:5173](http://localhost:5173). The API is available at [http://localhost:8080](http://localhost:8080), with its health endpoint at [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

The development compose file seeds a demo account and example jobs:

| Field | Value |
| --- | --- |
| Email | `demo@queueforge.dev` |
| Password | `QueueForge!2026` |

Use the demo account only for local development. Set `APP_DEMO_SEED=false` and replace the database passwords and `JWT_SECRET` before exposing an instance to a network. The demo seeder does not overwrite an existing account.

The web app is served on port 5173 and proxies API requests through Nginx. PostgreSQL, Redis, and MongoDB use named Docker volumes, so jobs and execution events persist across restarts. MongoDB is published on host port 27018 to avoid conflicts with other local projects. To stop the services while keeping data, run `docker compose down`. To remove the local database volumes as well, run `docker compose down -v`.

The API/scheduler and worker pool run as separate containers. The default worker service provides four execution threads. Scale worker processes independently with `docker compose up --build -d --scale worker=3`; three containers provide twelve threads when `APP_WORKER_CONCURRENCY=4`.

## What you can do

- Register an account or sign in with a signed JWT.
- Submit jobs for immediate execution or schedule a future start.
- Choose a priority, maximum attempts, retry delay, and prerequisite jobs.
- Track queued, waiting, running, retrying, completed, failed, and cancelled states.
- Inspect input, output, attempts, and the MongoDB-backed execution timeline.
- Retry failed jobs and cancel jobs that have not started.
- Watch the dashboard, worker pool, Redis ready list, and processing list refresh live.
- Use four safe built-in handlers to demonstrate job execution without running arbitrary code.

## Architecture

```text
React dashboard
      │ REST + JWT
      ▼
Spring Boot API + scheduler ── PostgreSQL
      │                         authoritative jobs, dependencies
      ├──────────────────────── Redis ◀──────── Worker containers
      │                         ready + processing lists   claim → execute → acknowledge
      └──────────────────────── MongoDB
                                append-only execution events
```

PostgreSQL is the source of truth for job state. A scheduled dispatcher evaluates start times and dependency completion before publishing job IDs to Redis. Workers atomically move IDs from the ready list to the processing list, then claim the matching PostgreSQL row under a pessimistic lock. The state transition prevents duplicate queue messages from executing a job twice. Successful results and failures are written back to PostgreSQL. Failed attempts use a configurable fixed delay before the next attempt. A recovery pass resets jobs with expired worker leases.

Dependency edges point from a new job to already existing jobs. This keeps the graph acyclic through the public API. A dependent job waits until every prerequisite completes. If a prerequisite fails or is cancelled, the dependent job fails with a clear reason.

MongoDB stores a separate event document for each creation, queue, start, retry, completion, cancellation, or failure event. PostgreSQL remains authoritative if the event store is unavailable.

### Built-in job types

| Type | Example payload | Behavior |
| --- | --- | --- |
| `ECHO` | `{"message":"hello","source":"api"}` | Returns the payload as the result. |
| `TRANSFORM` | `{"text":"queueforge" }` | Returns the original text, uppercased text, and character count. |
| `DELAY` | `{"milliseconds":1500}` | Waits for up to 30 seconds and returns the elapsed duration. |
| `SIMULATE_FAILURE` | `{"failUntilAttempt":2}` | Fails the first two attempts, then succeeds; useful for seeing retry behavior. |

Payloads must be JSON objects. The API accepts `maxAttempts` from 1 to 10, `retryDelaySeconds` from 1 to 3600, and `priority` from 1 to 10. Higher priority jobs dispatch first among the due jobs. Optional `runAt` values are ISO-8601 instants. Optional `dependsOn` values are UUIDs of jobs owned by the same account.

Example:

```json
{
  "name": "Normalize customer data",
  "type": "TRANSFORM",
  "payload": { "text": "acme supply order 4821" },
  "maxAttempts": 4,
  "retryDelaySeconds": 10,
  "priority": 7,
  "dependsOn": []
}
```

## API

Authentication:

- `POST /api/auth/register` — `{"name":"Alex Morgan","email":"alex@example.com","password":"at-least-8-chars"}`
- `POST /api/auth/login` — returns a JWT and account summary.
- `GET /api/auth/me` — current account.

Jobs and dashboard (all require `Authorization: Bearer <token>`):

- `GET /api/dashboard` — counts, recent jobs, 12-hour activity, Redis depth, and configured workers.
- `GET /api/jobs?status=FAILED&search=import` — list the account's latest 500 jobs; both filters are optional.
- `POST /api/jobs` — create a job.
- `GET /api/jobs/{id}` — read one job.
- `GET /api/jobs/{id}/events` — read its latest 50 execution events.
- `POST /api/jobs/{id}/retry` — retry a failed or cancelled job.
- `POST /api/jobs/{id}/cancel` — cancel a job that has not started.

For a C++ REST client example, see [examples/cpp-client](examples/cpp-client/README.md).

## Configure the worker pool

Set `APP_WORKER_CONCURRENCY` in `.env` to control threads per worker process. The default is four worker threads. `JWT_SECRET` should be a long random string (at least 32 bytes); JWTs expire after 24 hours by default. `APP_CORS_ALLOWED_ORIGINS` contains a comma-separated list of trusted browser origins.

The Compose defaults are convenient for a local portfolio/demo environment. Before deployment, set unique database passwords, use a secret manager for `JWT_SECRET`, disable demo seeding, restrict published database ports, and configure TLS at the ingress or reverse proxy.

## Project layout

```text
backend/                  Spring Boot API, queue dispatcher, worker pool
  src/main/resources/
    db/migration/         PostgreSQL schema managed by Flyway
frontend/                 React + Vite responsive dashboard
examples/cpp-client/      C++ job submission client
docker-compose.yml        Complete local infrastructure
.env.example              Local environment template
```

## Stack

Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA · PostgreSQL · Redis · MongoDB · React · JavaScript · SQL · C++ · Docker Compose · Maven
