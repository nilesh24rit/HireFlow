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
the report.
