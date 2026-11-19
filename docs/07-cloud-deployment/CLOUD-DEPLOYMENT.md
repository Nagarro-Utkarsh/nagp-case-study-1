# Deliverable 7 — Cloud Deployment (AWS)

HabitLoop is deployed to **AWS, region `ap-southeast-2` (Sydney)**. This document lists every
service used, its critical configuration, and the console screenshots that evidence it.

| Item | Value |
|---|---|
| Application URL | `https://<cloudfront-domain>.cloudfront.net` |
| API base (same origin) | `https://<cloudfront-domain>.cloudfront.net/api` |
| Region | `ap-southeast-2` |
| Deployment date | `<YYYY-MM-DD>` |

> Screenshots live in [`screenshots/`](./screenshots), one folder per service.

---

## 1. Deployment steps (how the system was deployed)

1. **Container images** – each Spring Boot service is built with its multi-stage `Dockerfile`
   (`linux/amd64`) and pushed to its own Amazon ECR repository with an immutable tag:
   ```bash
   aws ecr get-login-password --region ap-southeast-2 | docker login --username AWS --password-stdin <account>.dkr.ecr.ap-southeast-2.amazonaws.com
   ./scripts/publish-images.sh <image-tag>     # builds + pushes habitloop/<service>-service:<image-tag>
   ```
2. **Network & data** – VPC (2 AZs, public / private application / isolated data subnets),
   RDS PostgreSQL in the isolated subnets, DynamoDB table `habitloop-analytics`, secrets in
   Secrets Manager. The relational schema (`backend/db/init.sql`) is applied once by a
   one-off Fargate task inside the VPC.
3. **Compute** – one ECS Fargate service per microservice in the private subnets, registered with
   the ALB target groups and with Cloud Map (`habitloop.internal`) for service-to-service calls.
4. **Event pipeline** – the Normalizer Lambda (`integration-normalizer-lambda` jar, Java 21) is
   deployed into the VPC; an EventBridge rule on bus `habitloop-events` targets it, with SQS
   dead-letter queues for failures.
5. **Frontend** – the SvelteKit static build is uploaded to the private S3 bucket and served via
   CloudFront:
   ```bash
   cd frontend && pnpm install && pnpm build
   aws s3 sync apps/web/build s3://<frontend-bucket> --delete
   aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
   ```
6. **Monitoring** – CloudWatch log groups, dashboard `HabitLoop-Demo`, alarms → SNS email topic.

---

## 2. Services and critical configuration

### 2.1 Amazon VPC — networking
- CIDR `10.20.0.0/16`, 2 AZs; 2 public `/24`, 2 private application `/20`, 2 isolated data `/24` subnets.
- Internet Gateway for public subnets; 1 NAT Gateway for private egress; data subnets have no internet route.
- Security groups per component (ALB, each service, Lambda, database) — no "allow all" outbound.
- Details: [Network Architecture](../06-network-architecture/README.md).

| Screenshot | File |
|---|---|
| VPC overview / resource map | ![](./screenshots/01-vpc/01-vpc-resource-map.png) |
| Subnets list (6 subnets, 2 AZs) | ![](./screenshots/01-vpc/02-subnets.png) |
| Route tables (public → IGW, private → NAT, isolated) | ![](./screenshots/01-vpc/03-route-tables.png) |
| NAT Gateway | ![](./screenshots/01-vpc/04-nat-gateway.png) |
| Security groups (DB SG inbound 5432 from service SGs only) | ![](./screenshots/01-vpc/05-security-groups.png) |

### 2.2 Amazon ECR — container registry
- 6 repositories `habitloop/<service>-service`, **immutable tags**, **scan on push**,
  lifecycle policy keeps the last 5 images.

| Screenshot | File |
|---|---|
| Repositories list | ![](./screenshots/02-ecr/01-repositories.png) |
| Image tags + scan results | ![](./screenshots/02-ecr/02-image-scan.png) |

### 2.3 Amazon ECS on AWS Fargate — microservices
- 1 cluster, 6 services (user, activity, challenge, integration, analytics, recommendation).
- Task size 0.25 vCPU / 512 MiB, desired count 1, auto scaling 1 → 2 tasks at 70 % CPU.
- Private subnets, no public IP, deployment circuit breaker with automatic rollback,
  health-check grace period 180 s, container logs to CloudWatch `/habitloop/<service>`.
- Secrets (DB credentials, JWT key, internal key) injected from Secrets Manager; separate IAM
  task role and execution role per service.

| Screenshot | File |
|---|---|
| Cluster – services tab (6 services, all running) | ![](./screenshots/03-ecs/01-cluster-services.png) |
| Service details (deployment, circuit breaker, networking) | ![](./screenshots/03-ecs/02-service-details.png) |
| Task definition (CPU/memory, env, secrets, logging) | ![](./screenshots/03-ecs/03-task-definition.png) |
| Auto scaling policy | ![](./screenshots/03-ecs/04-auto-scaling.png) |
| Cloud Map namespace `habitloop.internal` | ![](./screenshots/03-ecs/05-cloud-map.png) |

### 2.4 Elastic Load Balancing — Application Load Balancer
- Internet-facing ALB in the public subnets, listener HTTP :80, default action fixed 404.
- Path-based rules → 6 target groups (IP targets), health check `/actuator/health`.

| Path pattern | Target group |
|---|---|
| `/api/auth*`, `/api/users*` | user-service |
| `/api/habits*`, `/api/activities*` | activity-service |
| `/api/communities*`, `/api/challenges*`, `/api/invites*` | challenge-service |
| `/api/integrations*` | integration-service |
| `/api/analytics*` | analytics-service |
| `/api/recommendations*` | recommendation-service |

| Screenshot | File |
|---|---|
| Listener rules | ![](./screenshots/04-alb/01-listener-rules.png) |
| Target groups – healthy targets | ![](./screenshots/04-alb/02-target-health.png) |

### 2.5 Amazon RDS for PostgreSQL — relational data
- PostgreSQL 16, `db.t4g.micro`, 20 GB gp2, **storage encrypted**, **not publicly accessible**,
  isolated data subnets, TLS required (`sslmode=require`). Automated backups are disabled for
  this short-lived demo (enable 7+ days retention and Multi-AZ for production).
- One schema per service (`user_service`, `activity_service`, `challenge_service`, `integration_service`).
- Master credentials generated and stored in Secrets Manager.

| Screenshot | File |
|---|---|
| Database summary (engine, class, status) | ![](./screenshots/05-rds/01-database-summary.png) |
| Connectivity & security (private, SG, subnets) | ![](./screenshots/05-rds/02-connectivity.png) |
| Configuration (encryption, storage) | ![](./screenshots/05-rds/03-configuration.png) |

### 2.6 Amazon DynamoDB — analytics store
- Table `habitloop-analytics`, partition key `pk`, sort key `sk`, **on-demand** capacity,
  encryption at rest. Items: logs, per-day counts + completion hours, streaks, user cohorts.

| Screenshot | File |
|---|---|
| Table overview (keys, capacity mode) | ![](./screenshots/06-dynamodb/01-table-overview.png) |
| Explore items (sample analytics items) | ![](./screenshots/06-dynamodb/02-items.png) |

### 2.7 Amazon EventBridge, AWS Lambda and Amazon SQS — partner event pipeline
- Custom bus `habitloop-events`; rule matches `source = habitloop.integration`,
  `detail-type = PartnerEventReceived`.
- Target: Lambda **Integration Normalizer** (Java 21, 512 MB, 30 s timeout, in VPC), retry
  attempts 2, max event age 1 h.
- SQS dead-letter queues: EventBridge delivery DLQ and Lambda failure DLQ (14-day retention).

| Screenshot | File |
|---|---|
| Event bus + rule (event pattern) | ![](./screenshots/07-eventbridge-lambda/01-eventbridge-rule.png) |
| Rule target with retry policy & DLQ | ![](./screenshots/07-eventbridge-lambda/02-rule-target.png) |
| Lambda configuration (runtime, memory, VPC) | ![](./screenshots/07-eventbridge-lambda/03-lambda-config.png) |
| Lambda monitoring / logs after a wearable event | ![](./screenshots/07-eventbridge-lambda/04-lambda-logs.png) |
| SQS dead-letter queues | ![](./screenshots/07-eventbridge-lambda/05-sqs-dlqs.png) |

### 2.8 Amazon S3 and Amazon CloudFront — frontend hosting
- S3 bucket: **Block all public access**, SSE-S3 encryption, TLS-only bucket policy, readable
  only by CloudFront via **Origin Access Control**.
- CloudFront: default behaviour → S3 (redirect HTTP → HTTPS, SPA rewrite function);
  behaviour `api/*` → ALB, caching disabled, all methods, all viewer headers except `Host`.

| Screenshot | File |
|---|---|
| S3 bucket – permissions (public access blocked) | ![](./screenshots/08-s3-cloudfront/01-s3-permissions.png) |
| CloudFront distribution – origins | ![](./screenshots/08-s3-cloudfront/02-cloudfront-origins.png) |
| CloudFront behaviours (`api/*` + default) | ![](./screenshots/08-s3-cloudfront/03-cloudfront-behaviors.png) |
| Application running in browser via CloudFront URL | ![](./screenshots/08-s3-cloudfront/04-app-live.png) |

### 2.9 AWS Secrets Manager and IAM — security
- Secrets: database credentials, JWT signing secret, internal service key.
- Each ECS service has its own **task role** (e.g. only analytics can access DynamoDB, only
  integration can `PutEvents`) and **execution role** (pull image, read its secrets, write logs).

| Screenshot | File |
|---|---|
| Secrets list | ![](./screenshots/09-secrets-iam/01-secrets.png) |
| Example task role policy (least privilege) | ![](./screenshots/09-secrets-iam/02-task-role-policy.png) |

### 2.10 Amazon CloudWatch and Amazon SNS — monitoring
- Log groups `/habitloop/<service>` (1-week retention).
- Dashboard **HabitLoop-Demo**: API requests & 5xx, ECS CPU, PostgreSQL CPU/connections/free
  storage, unhealthy targets, Normalizer errors/throttles, DLQ messages.
- Alarms: ALB 5xx, unhealthy targets, service CPU ≥ 80 %, RDS CPU / connections / free storage,
  Lambda errors / throttles, EventBridge failed invocations, DLQ messages → SNS email topic.

| Screenshot | File |
|---|---|
| Dashboard | ![](./screenshots/10-cloudwatch/01-dashboard.png) |
| Alarms list | ![](./screenshots/10-cloudwatch/02-alarms.png) |
| Service log stream | ![](./screenshots/10-cloudwatch/03-logs.png) |
| SNS topic + confirmed subscription | ![](./screenshots/10-cloudwatch/04-sns.png) |

---

## 3. Verification

| Check | Result |
|---|---|
| All 6 ECS services running, targets healthy | ✅ |
| SPA loads over HTTPS from CloudFront | ✅ |
| Sign up → create habit → log → dashboard updates | ✅ |
| Wearable event → EventBridge → Lambda → `PROCESSED` | ✅ |
| Recommendations page shows and applies a suggestion | ✅ |

## 4. Cost and teardown

The environment uses the smallest instance sizes, a single NAT Gateway, on-demand DynamoDB and
1-week log retention. It is removed after evaluation to avoid ongoing charges.
