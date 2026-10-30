"""Exception boundaries of trivyignore.py.

Run: python -m unittest discover -s .github/scripts -v
"""
import unittest

import trivyignore

NPM_UNDICI = "usr/local/lib/node_modules/npm/node_modules/undici/package.json"
POSTGRES_REF = "ghcr.io/kyozoku-des/whos-hot-hockey-postgres:local"
POSTGRES = "ghcr.io_kyozoku-des_whos-hot-hockey-postgres_local"
CADDY = "ghcr.io_kyozoku-des_whos-hot-hockey-caddy_local"
GO_JOSE_V3 = "github.com/go-jose/go-jose/v3"
DOCKER_JAVA = ("root/.m2/repository/com/github/docker-java/docker-java-transport-zerodep/3.7.1/"
               "docker-java-transport-zerodep-3.7.1.jar")


def os_result(target, distro, fixed=""):
    return {"Class": "os-pkgs", "Type": distro, "Target": target,
            "Vulnerabilities": [vuln("CVE-2026-0001", "libxml2", fixed=fixed)]}


def lang_result(pkg_type, target, name, path="", fixed=""):
    return {"Class": "lang-pkgs", "Type": pkg_type, "Target": target,
            "Vulnerabilities": [vuln("CVE-2026-0002", name, path=path, fixed=fixed)]}


def vuln(vuln_id, name, path="", fixed=""):
    return {"VulnerabilityID": vuln_id, "PkgName": name, "InstalledVersion": "1.0",
            "PkgPath": path, "FixedVersion": fixed,
            "PkgIdentifier": {"PURL": f"pkg:generic/{name}@1.0"}}


def policies(image, *results):
    return trivyignore.build_policies({image: {"Results": list(results)}})


class AcceptedFindings(unittest.TestCase):
    def test_each_category_is_scoped_to_its_image_and_location(self):
        cases = [
            (POSTGRES, os_result(f"{POSTGRES_REF} (debian 13.7)", "debian"),
             f"{POSTGRES_REF} (debian 13.7)"),
            ("grafana_alloy_v1.21.0-distroless",
             os_result("grafana/alloy:v1.21.0-distroless (ubuntu 24.04)", "ubuntu", fixed="3.1"),
             "grafana/alloy:v1.21.0-distroless (ubuntu 24.04)"),
            ("grafana_alloy_v1.21.0-distroless",
             lang_result("gobinary", "usr/bin/alloy", "stdlib"), "usr/bin/alloy"),
            ("scan_frontend-build", lang_result("node-pkg", "Node.js", "undici", NPM_UNDICI),
             NPM_UNDICI),
            ("scan_api-build", lang_result("jar", "Java", "org.apache.httpcomponents.core5:httpcore5",
                                           DOCKER_JAVA), DOCKER_JAVA),
            (CADDY, lang_result("gobinary", "usr/bin/caddy", GO_JOSE_V3), "usr/bin/caddy"),
            ("scan_caddy-build", lang_result("gobinary", "out/caddy", GO_JOSE_V3), "out/caddy"),
        ]
        for image, result, path in cases:
            with self.subTest(image=image, path=path):
                entries = policies(image, result)
                self.assertEqual(list(entries), [image])
                (purls, paths), = entries[image].values()
                self.assertEqual(paths, {path})

    def test_rendered_entries_carry_paths_and_purls(self):
        entries = policies("scan_frontend-build",
                           lang_result("node-pkg", "Node.js", "undici", NPM_UNDICI))
        text = trivyignore.render("scan_frontend-build", entries["scan_frontend-build"], "2026-11-30")
        self.assertIn(f'paths:\n      - "{NPM_UNDICI}"', text)
        self.assertIn('purls:\n      - "pkg:generic/undici@1.0"', text)
        self.assertIn("expired_at: 2026-11-30", text)
        self.assertIn("frontend/package.json's packageManager field", text)

    def test_caddy_go_jose_v3_expires_sooner_than_the_run_date(self):
        entries = policies(CADDY, lang_result("gobinary", "usr/bin/caddy", GO_JOSE_V3))
        text = trivyignore.render(CADDY, entries[CADDY], "2026-11-30")
        self.assertIn(f"expired_at: {trivyignore.CATEGORY_EXPIRES['caddy-go-jose-v3']}", text)
        self.assertNotIn("2026-11-30", text)
        earlier = trivyignore.render(CADDY, entries[CADDY], "2026-11-01")
        self.assertIn("expired_at: 2026-11-01", earlier)


class RejectedFindings(unittest.TestCase):
    def assert_rejected(self, image, result):
        with self.assertRaises(ValueError):
            policies(image, result)

    def test_esbuild_binary_in_frontend_build_stage(self):
        self.assert_rejected("scan_frontend-build", lang_result(
            "gobinary", "app/node_modules/@esbuild/linux-x64/bin/esbuild", "stdlib"))

    def test_application_package_in_frontend_build_stage(self):
        self.assert_rejected("scan_frontend-build", lang_result(
            "node-pkg", "Node.js", "vue", "app/node_modules/vue/package.json"))

    def test_alpine_package_in_frontend_build_stage(self):
        self.assert_rejected("scan_frontend-build", os_result(
            "scan/frontend-build (alpine 3.24.2)", "alpine", fixed="1.3.2-r1"))

    def test_any_go_binary_in_postgres(self):
        # gosu is removed from the image; a Go binary there must not be accepted again.
        self.assert_rejected(POSTGRES, lang_result("gobinary", "usr/local/bin/gosu", "stdlib"))

    def test_debian_package_with_a_fix_in_postgres(self):
        self.assert_rejected(POSTGRES, os_result(
            f"{POSTGRES_REF} (debian 13.7)", "debian", fixed="2.9.14-2.1+deb13u4"))

    def test_os_findings_of_another_image(self):
        self.assert_rejected(POSTGRES, os_result("postgres:17 (debian 13.7)", "debian"))

    def test_jar_outside_shaded_testcontainers_transport(self):
        self.assert_rejected("scan_api-build", lang_result(
            "jar", "Java", "org.apache.httpcomponents.client5:httpclient5",
            "app/httpclient5-5.5.1.jar"))

    def test_other_library_inside_shaded_transport(self):
        self.assert_rejected("scan_api-build", lang_result(
            "jar", "Java", "com.fasterxml.jackson.core:jackson-databind", DOCKER_JAVA))

    def test_go_jose_v3_with_a_fix_in_caddy(self):
        self.assert_rejected(CADDY, lang_result("gobinary", "usr/bin/caddy", GO_JOSE_V3, fixed="3.0.6"))

    def test_other_go_module_in_caddy(self):
        self.assert_rejected(CADDY, lang_result(
            "gobinary", "usr/bin/caddy", "github.com/go-jose/go-jose/v4"))
        self.assert_rejected(CADDY, lang_result("gobinary", "usr/bin/caddy", "stdlib"))

    def test_go_jose_v3_in_another_binary_or_image(self):
        self.assert_rejected(CADDY, lang_result("gobinary", "usr/bin/other", GO_JOSE_V3))
        self.assert_rejected("scan_caddy-build", lang_result("gobinary", "usr/bin/caddy", GO_JOSE_V3))
        self.assert_rejected("ghcr.io_kyozoku-des_whos-hot-hockey-frontend_local", lang_result(
            "gobinary", "usr/bin/caddy", GO_JOSE_V3))

    def test_alpine_package_in_caddy(self):
        self.assert_rejected(CADDY, os_result(
            "ghcr.io/kyozoku-des/whos-hot-hockey-caddy:local (alpine 3.23.7)", "alpine"))

    def test_any_finding_in_an_application_runtime_image(self):
        self.assert_rejected("ghcr.io_kyozoku-des_whos-hot-hockey-api_local", lang_result(
            "jar", "Java", "org.apache.httpcomponents.core5:httpcore5", DOCKER_JAVA))


if __name__ == "__main__":
    unittest.main()
