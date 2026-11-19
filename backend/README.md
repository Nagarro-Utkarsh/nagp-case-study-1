# HabitLoop backend

This Maven reactor contains the six Spring Boot services, shared API/authentication code, and the standalone integration normalizer Lambda. Java 21 and Maven 3.9+ are required.

## Run locally

From this directory, start the services with Docker Compose:

```sh
colima start --memory 4  # if using Colima
docker-compose up --build
```

PostgreSQL is available on port `5432`, DynamoDB Local on `8000`, and the APIs on ports `8081`–`8086`. Swagger UI is available at `/swagger-ui.html` on each API. The local integration publisher uses direct mode; AWS deployments can set `INTEGRATION_PUBLISHER=eventbridge`.

The Compose credentials and secrets are for local development only. Replace `JWT_SECRET`, `INTERNAL_KEY`, and database credentials outside the local demo. Configure `DYNAMODB_ENDPOINT` only for DynamoDB Local; AWS deployments use the task role and AWS SDK default region/credentials.

## Build and tests

```sh
mvn test
mvn package
```

`demo.http` contains requests for signup, habit creation/logging, community challenges, and mock wearable events. Its response scripts save returned IDs and the JWT as variables.

### Postman

Import [`HabitLoop.postman_collection.json`](../docs/04-api-documentation/postman/HabitLoop.postman_collection.json) directly into Postman. No environment file is required: URLs, the local internal key, tokens, and IDs are collection variables. Start with **01 · Setup & authentication**, then run the numbered service folders in order. Setup creates new owner, member, and cross-tenant demo accounts; successful requests save the IDs needed by later requests. The **07 · Live updates** folder contains a long-lived SSE request and should be sent separately. **99 · Cleanup** disconnects and deletes demo resources, so run it manually and last. Internal requests use the local key and are intended only for trusted local/private access. Avoid exporting the collection after running it if its saved variables contain live tokens or wearable keys.

The shaded Lambda artifact is `integration-normalizer-lambda/target/integration-normalizer-lambda-1.0.0.jar`; configure the Lambda handler as `com.habitloop.lambda.IntegrationNormalizerHandler`. On AWS, an EventBridge rule for `PartnerEventReceived` on bus `habitloop-events` invokes the function, with separate delivery and asynchronous-processing SQS dead-letter queues; `ACTIVITY_URL` and `INTEGRATION_URL` point to the private service URLs. Set `INTERNAL_KEY_SECRET_ARN` to the Secrets Manager ARN; the handler retrieves the key during initialization. Do not put the key itself in Lambda environment variables.

## Services

| Service | Port | Data |
|---|---:|---|
| User | 8081 | PostgreSQL `user_service` |
| Activity | 8082 | PostgreSQL `activity_service` |
| Challenge | 8083 | PostgreSQL `challenge_service` |
| Integration | 8084 | PostgreSQL `integration_service` |
| Analytics | 8085 | DynamoDB `habitloop-analytics` |
| Recommendation | 8086 | Stateless |

All user-facing API routes require a JWT except signup/login. `/internal/**` routes require `X-Internal-Key`; partner event ingestion requires a connection-specific `X-Api-Key`. The analytics SSE route accepts the same JWT as `?token=` for browser `EventSource` clients.
