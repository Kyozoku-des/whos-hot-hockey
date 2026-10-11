"""Exercise the actual embedded deploy preflight with synthetic Compose output."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import unittest


SCRIPT = (Path(__file__).resolve().parents[1] / "files/whoshot-deploy").read_text()
PREFLIGHT = SCRIPT.split("| python3 -c '\n", 1)[1].split("\n' || die", 1)[0]


class DeployPreflightTest(unittest.TestCase):
    def settings(self, new=True):
        settings = {"services": {
            "postgres": {"environment": {"POSTGRES_PASSWORD": "fixture-admin-password"}},
            "backend-api": {"environment": {"SPRING_DATASOURCE_PASSWORD": "fixture-api-password-long"}},
            "data-job": {"environment": {"SPRING_DATASOURCE_PASSWORD": "fixture-job-password-long"}},
            "caddy": {"environment": {"SITE_DOMAIN": "example.test"}},
        }}
        if new:
            settings["services"]["db-roles"] = {}
        return settings

    def run_preflight(self, settings):
        result = subprocess.run([sys.executable, "-c", PREFLIGHT], input=json.dumps(settings),
                                capture_output=True, text=True)
        for service in settings["services"].values():
            for key, value in service.get("environment", {}).items():
                if "PASSWORD" in key and value and value != "whoshot_local":
                    self.assertNotIn(value, result.stdout + result.stderr)
        return result.returncode

    def test_accepts_distinct_new_credentials_and_legacy_rollback(self):
        self.assertEqual(self.run_preflight(self.settings()), 0)
        legacy = self.settings(new=False)
        for name in ("backend-api", "data-job"):
            legacy["services"][name]["environment"]["SPRING_DATASOURCE_PASSWORD"] = "fixture-admin-password"
        self.assertEqual(self.run_preflight(legacy), 0)

    def test_rejects_defaults_short_and_reused_passwords(self):
        for service in ("backend-api", "data-job"):
            for password in ("", "short", "whoshot_local", "whoshot_api_local", "whoshot_ingest_local",
                             "fixture-admin-password", "fixture-job-password-long" if service == "backend-api" else "fixture-api-password-long"):
                settings = copy.deepcopy(self.settings())
                settings["services"][service]["environment"]["SPRING_DATASOURCE_PASSWORD"] = password
                with self.subTest(service=service, password_case=len(password)):
                    self.assertNotEqual(self.run_preflight(settings), 0)
