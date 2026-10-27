import unittest

from seed_runner import (
    APPLICATION_STATUSES,
    SeedConfig,
    SeedSafetyError,
    SeedRunner,
    SyntheticUser,
    build_config,
)


class SeedSafetyTest(unittest.TestCase):
    def test_disabled_by_default(self):
        config = SeedConfig(admin_email="admin@example.com", admin_password="password")
        with self.assertRaises(SeedSafetyError):
            config.validate(execute=False)

    def test_production_is_rejected_even_when_enabled(self):
        config = SeedConfig(
            enabled=True,
            profile="dev",
            environment="production",
            admin_email="admin@example.com",
            admin_password="password",
        )
        with self.assertRaises(SeedSafetyError):
            config.validate(execute=True)

    def test_configurable_counts_are_read(self):
        config = build_config(
            {
                "SEED_ENABLED": "true",
                "SEED_ADMIN_EMAIL": "admin@example.com",
                "SEED_ADMIN_PASSWORD": "password",
                "SEED_RANDOM_SEED": "42",
                "SEED_BATCH_SIZE": "7",
            }
        )
        self.assertEqual(config.random_seed, 42)
        self.assertEqual(config.batch_size, 7)

    def test_random_seed_is_reproducible(self):
        first = SeedRunner(
            SeedConfig(random_seed=123, admin_email="a@example.com", admin_password="password")
        ).random
        second = SeedRunner(
            SeedConfig(random_seed=123, admin_email="a@example.com", admin_password="password")
        ).random
        self.assertEqual([first.random() for _ in range(5)], [second.random() for _ in range(5)])

    def test_application_pairs_are_deterministic_and_unique_for_default_counts(self):
        config = SeedConfig(
            random_seed=123,
            candidate_count=100,
            job_count=100,
            application_count=200,
            admin_email="a@example.com",
            admin_password="password",
        )
        runner = SeedRunner(config)
        candidates = [
            (SyntheticUser(str(index), f"candidate{index}@example.com", "CANDIDATE", "password"), str(index))
            for index in range(100)
        ]
        jobs = [
            (SyntheticUser(str(index), f"recruiter{index}@example.com", "RECRUITER", "password"), str(index))
            for index in range(100)
        ]
        first = [runner._application_pair(index, candidates, jobs) for index in range(1, 201)]
        second = [runner._application_pair(index, candidates, jobs) for index in range(1, 201)]
        self.assertEqual(first, second)
        self.assertEqual(len(set(first)), 200)

    def test_application_statuses_are_supported(self):
        self.assertEqual(
            set(APPLICATION_STATUSES),
            {"APPLIED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW", "REJECTED", "HIRED"},
        )

    def test_invalid_batch_size_and_application_dependencies_are_rejected(self):
        with self.assertRaises(SeedSafetyError):
            SeedConfig(
                enabled=True,
                profile="dev",
                admin_email="admin@example.com",
                admin_password="password",
                batch_size=0,
            ).validate(execute=True)
        with self.assertRaises(SeedSafetyError):
            SeedConfig(
                enabled=True,
                profile="dev",
                admin_email="admin@example.com",
                admin_password="password",
                candidate_count=1,
                job_count=1,
                application_count=2,
            ).validate(execute=True)
        with self.assertRaises(SeedSafetyError):
            SeedConfig(
                enabled=True,
                profile="dev",
                admin_email="admin@example.com",
                admin_password="password",
                candidate_count=0,
                application_count=1,
            ).validate(execute=True)

    def test_main_does_not_run_without_explicit_execution(self):
        from seed_runner import main

        self.assertEqual(main([]), 2)


if __name__ == "__main__":
    unittest.main()
