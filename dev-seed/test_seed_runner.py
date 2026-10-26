import unittest

from seed_runner import SeedConfig, SeedSafetyError, build_config


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


if __name__ == "__main__":
    unittest.main()
