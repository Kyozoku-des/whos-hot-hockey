"""Exercise the shipped nginx config against a local mock API (no database).

Run: python3 -m unittest discover -s frontend/tests -v
Requires nginx with http_realip_module; NGINX_BINARY can select a binary.
"""

import concurrent.futures
import contextlib
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest


FRONTEND = Path(__file__).resolve().parents[1]
NGINX = os.environ.get("NGINX_BINARY") or shutil.which("nginx")
# Enough requests that refill (10 r/s) cannot absorb the 50-request burst
# unless a burst takes over 15 seconds, which keeps slow machines deterministic.
BURST_REQUESTS = 200


def can_bind(address):
    with socket.socket() as probe:
        try:
            probe.bind((address, 0))
        except OSError:
            return False
    return True


class MockApi(BaseHTTPRequestHandler):
    calls = []
    lock = threading.Lock()

    def do_GET(self):
        addresses = [self.headers.get("X-Real-IP"), self.headers.get("X-Forwarded-For")]
        with self.lock:
            self.calls.append(addresses)
        body = "|".join(addresses).encode()
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


@contextlib.contextmanager
def running_nginx(trusted_proxy, logs=None):
    with tempfile.TemporaryDirectory(prefix="nhl-nginx-") as directory:
        root = Path(directory)
        # Public files live in their own web root, as in the image; server config,
        # logs and temp files stay outside it.
        html = root / "html"
        html.mkdir()
        (html / "index.html").write_text("SPA")
        (html / "app.js").write_text("asset")
        # Use the same generators as Vite, without needing a bundled frontend.
        subprocess.run(
            ["node", "--input-type=module", "-e",
             "import {createSitemap,createSpaRoutes,createRobots} from './scripts/search-assets.js';"
             "import {writeFileSync} from 'node:fs';"
             "writeFileSync(process.argv[1]+'/sitemap.xml',createSitemap());"
             "writeFileSync(process.argv[1]+'/robots.txt',createRobots());"
             "writeFileSync(process.argv[2]+'/spa-routes.conf',createSpaRoutes());", str(html), str(root)],
            cwd=FRONTEND, capture_output=True, text=True, check=True)
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        api = ThreadingHTTPServer(("127.0.0.1", 0), MockApi)
        thread = threading.Thread(target=api.serve_forever, daemon=True)
        config = (FRONTEND / "nginx.conf").read_text()
        config = config.replace("listen 3000;", f"listen 127.0.0.1:{port};")
        config = config.replace("/usr/share/nginx/html", str(html))
        config = config.replace("/var/log/nginx/error.log", str(root / "error.log"))
        config = config.replace("backend-api:8080", f"127.0.0.1:{api.server_port}")
        config = config.replace("/etc/nginx/spa-routes.conf", str(root / "spa-routes.conf"))
        config = config.replace("/etc/nginx/security-headers.conf",
                                str(FRONTEND / "nginx/security-headers.conf"))
        realip = (FRONTEND / "nginx/realip.conf.template").read_text()
        realip = realip.replace("${CADDY_TRUSTED_PROXY}", trusted_proxy)
        # Single-process mode avoids privileged worker user changes in containers.
        main = root / "nginx.conf"
        user = ""
        if os.geteuid() == 0:
            import grp, pwd  # POSIX-only; imported lazily so discovery works elsewhere.
            user = f"user {pwd.getpwuid(os.getuid()).pw_name} {grp.getgrgid(os.getgid()).gr_name};\n"
        main.write_text(
            user +
            f"daemon off; master_process off; pid {root}/nginx.pid;\n"
            f"error_log {root}/error.log warn; events {{}}\n"
            f"http {{ access_log {root}/access.log combined; client_body_temp_path {root}/client; "
            f"proxy_temp_path {root}/proxy; fastcgi_temp_path {root}/fastcgi; "
            f"uwsgi_temp_path {root}/uwsgi; scgi_temp_path {root}/scgi;\n"
            f"{realip}\n{config}\n}}\n"
        )
        check = subprocess.run([NGINX, "-t", "-p", directory, "-c", str(main)],
                               capture_output=True, text=True)
        if check.returncode:
            api.server_close()
            raise RuntimeError(check.stderr)
        thread.start()
        process = subprocess.Popen([NGINX, "-p", directory, "-c", str(main)],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        try:
            for _ in range(100):
                if process.poll() is not None:
                    raise RuntimeError(process.stderr.read().decode())
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                        break
                except OSError:
                    time.sleep(0.02)
            else:
                raise RuntimeError("nginx did not start")
            yield port
        finally:
            process.terminate()
            process.communicate(timeout=5)
            if logs is not None:
                logs.append("\n".join(path.read_text() for path in root.glob("*.log")))
            api.shutdown()
            api.server_close()
            thread.join()


def request(port, path="/api/players", forwarded="203.0.113.1", source="127.0.0.1"):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5,
                                            source_address=(source, 0))
    try:
        connection.request("GET", path, headers={"X-Forwarded-For": forwarded,
                                                "X-Real-IP": "192.0.2.99"})
        response = connection.getresponse()
        return response.status, response.read().decode()
    finally:
        connection.close()


@unittest.skipIf(os.name == "nt", "nginx integration tests require a POSIX host")
class RateLimitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not NGINX:
            raise RuntimeError("Install nginx or set NGINX_BINARY to run these integration tests")

    def burst(self, port, **kwargs):
        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
            return list(pool.map(lambda _: request(port, **kwargs), range(BURST_REQUESTS)))

    def assert_limited(self, responses):
        statuses = [status for status, _ in responses]
        self.assertIn(200, statuses)
        self.assertIn(429, statuses)
        self.assertEqual(set(statuses), {200, 429})

    def test_trusted_proxy_limits_each_client_and_recovers(self):
        with running_nginx("127.0.0.1") as port:
            with MockApi.lock:
                MockApi.calls.clear()
            responses = self.burst(port, forwarded="192.0.2.99, 203.0.113.1")
            self.assert_limited(responses)
            # Rejections must never reach the backend; the forged prefix and
            # X-Real-IP must never be forwarded as the verified address.
            with MockApi.lock:
                self.assertEqual(len(MockApi.calls), sum(s == 200 for s, _ in responses))
                self.assertTrue(all(c == ["203.0.113.1"] * 2 for c in MockApi.calls))
            self.assertEqual(request(port, forwarded="203.0.113.2"),
                             (200, "203.0.113.2|203.0.113.2"))
            self.assertEqual(request(port, forwarded="2001:db8::1"),
                             (200, "2001:db8::1|2001:db8::1"))
            time.sleep(0.2)
            self.assertEqual(request(port)[0], 200)

    def test_untrusted_peer_cannot_rotate_forwarded_ips(self):
        with running_nginx("127.0.0.1") as port:
            with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
                responses = list(pool.map(
                    lambda i: request(port, source="127.0.0.2", forwarded=f"203.0.113.{i+1}"),
                    range(BURST_REQUESTS)))
            self.assert_limited(responses)
            self.assertTrue(all(body == "127.0.0.2|127.0.0.2"
                                for status, body in responses if status == 200))

    def test_default_trust_ignores_headers_and_static_routes_are_limited(self):
        with running_nginx("unix:") as port:
            self.assertEqual(request(port), (200, "127.0.0.1|127.0.0.1"))
            self.assert_limited(self.burst(port))
            for path in ("/", "/player/123", "/app.js"):
                time.sleep(0.2)
                statuses = [status for status, _ in self.burst(port, path=path)]
                self.assertIn(429, statuses)
                self.assertLessEqual(set(statuses), {200, 429})

    def test_ipv6_addresses_in_one_prefix_share_a_budget(self):
        # Include compressed forms and a 0x0a byte: grouping must use address
        # bytes, not the spelling of the forwarded IPv6 string or '.' regex rules.
        for prefix in ("2001:db8::", "2001:db8:a00:1234:"):
            with self.subTest(prefix=prefix), running_nginx("127.0.0.1") as port:
                with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
                    responses = list(pool.map(
                        lambda i: request(port, forwarded=prefix + (":" if prefix.endswith("1234:") else "") + format(i + 1, "x")),
                        range(BURST_REQUESTS)))
                self.assert_limited(responses)
                self.assertEqual(request(port, forwarded="2001:db8:ffff::1")[0], 200)

    def test_distributed_api_requests_share_an_aggregate_budget(self):
        with running_nginx("127.0.0.1") as port:
            with concurrent.futures.ThreadPoolExecutor(max_workers=30) as pool:
                responses = list(pool.map(
                    lambda i: request(port, forwarded=f"198.18.{i // 250}.{i % 250 + 1}"),
                    range(600)))
            self.assert_limited(responses)

    def test_request_text_is_absent_from_nginx_access_and_rejection_logs(self):
        marker = "UNTRUSTED_REQUEST_LOG_MARKER"
        logs = []
        with running_nginx("unix:", logs=logs) as port:
            self.assert_limited(self.burst(port, path=f"/api/{marker}?input={marker}"))
        self.assertTrue(logs)
        self.assertNotIn(marker, "\n".join(logs))

    def test_security_headers_on_every_route_and_no_version(self):
        with running_nginx("unix:") as port:
            for path in ("/", "/player/123", "/app.js", "/missing.js", "/api/players"):
                with self.subTest(path=path):
                    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
                    try:
                        connection.request("GET", path)
                        response = connection.getresponse()
                        body = response.read().decode()
                    finally:
                        connection.close()
                    self.assertIn("frame-ancestors 'none'",
                                  response.getheader("Content-Security-Policy", ""))
                    self.assertEqual(response.getheader("X-Content-Type-Options"), "nosniff")
                    self.assertEqual(response.getheader("Server"), "nginx")
                    self.assertNotRegex(body, r"nginx/\d")

    def test_crawl_routes_and_unknown_urls(self):
        with running_nginx("unix:") as port:
            for path in ("/", "/player/8478402", "/team/BOS", "/team/bos", "/TEAM/bos/", "/player/any-id", "/tips", "/settings"):
                self.assertEqual(request(port, path=path), (200, "SPA"), path)
            for path in ("/not-a-page", "/player", "/team/BOS/extra", "/spa-routes.conf"):
                self.assertEqual(request(port, path=path), (404, "SPA"), path)
            for path in ("/team/logo.png", "/player/x.js", "/team/missing.css"):
                status, body = request(port, path=path)
                self.assertEqual(status, 404, path)
                self.assertNotEqual(body, "SPA", path)
            status, robots = request(port, path="/robots.txt")
            self.assertEqual(status, 200)
            self.assertIn("Sitemap: https://whoshothockey.com/sitemap.xml", robots)
            status, sitemap = request(port, path="/sitemap.xml")
            self.assertEqual(status, 200)
            self.assertIn("<loc>https://whoshothockey.com/team/BOS</loc>", sitemap)

    def test_api_with_asset_extension_cannot_bypass_limit(self):
        for path in ("/api/players.json", "/api/players.js"):
            with self.subTest(path=path), running_nginx("unix:") as port:
                self.assert_limited(self.burst(port, path=path))


if __name__ == "__main__":
    unittest.main()
