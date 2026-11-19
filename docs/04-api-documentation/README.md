# Deliverable 4 — API Documentation

| Artifact | File | How to use |
|---|---|---|
| OpenAPI 3.0 specification (all six services, incl. Recommendation) | [openapi/habitloop-openapi.yaml](openapi/habitloop-openapi.yaml) | Open in [Swagger Editor](https://editor.swagger.io) (File → Import file) or import into Postman |
| Rendered API reference (offline HTML) | [openapi/habitloop-api-reference.html](openapi/habitloop-api-reference.html) | Download and open in a browser |
| Postman collection (~100 requests incl. negative tests) | [postman/HabitLoop.postman_collection.json](postman/HabitLoop.postman_collection.json) | Postman → Import → run folders in order |
| Live Swagger UI per service | `http://localhost:808x/swagger-ui.html` | Available while the backend runs locally |
| HTTP client examples | [backend/demo.http](../../backend/demo.http) | IntelliJ / VS Code REST client |

## Endpoint summary

### User Service (8081)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/signup` | — | Register in a workspace (`public`, `acme`), returns JWT |
| POST | `/api/auth/login` | — | Login, returns JWT |
| GET / PUT | `/api/users/me` | JWT | Read / update profile |
| GET | `/api/users?email=` | JWT | Look up a same-tenant user (invites) |

### Activity Service (8082)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET / POST | `/api/habits` | JWT | List / create habits (DAILY or WEEKLY, `daysOfWeek`, `preferredTime`) |
| GET / PUT / DELETE | `/api/habits/{id}` | JWT | Read / update / delete a habit |
| POST | `/api/habits/{id}/logs` | JWT | Mark habit done (manual completion) |
| GET | `/api/habits/{id}/logs` | JWT | Completion history |

### Challenge Service (8083)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET / POST | `/api/communities` | JWT | Community feed / create community |
| GET | `/api/communities/{id}` | JWT | Members and challenges |
| POST | `/api/communities/{id}/join` | JWT | Join a public community |
| POST | `/api/communities/{id}/invites` | JWT (owner) | Invite by email |
| GET | `/api/invites` | JWT | My pending invites |
| POST | `/api/invites/{id}/accept` · `/decline` | JWT | Respond to an invite |
| GET | `/api/challenges` | JWT | Challenge feed |
| POST | `/api/communities/{id}/challenges` | JWT (owner) | Create challenge |
| GET | `/api/challenges/{id}` | JWT (member) | Challenge + leaderboard |
| POST | `/api/challenges/{id}/join` | JWT (member) | Join with a matching-category habit |

### Integration Service (8084)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/integrations/partners` | JWT | Partner catalogue |
| GET / POST | `/api/integrations/connections` | JWT | List / connect device (returns one-time API key) |
| DELETE | `/api/integrations/connections/{id}` | JWT | Disconnect |
| POST | `/api/integrations/connections/{id}/simulate` | JWT | Simulate a device event |
| GET | `/api/integrations/connections/{id}/events` | JWT | Event history and processing status |
| POST | `/api/integrations/events` | `X-Api-Key` | Device/partner event ingestion (idempotent on `eventId`) |

### Analytics Service (8085)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/analytics/dashboard` | JWT | Today count, streak, 7-day completion % per habit |
| GET | `/api/analytics/habits/{id}/trend?days=30` | JWT | Daily counts |
| GET | `/api/analytics/cohorts` | JWT | Signup-week cohorts |
| GET | `/api/analytics/stream?token=` | JWT (query) | SSE `dashboard-updated` events |

### Recommendation Service (8086)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/recommendations` | JWT | `TIMING` and `FREQUENCY` suggestions with reason |
| POST | `/api/recommendations/apply` | JWT | Accept a suggestion → updates habit schedule via Activity Service |

Example response:

```json
[
  {
    "habitId": "6f1c2a52-6c8e-4c8a-9d0e-3b8b8f0f9a11",
    "type": "TIMING",
    "current": "08:00",
    "suggested": "18:00",
    "reason": "Most recorded completions occur in this two-hour window."
  }
]
```

### Internal service contracts (`X-Internal-Key`, private network only)
| Method | Path | Called by |
|---|---|---|
| POST | `/internal/activity-logs` | Normalizer Lambda |
| PUT | `/internal/integrations/events/{id}/status` | Normalizer Lambda |
| POST | `/internal/analytics/users/signup` | User Service |
| POST | `/internal/analytics/activity` | Activity Service |
| GET | `/internal/analytics/habits/{id}/count` | Challenge Service |
| GET | `/internal/analytics/habits/{id}/hours` | Recommendation Service |

## Using the Postman collection

1. Postman → **Import** → select `HabitLoop.postman_collection.json` (no environment file needed).
2. Run **00 · Health**, then **01 · Setup & authentication** — it creates owner, member and a
   cross-tenant account and stores tokens/IDs in collection variables.
3. Run folders **02 → 06** in order (Collection Runner works). Each request has test scripts and
   negative cases (wrong tenant, missing auth, category mismatch, invalid device key…).
4. **07 · Live updates** is a long-lived SSE request — send it manually.
5. **99 · Cleanup** deletes demo data — run last.

**Against the cloud deployment:** set the six `*Url` collection variables (`userUrl`,
`activityUrl`, …) to `https://<cloudfront-domain>`. The `06 · Internal service contracts` folder
works only locally because internal routes are not exposed publicly.

## Error format

Errors follow RFC 7807 (`application/problem+json`):

```json
{ "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Habit not found", "instance": "/api/habits/…" }
```
