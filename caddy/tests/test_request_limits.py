"""Exercise the public HTTP redirect's slow-header boundary with the shipped Caddy image."""
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest
import uuid


class PublicHttpLimitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix="whoshot-http-limits-")
        cls.addClassCleanup(cls.directory.cleanup)
        cls.name = "whoshot-http-limits-" + uuid.uuid4().hex[:12]
        cls.image = os.environ.get("CADDY_TEST_IMAGE", "ghcr.io/kyozoku-des/whos-hot-nhl-caddy:local")
        config = (Path(__file__).resolve().parents[1] / "Caddyfile").read_text()
        # Local CA only: no public DNS, ACME request, or host certificate installation.
        config = config.replace("{\n\t# The admin", "{\n\tlocal_certs\n\tskip_install_trust\n\t# The admin")
        config = config.replace("{$SITE_DOMAIN}", "security58.test")
        config = config.replace("reverse_proxy frontend:3000", 'respond "fixture" 200')
        path = Path(cls.directory.name) / "Caddyfile"
        path.write_text(config)
        cls.addClassCleanup(lambda: subprocess.run(
            ["docker", "rm", "-f", cls.name], capture_output=True))
        cls.docker("run", "-d", "--name", cls.name, "-p", "127.0.0.1::80",
                   "-v", str(path) + ":/etc/caddy/Caddyfile:ro", cls.image)
        cls.port = int(cls.docker("port", cls.name, "80/tcp").rsplit(":", 1)[1])
        for attempt in range(50):
            try:
                with socket.create_connection(("127.0.0.1", cls.port), timeout=1):
                    return
            except OSError:
                if attempt == 49:
                    raise
                time.sleep(0.1)

    @staticmethod
    def docker(*args):
        return subprocess.run(["docker", *args], capture_output=True, text=True,
                              check=True).stdout.strip()

    def test_http_redirect_has_the_configured_deadlines(self):
        adapted = json.loads(self.docker("exec", self.name, "caddy", "adapt",
                                        "--config", "/etc/caddy/Caddyfile"))
        servers = list(adapted["apps"]["http"]["servers"].values())
        http = [server for server in servers if ":80" in server["listen"]]
        self.assertEqual(len(http), 1, "HTTP must exist during adaptation to inherit server limits")
        self.assertEqual(http[0]["read_header_timeout"], 10_000_000_000)
        self.assertEqual(http[0]["read_timeout"], 10_000_000_000)
        self.assertEqual(http[0]["write_timeout"], 30_000_000_000)
        self.assertEqual(http[0]["idle_timeout"], 120_000_000_000)

    def test_incomplete_http_headers_are_closed_and_redirect_recovers(self):
        with socket.create_connection(("127.0.0.1", self.port), timeout=13) as connection:
            connection.sendall(b"GET / HTTP/1.1\r\nHost: security58.test\r\nX-Slow: ")
            started = time.monotonic()
            try:
                response = connection.recv(4096)
            except ConnectionResetError:
                response = b""
            self.assertLess(time.monotonic() - started, 13)
            self.assertTrue(not response or b" 408 " in response, response)
        with socket.create_connection(("127.0.0.1", self.port), timeout=3) as connection:
            connection.sendall(b"GET /players?season=20262027 HTTP/1.1\r\n"
                               b"Host: security58.test\r\nConnection: close\r\n\r\n")
            response = connection.recv(4096)
            self.assertIn(b" 308 ", response)
            self.assertIn(b"Location: https://security58.test/players?season=20262027", response)


if __name__ == "__main__":
    unittest.main()
