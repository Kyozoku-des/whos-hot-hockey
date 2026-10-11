"""Run the shipped logging filter with dummy traffic in a disposable Caddy container."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
import urllib.request
import uuid


class AccessLogTest(unittest.TestCase):
    def test_client_text_is_removed_before_access_logs_are_emitted(self):
        config = (Path(__file__).resolve().parents[1] / "Caddyfile").read_text()
        config = config.replace("{\n\t# The admin", "{\n\tauto_https off\n\t# The admin")
        config = config.replace("www.{$SITE_DOMAIN} {", "http://www.review.test:8080 {")
        config = config.replace("{$SITE_DOMAIN} {", "http://review.test:8080 {")
        config = config.replace("{$SITE_DOMAIN}", "review.test")
        config = config.replace("reverse_proxy frontend:3000", 'respond "fixture" 200')
        name = "whoshot-access-test-" + uuid.uuid4().hex[:12]
        image = os.environ.get("CADDY_TEST_IMAGE", "ghcr.io/kyozoku-des/whos-hot-nhl-caddy:local")
        marker = "UNTRUSTED_ACCESS_LOG_MARKER"

        def docker(*args):
            return subprocess.run(["docker", *args], capture_output=True, text=True, check=True).stdout.strip()

        with tempfile.TemporaryDirectory(prefix="whoshot-caddy-test-") as directory:
            path = Path(directory) / "Caddyfile"
            path.write_text(config)
            try:
                docker("run", "-d", "--name", name, "-p", "127.0.0.1::8080",
                       "-v", str(path) + ":/etc/caddy/Caddyfile:ro", image)
                port = docker("port", name, "8080/tcp").rsplit(":", 1)[1]
                for attempt in range(50):
                    try:
                        request = urllib.request.Request(
                            f"http://127.0.0.1:{port}/{marker}?q={marker}",
                            headers={"Host": "review.test:8080", "User-Agent": marker, "X-Test": marker})
                        with urllib.request.urlopen(request, timeout=2) as response:
                            self.assertEqual(response.status, 200)
                            response.read()
                        break
                    except OSError:
                        if attempt == 49:
                            raise
                        time.sleep(0.1)
                docker("stop", "--time", "2", name)
                result = subprocess.run(["docker", "logs", name], capture_output=True, text=True, check=True)
                output = result.stdout + "\n" + result.stderr
                self.assertNotIn(marker, output)
                rows = [json.loads(line) for line in output.splitlines() if line.startswith("{")]
                access = [row for row in rows if row.get("logger", "").startswith("http.log.access")]
                self.assertTrue(access, "the test must actually produce an access record")
                self.assertEqual(access[-1]["status"], 200)
                self.assertIn("duration", access[-1])
                self.assertNotIn("request", access[-1])
                self.assertNotIn("resp_headers", access[-1])
            finally:
                subprocess.run(["docker", "rm", "-f", name], capture_output=True)
