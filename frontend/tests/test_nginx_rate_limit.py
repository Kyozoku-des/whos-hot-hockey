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
import pwd
import grp
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest


FRONTEND = Path(__file__).resolve().parents[1]
NGINX = os.environ.get("NGINX_BINARY") or shutil.which("nginx")


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
def running_nginx(trusted_proxy):
    with tempfile.TemporaryDirectory(prefix="nhl-nginx-") as directory:
        root = Path(directory)
        (root / "index.html").write_text("SPA")
        (root / "app.js").write_text("asset")
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        api = ThreadingHTTPServer(("127.0.0.1", 0), MockApi)
        thread = threading.Thread(target=api.serve_forever, daemon=True)
        config = (FRONTEND / "nginx.conf").read_text()
        config = config.replace("listen 3000;", f"listen 127.0.0.1:{port};")
        config = config.replace("/usr/share/nginx/html", str(root))
        config = config.replace("backend-api:8080", f"127.0.0.1:{api.server_port}")
        realip = (FRONTEND / "nginx/realip.conf.template").read_text()
        realip = realip.replace("${CADDY_TRUSTED_PROXY}", trusted_proxy)
        # Single-process mode avoids privileged worker user changes in containers.
        main = root / "nginx.conf"
        user = ""
        if os.geteuid() == 0:
            user = f"user {pwd.getpwuid(os.getuid()).pw_name} {grp.getgrgid(os.getgid()).gr_name};\n"
        main.write_text(
            user +
            f"daemon off; master_process off; pid {root}/nginx.pid;\n"
            f"error_log {root}/error.log warn; events {{}}\n"
            f"http {{ access_log off; client_body_temp_path {root}/client; "
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


class RateLimitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not NGINX:
            raise RuntimeError("Install nginx or set NGINX_BINARY to run these integration tests")

    def burst(self, port, **kwargs):
        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
            return list(pool.map(lambda _: request(port, **kwargs), range(100)))

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
                    range(100)))
            self.assert_limited(responses)
            self.assertTrue(all(body == "127.0.0.2|127.0.0.2"
                                for status, body in responses if status == 200))

    def test_default_trust_ignores_headers_and_static_routes_are_unlimited(self):
        with running_nginx("unix:") as port:
            self.assertEqual(request(port), (200, "127.0.0.1|127.0.0.1"))
            self.assert_limited(self.burst(port))
            for path in ("/", "/players/123", "/app.js"):
                self.assertTrue(all(status == 200 for status, _ in self.burst(port, path=path)))

    def test_api_with_asset_extension_cannot_bypass_limit(self):
        for path in ("/api/players.json", "/api/players.js"):
            with self.subTest(path=path), running_nginx("unix:") as port:
                self.assert_limited(self.burst(port, path=path))


if __name__ == "__main__":
    unittest.main()
