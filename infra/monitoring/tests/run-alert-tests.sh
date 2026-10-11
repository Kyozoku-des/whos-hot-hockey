#!/usr/bin/env bash
# Unit tests for the alert rules: renders them with Terraform exactly as they are
# applied, converts them to a Prometheus rule file and runs the promtool scenarios in
# alerts.test.yml against it. Needs only Docker (and bash, e.g. Git Bash on Windows).
#
#   infra/monitoring/tests/run-alert-tests.sh
set -euo pipefail

TF_IMAGE=hashicorp/terraform:1.16.4
PROM_IMAGE=prom/prometheus:v3.5.0
PYTHON_IMAGE=python:3.13.13-alpine3.23

module_dir="$(cd "$(dirname "$0")/.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
# mktemp creates it owner-only; the Prometheus image runs as nobody and must read it.
chmod 755 "$work_dir"

# Docker on Windows (Git Bash) needs Windows paths and no MSYS path rewriting.
host_path() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
export MSYS_NO_PATHCONV=1

terraform() {
  docker run --rm -i -v "$(host_path "$module_dir"):/work" -w /work "$TF_IMAGE" "$@"
}

terraform init -backend=false -input=false >/dev/null

# Placeholder values: only the rule definitions are read. site_url is set so the
# uptime rule is included too.
echo 'jsonencode({ for name, rule in local.rules : name => { expr = rule.expr, datasource = rule.datasource, op = rule.op, threshold = rule.threshold, for = rule.for } })' |
  terraform console \
    -var grafana_url=https://example.grafana.net \
    -var grafana_token=unused \
    -var alert_email=alerts@example.com \
    -var site_url=https://example.com \
    >"$work_dir/rules.json"

docker run --rm -v "$(host_path "$module_dir/tests"):/scripts:ro" -v "$(host_path "$work_dir"):/tests"   "$PYTHON_IMAGE" python /scripts/render_rules.py /tests/rules.json >"$work_dir/rules.yml"
cp "$module_dir/tests/alerts.test.yml" "$work_dir/"
chmod 644 "$work_dir"/*

docker run --rm --entrypoint promtool -v "$(host_path "$work_dir"):/tests" -w /tests "$PROM_IMAGE" \
  test rules alerts.test.yml
