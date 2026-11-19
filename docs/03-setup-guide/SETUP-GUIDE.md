# Deliverable 3 — Local Setup Guide (Docker Compose)

This guide runs the complete HabitLoop stack on a laptop: PostgreSQL, DynamoDB Local, the six
Spring Boot microservices (via Docker Compose) and the SvelteKit frontend.

> No AWS account is required locally. The Integration Service runs in **direct mode**, which applies
> the same partner-event validation/normalisation in-process instead of going through EventBridge
> and Lambda.

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Docker Desktop **or** Colima + Docker CLI | Docker Engine 24+, Compose v2 | `docker compose version` |
| Node.js | 22 (see `frontend/.nvmrc`) | `node -v` |
| pnpm | 10.30.1 (via Corepack) | `pnpm -v` |
| Git | any | `git --version` |
| *(optional)* Java 21 + Maven 3.9 | only to run backend tests outside Docker | `java -version`, `mvn -v` |

Allocate **at least 4 GB of memory** to Docker (eight containers run concurrently).

Free local ports: `5432`, `8000`, `8081`–`8086`, `5173`.

## 2. Clone the repository

```sh
git clone <repository-url> habitloop
cd habitloop
```

## 3. Start the backend

```sh
cd backend
# Colima users only:
colima start --memory 4
docker compose up --build
```

The first build downloads Maven dependencies and compiles every service inside Docker, so it
takes several minutes. Subsequent starts are much faster.

What starts:

| Container | Port | Purpose |
|---|---:|---|
| `postgres` | 5432 | PostgreSQL 16; `db/init.sql` creates the four service schemas |
| `dynamodb-local` | 8000 | DynamoDB Local; the Analytics Service creates `habitloop-analytics` automatically |
| `user-service` | 8081 | Auth, tenants (`public`, `acme` are seeded), profiles |
| `activity-service` | 8082 | Habits and activity logs |
| `challenge-service` | 8083 | Communities, invites, challenges |
| `integration-service` | 8084 | Wearable connections and events (`INTEGRATION_PUBLISHER=direct`) |
| `analytics-service` | 8085 | Streaks, trends, cohorts, SSE |
| `recommendation-service` | 8086 | Timing / frequency recommendations |

### Verify the backend

In a second terminal:

```sh
for port in 8081 8082 8083 8084 8085 8086; do
  printf "%s " $port; curl -s http://localhost:$port/actuator/health; echo
done
```

Every service should return `{"status":"UP"}`.

Swagger UI is available for every service, for example:

- http://localhost:8081/swagger-ui.html (User)
- http://localhost:8086/swagger-ui.html (Recommendation)

## 4. Start the frontend

```sh
cd frontend
corepack enable
pnpm install
pnpm dev
```

Open **http://localhost:5173**. The Vite dev server proxies `/api/*` paths to the matching backend
port, so no extra configuration is needed. (`VITE_API_BASE` stays empty — see
`apps/web/.env.example`.)

## 5. Quick smoke test

1. Open http://localhost:5173/signup, create an account in workspace `public`.
2. Complete onboarding by creating a daily habit (e.g. *Morning walk*, category `fitness`).
3. On the dashboard click **Log** — the activity summary and streak update.

For the complete scenario follow the [User Walkthrough](../05-user-walkthrough/USER-WALKTHROUGH.md).
For API-level testing import the [Postman collection](../04-api-documentation/postman/HabitLoop.postman_collection.json)
or use [`backend/demo.http`](../../backend/demo.http).

## 6. Configuration reference

All configuration is supplied through environment variables (see `backend/docker-compose.yml`).

| Variable | Used by | Local value | Notes |
|---|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | User, Activity, Challenge, Integration | `jdbc:postgresql://postgres:5432/habitloop`, `habitloop`/`habitloop` | Secrets Manager in AWS |
| `JWT_SECRET` | all services | local placeholder | Secrets Manager in AWS |
| `INTERNAL_KEY` | all services | `local-internal-key` | Secrets Manager in AWS |
| `USER_URL` … `RECOMMENDATION_URL` | all services | `http://<service>:<port>` | Cloud Map DNS in AWS |
| `FRONTEND_ORIGIN` | all services | `http://localhost:5173` | CORS |
| `INTEGRATION_PUBLISHER` | Integration | `direct` | `eventbridge` in AWS |
| `EVENT_BUS_NAME` | Integration | — | `habitloop-events` in AWS |
| `DYNAMODB_ENDPOINT` | Analytics | `http://dynamodb-local:8000` | unset in AWS (uses IAM task role) |
| `ANALYTICS_TABLE` | Analytics | `habitloop-analytics` | |

The local credentials are for development only.

## 7. Running tests and builds

```sh
# Backend unit tests (requires Java 21 + Maven)
cd backend && mvn test

# Frontend type-check, unit tests and production build
cd frontend && pnpm check && pnpm test && pnpm build
```

The production web build is written to `frontend/apps/web/build/`.

## 8. Stopping and resetting

```sh
cd backend
docker compose down        # stop containers, keep PostgreSQL data
docker compose down -v     # stop and delete the PostgreSQL volume (fresh start)
```

DynamoDB Local runs in memory, so analytics data is reset on every restart.

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| Containers exit with code 137 | Docker is out of memory — give it ≥ 4 GB (`colima start --memory 4`) |
| `port is already allocated` | Stop the process using that port, or a previous `docker compose` run |
| Dashboard shows "unavailable" briefly | Analytics updates asynchronously; refresh after a few seconds |
| No recommendations shown | Timing suggestions need ≥ 5 completions in the last 28 days clustered in a different 2-hour window from the preferred time |
| Frontend 502/504 on `/api/*` | Backend not running or still starting — check `docker compose ps` and the health URLs |
