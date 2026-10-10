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

The Step 13 login endpoint `POST /api/auth/login` is not part of this table: the route set
above is unchanged by the authentication work, so credential login is called directly
against auth-service (`http://localhost:8081/api/auth/login`). Once a client holds an access
token it is sent through the gateway on the routed paths above.

Swagger/OpenAPI stays on the services themselves and is unaffected by the gateway:
`http://localhost:<service-port>/v3/api-docs` and `/swagger-ui.html`.

## Security

Security is a stateless bearer-JWT foundation:

- **Credential login:** `POST /api/auth/login` with `{email, password}` is the only public
  endpoint in the platform. It answers `{accessToken, tokenType: "Bearer", expiresIn}` on
  success and a single generic `401` "Invalid email or password" for every failure — wrong
  password, unknown email, and accounts created before Step 13 (no stored hash) are
  indistinguishable, so the endpoint cannot be used to enumerate users. Passwords are stored
  as BCrypt hashes and are never returned, logged, or echoed.
- **auth-service** issues tokens: subject is the user's UUID id, with `iat`, `exp`, issuer
  (`hireflow-auth`) and a server-derived role claim. The signing key comes from
  `hireflow.jwt.signing-key` (env `HIREFLOW_JWT_SIGNINGKEY`); the application fails at
  startup when it is missing, and no key is hardcoded anywhere.
- **All four services validate tokens** through the same stateless filter: signature,
  algorithm (HS256), expiry and issuer are checked, the request carries no session or
  cookie, and `401`/`403` answer in the common error contract with a `Bearer` challenge
  (`error="invalid_token"` when a token was supplied and rejected). No role rules exist
  yet — RBAC arrives in Step 15.
- **CSRF policy:** authority never lives in a cookie or session — each request authenticates
  itself through the `Authorization` header — so every service runs with
  `SessionCreationPolicy.STATELESS` and CSRF protection is deliberately disabled, with the
  reasoning documented in `SecurityConfiguration`.
- **Security responses** use the common error contract: `401` and `403` answer
  `{timestamp, status, error, code, message, path}` with the `UNAUTHENTICATED`/`FORBIDDEN`
  codes, `401` keeps its `WWW-Authenticate` challenge, and no stack traces, claims, or
  internal details are exposed. Responses are written directly, so no error dispatch can
  rewrite a status or path.
- **The gateway performs no authentication.** It forwards the `Authorization` header
  byte-for-byte and validates nothing itself: token validation happens only in the service
  behind the route, so behavior is identical direct and through the gateway, and `401`
  responses (including `invalid_token` rejections) pass through untouched. This keeps the
  gateway free of signing keys and keeps edge and service from ever disagreeing about a
  credential.
- **Swagger documents the contract:** every service publishes a `bearerAuth` HTTP security
  scheme (`bearer`, JWT format) in its OpenAPI document, every protected operation carries
  that security requirement plus a `401` response in the shared error model, and the public
  `POST /api/auth/login` is deliberately exempt.
- No health endpoint is exposed (no actuator dependency in any service).

| Check | auth-service (8081) | candidate/job/application | via gateway (8080) |
| --- | --- | --- | --- |
| `POST /api/auth/login` with valid credentials | `200` + access token | — | — |
| `POST /api/auth/login` with bad credentials | `401` generic message | — | — |
| Request without a token | `401` + error contract | `401` + error contract | `401` passthrough |
| Request with an invalid/expired token | `401` `invalid_token` | `401` `invalid_token` | `401` passthrough |
| `GET /api/users/{id}` with a valid token | `200` | — | `200`, byte-identical body |
| Create via `POST` (no CSRF token) | `201` with a token | `201` with a token | `201` |
| `/v3/api-docs`, `/swagger-ui.html` | `401` → `200` with a token | `200` (public) | — |
| `/actuator/health` | not present (`404`) | not present (`404`) | — |

