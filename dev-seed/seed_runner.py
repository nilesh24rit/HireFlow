#!/usr/bin/env python3
"""Explicit, development-only synthetic data runner for HireFlow."""

from __future__ import annotations

import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any


RESERVED_DOMAIN = "example.com"
DEFAULT_PASSWORD = "HireFlow-Seed-Only-2026!"
SKILL_SETS = (
    ("Java", "Spring Boot", "PostgreSQL", "Kafka"),
    ("TypeScript", "React", "CSS", "Jest"),
    ("Python", "Data Engineering", "SQL", "Airflow"),
    ("AWS", "Docker", "Kubernetes", "Terraform"),
    ("Selenium", "JUnit", "API Testing", "CI/CD"),
)
JOB_FAMILIES = (
    ("Backend Engineer", "Java", "Spring Boot"),
    ("Frontend Engineer", "TypeScript", "React"),
    ("Cloud Engineer", "AWS", "Kubernetes"),
    ("Data Engineer", "Python", "SQL"),
    ("QA Automation Engineer", "Selenium", "JUnit"),
)


class SeedSafetyError(RuntimeError):
    """Raised when the runner is not being used in an explicitly safe mode."""


class SeedRequestError(RuntimeError):
    """Raised when a service rejects a seed request."""


@dataclass(frozen=True)
class SeedConfig:
    auth_url: str = "http://localhost:8081"
    candidate_url: str = "http://localhost:8082"
    job_url: str = "http://localhost:8083"
    application_url: str = "http://localhost:8084"
    profile: str = "dev"
    environment: str = "development"
    enabled: bool = False
    random_seed: int = 2801
    candidate_count: int = 100
    recruiter_count: int = 15
    administrator_count: int = 2
    job_count: int = 100
    application_count: int = 200
    batch_size: int = 10
    request_timeout: float = 10.0
    retries: int = 2
    admin_email: str = ""
    admin_password: str = ""
    synthetic_password: str = DEFAULT_PASSWORD

    def validate(self, execute: bool) -> None:
        if not execute or not self.enabled:
            raise SeedSafetyError(
                "Seeding is disabled. Pass --execute and set SEED_ENABLED=true."
            )
        if self.profile not in {"dev", "test"}:
            raise SeedSafetyError("Seeding is allowed only with the dev or test profile.")
        if self.environment.lower() in {"prod", "production"}:
            raise SeedSafetyError("Refusing to seed a production environment.")
        if not self.admin_email or not self.admin_password:
            raise SeedSafetyError(
                "SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD are required."
            )
        if self.batch_size < 1:
            raise SeedSafetyError("SEED_BATCH_SIZE must be at least 1.")
        if any(value < 0 for value in (
            self.candidate_count,
            self.recruiter_count,
            self.administrator_count,
            self.job_count,
            self.application_count,
        )):
            raise SeedSafetyError("Seed counts must not be negative.")


@dataclass
class SeedReport:
    created: dict[str, int] = field(default_factory=dict)
    existing: dict[str, int] = field(default_factory=dict)
    failed: list[dict[str, str]] = field(default_factory=list)
    duplicate_applications_prevented: int = 0
    outbox_events_created: int = 0
    analytics_records_verified: int = 0
    notification_records_verified: int = 0

    def increment(self, bucket: str, entity: str, amount: int = 1) -> None:
        target = self.created if bucket == "created" else self.existing
        target[entity] = target.get(entity, 0) + amount

    def as_dict(self) -> dict[str, Any]:
        return {
            "created": self.created,
            "existing": self.existing,
            "failed": self.failed,
            "duplicateApplicationsPrevented": self.duplicate_applications_prevented,
            "outboxEventsCreated": self.outbox_events_created,
            "analyticsRecordsVerified": self.analytics_records_verified,
            "notificationRecordsVerified": self.notification_records_verified,
        }


class SeedClient:
    """Small standard-library HTTP client; no service database is accessed."""

    def __init__(self, base_url: str, timeout: float, retries: int) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = retries

    def request(
        self,
        method: str,
        path: str,
        token: str | None = None,
        body: dict[str, Any] | None = None,
        expected: tuple[int, ...] = (200,),
    ) -> Any:
        payload = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(
            self.base_url + path, data=payload, headers=headers, method=method
        )
        last_error: Exception | None = None
        for attempt in range(self.retries + 1):
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    raw = response.read()
                    if response.status not in expected:
                        raise SeedRequestError(
                            f"{method} {path} returned unexpected status {response.status}"
                        )
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as error:
                raw = error.read().decode("utf-8", errors="replace")
                if error.code in {400, 401, 403, 404, 409, 422}:
                    raise SeedRequestError(
                        f"{method} {path} returned {error.code}: {raw[:300]}"
                    ) from error
                last_error = error
            except (urllib.error.URLError, TimeoutError) as error:
                last_error = error
            if attempt < self.retries:
                time.sleep(0.25 * (attempt + 1))
        raise SeedRequestError(f"{method} {path} failed after retries: {last_error}")


@dataclass(frozen=True)
class SyntheticUser:
    id: str
    email: str
    role: str
    password: str


class SeedRunner:
    """Coordinates API calls while keeping each service's data ownership intact."""

    def __init__(self, config: SeedConfig) -> None:
        self.config = config
        self.auth = SeedClient(config.auth_url, config.request_timeout, config.retries)
        self.candidate = SeedClient(
            config.candidate_url, config.request_timeout, config.retries
        )
        self.job = SeedClient(config.job_url, config.request_timeout, config.retries)
        self.application = SeedClient(
            config.application_url, config.request_timeout, config.retries
        )
        self.random = random.Random(config.random_seed)
        self.report = SeedReport()

    def login(self, email: str, password: str) -> str:
        response = self.auth.request(
            "POST",
            "/api/auth/login",
            body={"email": email, "password": password},
        )
        token = response.get("accessToken") if isinstance(response, dict) else None
        if not token:
            raise SeedRequestError("Auth service login did not return an access token.")
        return str(token)

    def ensure_user(self, token: str, email: str, role: str, index: int) -> SyntheticUser:
        try:
            response = self.auth.request(
                "GET", f"/api/users/email/{email}", token=token
            )
            self.report.increment("existing", role.lower())
        except SeedRequestError as error:
            if " returned 404:" not in str(error):
                raise
            response = self.auth.request(
                "POST",
                "/api/users",
                token=token,
                body={
                    "email": email,
                    "firstName": role.title(),
                    "lastName": f"Seed{index:03d}",
                    "password": self.config.synthetic_password,
                },
                expected=(201,),
            )
            self.report.increment("created", role.lower())
        user_id = response.get("id") if isinstance(response, dict) else None
        if not user_id:
            raise SeedRequestError(f"Auth service returned no id for {email}.")
        current_role = response.get("role")
        if current_role != role:
            response = self.auth.request(
                "PUT",
                f"/api/users/{user_id}/role",
                token=token,
                body={"role": role},
            )
        return SyntheticUser(
            id=str(user_id),
            email=email,
            role=role,
            password=self.config.synthetic_password,
        )

    def generate_users(self) -> tuple[list[SyntheticUser], list[SyntheticUser], list[SyntheticUser]]:
        admin_token = self.login(self.config.admin_email, self.config.admin_password)
        candidates = [
            self.ensure_user(
                admin_token,
                f"candidate{index:03d}@{RESERVED_DOMAIN}",
                "CANDIDATE",
                index,
            )
            for index in range(1, self.config.candidate_count + 1)
        ]
        recruiters = [
            self.ensure_user(
                admin_token,
                f"recruiter{index:03d}@{RESERVED_DOMAIN}",
                "RECRUITER",
                index,
            )
            for index in range(1, self.config.recruiter_count + 1)
        ]
        administrators = [
            self.ensure_user(
                admin_token,
                f"admin{index:03d}@{RESERVED_DOMAIN}",
                "ADMIN",
                index,
            )
            for index in range(1, self.config.administrator_count + 1)
        ]
        return candidates, recruiters, administrators

    def _pause_after_batch(self, completed: int) -> None:
        if completed and completed % self.config.batch_size == 0:
            time.sleep(0.05)

    def ensure_candidate(self, user: SyntheticUser, index: int) -> str:
        token = self.login(user.email, user.password)
        try:
            response = self.candidate.request(
                "GET", f"/api/candidates/user/{user.id}", token=token
            )
            self.report.increment("existing", "candidates")
            return str(response["id"])
        except SeedRequestError as error:
            if " returned 404:" not in str(error):
                raise
        skills = list(SKILL_SETS[(index - 1) % len(SKILL_SETS)])
        response = self.candidate.request(
            "POST",
            "/api/candidates",
            token=token,
            body={
                "userId": user.id,
                "headline": f"{skills[0]} {skills[1]} professional",
                "summary": (
                    f"Synthetic {skills[0]} specialist with experience delivering "
                    f"reliable {skills[1]} systems and collaborative engineering work."
                ),
                "location": ("Berlin", "Toronto", "Singapore", "Dublin")[index % 4],
                "yearsOfExperience": (index * 3) % 13,
                "currentCompany": f"Example Labs {index % 7 + 1}",
                "currentJobTitle": f"{skills[0]} {skills[1]} Engineer",
                "resumeUrl": f"https://example.com/resumes/candidate{index:03d}",
                "linkedinUrl": f"https://example.com/profiles/candidate{index:03d}",
                "githubUrl": f"https://example.com/github/candidate{index:03d}",
                "skills": skills,
            },
            expected=(201,),
        )
        self.report.increment("created", "candidates")
        return str(response["id"])

    def generate_candidates(self, users: list[SyntheticUser]) -> list[tuple[SyntheticUser, str]]:
        candidates: list[tuple[SyntheticUser, str]] = []
        for index, user in enumerate(users, start=1):
            candidates.append((user, self.ensure_candidate(user, index)))
            self._pause_after_batch(index)
        return candidates

    def ensure_job(self, user: SyntheticUser, index: int) -> str:
        token = self.login(user.email, user.password)
        existing = self.job.request(
            "GET", f"/api/jobs/recruiter/{user.id}", token=token
        )
        marker = f"Seed {self.config.random_seed} Job {index:03d}"
        for job in existing:
            if job.get("title") == marker:
                self.report.increment("existing", "jobs")
                return str(job["id"])
        family, required, preferred = JOB_FAMILIES[(index - 1) % len(JOB_FAMILIES)]
        response = self.job.request(
            "POST",
            "/api/jobs",
            token=token,
            body={
                "recruiterId": user.id,
                "title": marker,
                "description": (
                    f"{family} role in a synthetic company. Build, test, and operate "
                    f"services using {required} with a focus on maintainability."
                ),
                "location": ("Remote", "Berlin", "Toronto", "Singapore")[index % 4],
                "employmentType": ("FULL_TIME", "PART_TIME", "CONTRACT")[index % 3],
                "experienceMin": index % 6,
                "experienceMax": (index % 6) + 5,
                "salaryMin": 60000 + (index % 5) * 5000,
                "salaryMax": 100000 + (index % 5) * 5000,
                "status": "OPEN",
                "skills": [required, preferred],
            },
            expected=(201,),
        )
        self.report.increment("created", "jobs")
        return str(response["id"])

    def generate_jobs(
        self, recruiters: list[SyntheticUser]
    ) -> list[tuple[SyntheticUser, str]]:
        jobs: list[tuple[SyntheticUser, str]] = []
        if not recruiters and self.config.job_count:
            raise SeedSafetyError("At least one recruiter is required to generate jobs.")
        for index in range(1, self.config.job_count + 1):
            owner = recruiters[(index - 1) % len(recruiters)]
            jobs.append((owner, self.ensure_job(owner, index)))
            self._pause_after_batch(index)
        return jobs


def build_config(environment: dict[str, str] | None = None) -> SeedConfig:
    values = os.environ if environment is None else environment

    def integer(name: str, default: int) -> int:
        return int(values.get(name, default))

    return SeedConfig(
        auth_url=values.get("SEED_AUTH_URL", "http://localhost:8081"),
        candidate_url=values.get("SEED_CANDIDATE_URL", "http://localhost:8082"),
        job_url=values.get("SEED_JOB_URL", "http://localhost:8083"),
        application_url=values.get("SEED_APPLICATION_URL", "http://localhost:8084"),
        profile=values.get("SPRING_PROFILES_ACTIVE", values.get("SEED_PROFILE", "dev")),
        environment=values.get("SEED_ENVIRONMENT", "development"),
        enabled=values.get("SEED_ENABLED", "").lower() == "true",
        random_seed=integer("SEED_RANDOM_SEED", 2801),
        candidate_count=integer("SEED_CANDIDATE_COUNT", 100),
        recruiter_count=integer("SEED_RECRUITER_COUNT", 15),
        administrator_count=integer("SEED_ADMINISTRATOR_COUNT", 2),
        job_count=integer("SEED_JOB_COUNT", 100),
        application_count=integer("SEED_APPLICATION_COUNT", 200),
        batch_size=integer("SEED_BATCH_SIZE", 10),
        request_timeout=float(values.get("SEED_REQUEST_TIMEOUT", 10.0)),
        retries=integer("SEED_RETRIES", 2),
        admin_email=values.get("SEED_ADMIN_EMAIL", ""),
        admin_password=values.get("SEED_ADMIN_PASSWORD", ""),
        synthetic_password=values.get("SEED_SYNTHETIC_PASSWORD", DEFAULT_PASSWORD),
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="confirm this seed run")
    parser.add_argument("--report-file", help="write the JSON report to this path")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    config = build_config()
    try:
        config.validate(args.execute)
    except SeedSafetyError as error:
        print(f"SEED REFUSED: {error}", file=sys.stderr)
        return 2
    print("Seed configuration accepted; generation workflow is not yet enabled.")
    if args.report_file:
        with open(args.report_file, "w", encoding="utf-8") as report_file:
            json.dump(SeedReport().as_dict(), report_file, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
