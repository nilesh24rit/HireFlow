# HireFlow

HireFlow is an AI-powered recruitment platform designed to streamline hiring workflows. The platform enables recruiters to publish and manage job postings, candidates to maintain comprehensive profiles and submit applications, and incorporates intelligent capabilities for automated candidate-job matching and resume analysis.

## Technologies

- Java 25
- Spring Boot 4.1.x
- Apache Maven
- Microservices Architecture
- PostgreSQL
- Redis
- Apache Kafka
- gRPC & Protocol Buffers
- Spring AI
- Spring Security & Google OAuth2
- Springdoc OpenAPI

## Architecture & Services

The platform is organized into independent microservices:

- **api-gateway**: Central routing, rate limiting, and API edge management.
- **auth-service**: Identity management, authentication, token issuance, and OAuth2 integration.
- **candidate-service**: Candidate profiles, work experience, education, and portfolio records.
- **job-service**: Job requisitions, specifications, lifecycle, and recruiter management.
- **application-service**: Job application submission, review pipelines, and status tracking.
- **ai-service**: Resume parsing, skill extraction, candidate-job matching, and recommendation models.
- **notification-service**: Event-driven alerts, messaging, and multi-channel notifications.
- **analytics-service**: Recruitment metrics, funnel analytics, and hiring pipeline reporting.

## Build

Every service builds independently with Apache Maven. The build enforces JDK 25 or newer and Maven 3.9 or newer.

```bash
cd api-gateway
mvn clean verify
```

Repeat the same command inside each service directory. Shared protobuf definitions live in `proto/` and are compiled into gRPC stubs during the build.

## API Gateway Routing

The `api-gateway` module is the single entry point of the platform. It forwards requests to
the services without rewriting paths, methods, status codes, or response bodies:

| Public path | Route id | Target (local development) |
| --- | --- | --- |
| `/api/users/**` | `auth-service` | `http://localhost:8081` |
| `/api/candidates/**` | `candidate-service` | `http://localhost:8082` |
| `/api/jobs/**` | `job-service` | `http://localhost:8083` |
| `/api/applications/**` | `application-service` | `http://localhost:8084` |

Target addresses are development defaults declared in source and can be overridden with the
`hireflow.services.*-uri` properties (or equivalent environment variables); they contain no
credentials. Unmatched paths return `404`, an unreachable service returns a clean `502`
without stack traces, and every response produced by a service is passed through untouched.
Gateway access logs record only route id, HTTP method, request path, and response status.

Swagger/OpenAPI stays on the services themselves and is unaffected by the gateway:
`http://localhost:<service-port>/v3/api-docs` and `/swagger-ui.html`.

## Security

Security is a stateless, header-authenticated foundation (JWT arrives in a later step):

- **auth-service** holds the platform's only `SecurityFilterChain`: every endpoint requires
  authentication, currently over HTTP Basic. There is no form login, no session-based login,
  and no public endpoint — Swagger included, which answers `401` until credentials are sent.
- **CSRF policy:** authority never lives in a cookie or session — each request authenticates
  itself through the `Authorization` header — so auth-service runs with
  `SessionCreationPolicy.STATELESS` and CSRF protection is deliberately disabled, with the
  reasoning documented in `SecurityConfiguration`. The other REST services carry no security
  stack, sessions, or cookies, so there is no CSRF surface to remove or preserve there.
- **Security responses** use the common error contract: `401` and `403` answer
  `{timestamp, status, error, code, message, path}` with the `UNAUTHENTICATED`/`FORBIDDEN`
  codes, `401` keeps its `WWW-Authenticate` challenge, and no stack traces or internal
  details are exposed. Responses are written directly, so no error dispatch can rewrite a
  status or path.
- **The gateway performs no authentication.** It only routes, so behavior is identical
  direct and through the gateway, and `401` responses from auth-service pass through
  untouched.
- No health endpoint is exposed (no actuator dependency in any service).

| Check | auth-service (8081) | candidate/job/application | via gateway (8080) |
| --- | --- | --- | --- |
| Request without credentials | `401` + error contract | not applicable (no auth stack) | `401` passthrough |
| `GET /api/users/{id}` with credentials | `200` | — | `200`, byte-identical body |
| Create via `POST` (no CSRF token) | `201` with credentials | `201` (public) | `201` |
| `/v3/api-docs`, `/swagger-ui.html` | `401` → `200` with credentials | `200` (public) | — |
| `/actuator/health` | not present (`404`) | not present (`404`) | — |

