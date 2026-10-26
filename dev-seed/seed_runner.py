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
