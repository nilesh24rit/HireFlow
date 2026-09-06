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
