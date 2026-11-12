# QueueForge

QueueForge is a backend job processing system built using Java and Spring Boot. It allows users to submit jobs, schedule their execution, manage dependencies, and track their status.

Jobs are stored in PostgreSQL, while Redis is used to manage job delivery between the API and worker processes. Separate Java workers pick up jobs and execute them in the background.

The project also includes a React dashboard where users can submit jobs and monitor their progress, retries, and execution results.

## Dashboard

![QueueForge Dashboard](preview.png)

## Architecture

QueueForge has three main parts:

- **Frontend:** Provides the dashboard for submitting and monitoring jobs.
- **Backend API:** Handles authentication, job creation, scheduling, and job management.
- **Worker:** Picks up available jobs and executes them in the background.

### Application flow

```mermaid
flowchart LR
    Browser[React Dashboard] -->|REST API + JWT| API[Spring Boot API]
    API -->|Store jobs and events| DB[(PostgreSQL)]
    API -->|Dispatch job IDs| Redis[(Redis)]
    Redis --> Worker[Java Worker]
    Worker -->|Update job status| DB
    Worker -->|Acknowledge jobs| Redis
```

PostgreSQL stores the main job information and execution history. Redis helps coordinate job delivery between the API and workers.

## How QueueForge Works

The basic job processing flow is:

1. A user logs in and receives a JWT token.
2. The user submits a job through the dashboard.
3. The API validates the request and saves the job in PostgreSQL.
4. The scheduler checks which jobs are ready to execute.
5. Ready job IDs are added to Redis.
6. A worker picks up a job and processes it.
7. After execution, the worker updates the job status and saves the result.
8. If a job fails, it can be retried according to its configured retry settings.

QueueForge also supports job dependencies. A job can wait for another job to finish before it becomes eligible for execution.

### Job retries and recovery

Jobs can be configured with a maximum number of attempts and a retry delay.

If a worker stops unexpectedly while processing a job, the recovery mechanism checks for expired worker leases and allows eligible jobs to be processed again.

Redis may deliver the same job more than once. PostgreSQL row locking and job status checks help prevent multiple workers from claiming the same active job.

QueueForge uses **at-least-once delivery**, not exactly-once execution. If a worker performs an external operation but stops before saving its result, that operation could happen again during recovery. The included demo handlers do not perform external side effects.

The API and Redis do not share a distributed transaction. Periodic recovery helps dispatch jobs again if a failure happens between saving a job and publishing it to Redis.

## Technologies Used

- **Languages:** Java, C++, JavaScript, SQL
- **Backend:** Spring Boot, Spring Security, Spring Data JPA, Hibernate, REST APIs, JWT Authentication
- **Frontend:** React, HTML5, CSS3
- **Database and Caching:** PostgreSQL, Redis
- **Tools:** Git, GitHub, Maven, Docker, Docker Compose

## Main Features

- User registration and login with JWT authentication.
- Submit and schedule background jobs.
- Track job status and execution history.
- Redis-based job delivery and coordination.
- Multiple worker processes for job execution.
- Job priority and dependency management.
- Configurable retry attempts and retry delays.
- Recovery of jobs after worker interruptions.
- Dashboard showing job statistics and worker activity.
- Support for different built-in job types.
- C++ REST API example for submitting jobs.

## Built-in Job Types

QueueForge currently includes four built-in job types.

| Job Type | Description |
|---|---|
| ECHO | Returns the submitted payload. |
| TRANSFORM | Converts text to uppercase and returns its length and processing time. |
| DELAY | Waits for a specified duration before completing. |
| SIMULATE_FAILURE | Simulates failed attempts before succeeding. |

Job payloads must be JSON objects.

Example job request:

```json
{
  "name": "Normalize customer data",
  "type": "TRANSFORM",
  "payload": {
    "text": "acme supply order 4821"
  },
  "maxAttempts": 4,
  "retryDelaySeconds": 10,
  "priority": 7,
  "dependsOn": []
}
```

## Running the Project Locally

### Requirements

- Java 21
- Maven
- Docker Desktop
- Docker Compose

### 1. Clone the repository

```bash
git clone https://github.com/ashuuakk06/QueueForge.git
```

Move into the project directory:

```bash
cd QueueForge
```

### 2. Configure environment variables

Copy the example environment file:

```powershell
Copy-Item .env.example .env
```

Open `.env` and configure the required values:

- POSTGRES_PASSWORD
- JWT_SECRET

Use local development values and do not commit your actual `.env` file.

### 3. Start the application

Run:

```bash
docker compose up --build
```

Docker Compose starts the backend, frontend, PostgreSQL, Redis, and worker services.

### 4. Open the application

- **Frontend:** http://localhost:5173
- **Backend API:** http://localhost:8080
- **Health Check:** http://localhost:8080/actuator/health

### Demo account

If demo data is enabled in your local environment, you can use:

- **Email:** demo@queueforge.dev
- **Password:** QueueForge!2026

These credentials are intended only for local demonstration.

### Stop the application

To stop the containers:

```bash
docker compose down
```

To stop the containers and remove local database and Redis data:

```bash
docker compose down -v
```

## Scaling Workers

QueueForge supports running multiple worker containers.

The default worker configuration uses four threads per process.

To start three worker containers:

```bash
docker compose up --build -d --scale worker=3
```

This runs three worker processes with four threads configured per process.

The dashboard displays worker heartbeats, running jobs, and Redis queue information.

## API Endpoints

The backend provides REST APIs for authentication and job management.

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/auth/me` | Get current account details. |
| GET | `/api/dashboard` | Get dashboard statistics. |
| GET | `/api/jobs` | Get jobs for the logged-in account. |
| POST | `/api/jobs` | Create a new job. |
| GET | `/api/jobs/{id}` | Get job details. |
| GET | `/api/jobs/{id}/events` | Get job execution events. |
| POST | `/api/jobs/{id}/retry` | Retry a failed or cancelled job. |
| POST | `/api/jobs/{id}/cancel` | Cancel a job that has not started. |

Protected endpoints require a JWT token in the Authorization header.

```http
Authorization: Bearer <token>
```

The dashboard communicates with the backend through the `/api/` route.

A small C++20 example client is also included in:

`examples/cpp-client`

## Running Tests

To run the backend tests, open the backend directory:

```bash
cd backend
mvn test
```

To build the frontend, open the frontend directory:

```bash
cd frontend
npm install
npm run build
```

The backend tests cover job execution, retry handling, concurrent job claims, worker recovery, dependency failures, and execution event access.

## Current Limitations

- The API currently returns a maximum of 500 jobs.
- The execution event endpoint returns the latest 50 events.
- Retry delays are fixed rather than exponential.
- Worker recovery uses a fixed lease duration.
- The dashboard shows worker status and queue information but does not provide detailed CPU or memory monitoring.
- The system supports built-in job handlers rather than arbitrary user-defined code.
- Job delivery is at least once, so handlers involving external operations should use idempotency keys.

## Future Improvements

Some improvements that could be added in future versions:

- Server-side pagination for job history.
- More job types and configurable handlers.
- Better worker monitoring.
- Additional testing for failure and recovery scenarios.
- Improved job scheduling and retry options.

## Author

**Ashish Pagariya**

GitHub: https://github.com/ashuuakk06

---

QueueForge is a project built to understand how background job processing, queues, worker execution, and failure recovery work together in a Spring Boot application.
