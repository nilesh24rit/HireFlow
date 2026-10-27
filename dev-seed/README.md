# HireFlow development seed runner

The runner uses only the existing authenticated service APIs. It is not a production
microservice and never connects directly to a service database.

It is disabled unless both conditions are met:

1. `SEED_ENABLED=true`
2. the command includes `--execute`

The active profile must be `dev` or `test`, and `SEED_ENVIRONMENT` must not be
`production`. The runner requires an already provisioned administrator account:

```powershell
$env:SEED_ENABLED = "true"
$env:SEED_ADMIN_EMAIL = "admin@example.com"
$env:SEED_ADMIN_PASSWORD = "<local development password>"
python dev-seed\seed_runner.py --execute
```

Synthetic accounts use the reserved `example.com` domain and the development-only
password configured by `SEED_SYNTHETIC_PASSWORD`. Credentials are never included in
the report. The runner creates candidates, recruiters, administrators, candidate
profiles, open jobs, and applications through the authenticated service APIs. New
applications are created as `APPLIED` and moved through the supported recruiter
status transitions to provide lifecycle fixtures.

Runs are idempotent: users are looked up by their stable synthetic email, profiles
by owning user, jobs by their deterministic title marker, and applications by the
candidate/job pair. A JSON verification report can be saved without exposing
credentials:

```powershell
python dev-seed\seed_runner.py --execute --report-file seed-report.json
```

The runner does not connect to service databases, publish synthetic Kafka records,
or call paid AI providers. Outbox, notification, and analytics results should be
checked in the running development stack after asynchronous consumers settle.
