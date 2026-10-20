"""Build per-image Trivy ignore files from Trivy JSON reports (issue #58).

    python .github/scripts/trivyignore.py <reports dir> security/trivyignore <YYYY-MM-DD>

The reports are the security workflow's "security-image-reports" artifact from a run
without ignore files. Each image gets its own policy, named after its report, so an
exception can never apply to another image; images without a policy (the application
runtime images) get none. Every entry is pinned to the exact package version (purl) and
location (path) Trivy reported.

Only the findings described in RULES can be accepted. Anything else, such as a package
in a different location, a Debian package in postgres that has a fix, or any finding in
an application runtime image, stops the script: fix it instead of accepting it.
"""
import collections
import glob
import json
import os
import re
import sys

NPM_BUNDLED = re.compile(
    r"usr/local/lib/node_modules/npm/node_modules/"
    r"(?:(?:@[^/]+/)?[^/]+/node_modules/)*(?:@[^/]+/)?[^/]+/package\.json")
TESTCONTAINERS_SHADED = re.compile(
    r"root/\.m2/repository/com/github/docker-java/docker-java-transport-zerodep/[^/]+/"
    r"docker-java-transport-zerodep-[^/]+\.jar")

STATEMENTS = {
    "postgres-os": (
        "postgres:17 (Debian) base of whos-hot-hockey-postgres: no Debian fix released yet. "
        "PostgreSQL is reachable only from the Compose network and the host's loopback; these "
        "are OS libraries and tools, not the server itself."),
    "alloy": (
        "grafana/alloy:v1.21.0-distroless is the newest release. Alloy listens on loopback "
        "and the Compose networks only and pushes outbound to Grafana Cloud. Fixed in an "
        "upstream release."),
    "frontend-build-npm": (
        "Build stage only, never deployed: npm's own bundled dependencies, at the npm "
        "version pinned by frontend/package.json's packageManager field. They run once "
        "in CI on this repository's lockfile."),
    "backend-build-testcontainers": (
        "Build stage only, never deployed: HttpComponents shaded into Testcontainers 2.0.5's "
        "docker-java transport (newest release). Test scope; talks only to the local Docker "
        "socket."),
}


def _os(image_ref, distro, category, require_unfixed):
    target = re.compile(re.escape(image_ref) + r" \(" + distro + r" [0-9.]+\)")

    def rule(result, vuln):
        if result.get("Class") != "os-pkgs" or result.get("Type") != distro:
            return None
        if not target.fullmatch(result.get("Target", "")):
            return None
        if require_unfixed and vuln.get("FixedVersion"):
            return None
        return category, result["Target"]
    return rule


def _binary(path, category):
    def rule(result, vuln):
        if result.get("Type") == "gobinary" and result.get("Target") == path:
            return category, path
        return None
    return rule


def _package(pkg_type, path_pattern, name_prefix, category):
    def rule(result, vuln):
        path = vuln.get("PkgPath") or ""
        if (result.get("Type") == pkg_type and path_pattern.fullmatch(path)
                and vuln.get("PkgName", "").startswith(name_prefix)):
            return category, path
        return None
    return rule


# Report name (image reference with "/" and ":" replaced by "_") -> accepted findings.
RULES = {
    # Built from postgres:17 without gosu (infra/database/Dockerfile): Debian packages only.
    "ghcr.io_kyozoku-des_whos-hot-hockey-postgres_local": [
        _os("ghcr.io/kyozoku-des/whos-hot-hockey-postgres:local", "debian", "postgres-os", require_unfixed=True),
    ],
    "grafana_alloy_v1.21.0-distroless": [
        _os("grafana/alloy:v1.21.0-distroless", "ubuntu", "alloy", require_unfixed=False),
        _binary("usr/bin/alloy", "alloy"),
    ],
    "scan_frontend-build": [
        _package("node-pkg", NPM_BUNDLED, "", "frontend-build-npm"),
    ],
    "scan_api-build": [
        _package("jar", TESTCONTAINERS_SHADED, "org.apache.httpcomponents.",
                 "backend-build-testcontainers"),
    ],
    "scan_datajob-build": [
        _package("jar", TESTCONTAINERS_SHADED, "org.apache.httpcomponents.",
                 "backend-build-testcontainers"),
    ],
}


def classify(image, result, vuln):
    """Return (category, path) for an acceptable finding, or None."""
    for rule in RULES.get(image, []):
        match = rule(result, vuln)
        if match:
            return match
    return None


def build_policies(reports):
    """Map image -> {(category, id): (purls, paths)}; raise ValueError on any other finding."""
    policies = collections.defaultdict(lambda: collections.defaultdict(lambda: (set(), set())))
    rejected = []
    for image, report in reports.items():
        for result in report.get("Results", []):
            for vuln in result.get("Vulnerabilities") or []:
                match = classify(image, result, vuln)
                purl = vuln.get("PkgIdentifier", {}).get("PURL")
                if not match or not purl:
                    rejected.append(f"{image}: {vuln['VulnerabilityID']} {vuln.get('PkgName')} "
                                    f"{vuln.get('InstalledVersion')} at "
                                    f"{vuln.get('PkgPath') or result.get('Target')}")
                    continue
                category, path = match
                purls, paths = policies[image][(category, vuln["VulnerabilityID"])]
                purls.add(purl)
                paths.add(path)
    if rejected:
        raise ValueError("findings outside the accepted categories; fix them instead:\n  "
                         + "\n  ".join(rejected))
    return policies


def _quote(value):
    return json.dumps(value)  # a JSON string is a valid double-quoted YAML scalar


def render(image, entries, expires):
    lines = [
        f"# Accepted Trivy findings for {image} only (issue #58). Each entry matches the exact",
        "# package version and location listed and expires on the date given. Generated by",
        "# .github/scripts/trivyignore.py; regenerate rather than editing by hand.",
        "vulnerabilities:",
    ]
    for (category, vuln_id), (purls, paths) in sorted(entries.items()):
        lines.append(f"  - id: {vuln_id}")
        lines.append("    paths:")
        lines += [f"      - {_quote(p)}" for p in sorted(paths)]
        lines.append("    purls:")
        lines += [f"      - {_quote(p)}" for p in sorted(purls)]
        lines.append(f"    statement: {_quote(STATEMENTS[category])}")
        lines.append(f"    expired_at: {expires}")
    return "\n".join(lines) + "\n"


def main(reports_dir, out_dir, expires):
    reports = {os.path.basename(f)[:-5]: json.load(open(f, encoding="utf-8"))
               for f in sorted(glob.glob(os.path.join(reports_dir, "*.json")))}
    try:
        policies = build_policies(reports)
    except ValueError as error:
        sys.exit(str(error))
    os.makedirs(out_dir, exist_ok=True)
    for stale in glob.glob(os.path.join(out_dir, "*.yaml")):
        os.remove(stale)
    for image, entries in sorted(policies.items()):
        with open(os.path.join(out_dir, image + ".yaml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(render(image, entries, expires))
        print(f"{image}: {len(entries)} entries")


if __name__ == "__main__":
    main(*sys.argv[1:4])
