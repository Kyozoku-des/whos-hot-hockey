"""Turn the alert rules rendered by Terraform into a Prometheus rule file for promtool.

Reads the output of `terraform console` for local.rules (see run-alert-tests.sh) and
prints a rule file in which each Grafana rule (query A, last value, threshold) becomes
the equivalent Prometheus alert `(query) <op> <threshold>` with the same `for`. Loki
rules are skipped: promtool only evaluates PromQL.

Alert names become CamelCase ("Server silent" -> ServerSilent), since Prometheus alert
names can't contain spaces.
"""
import json
import sys

OPERATORS = {"gt": ">", "lt": "<"}


def alert_name(name):
    return "".join(word[:1].upper() + word[1:] for word in name.split())


def main(path):
    # terraform console prints the jsonencode() result as a quoted string
    rules = json.loads(json.loads(open(path, encoding="utf-8").read().strip()))
    lines = ["groups:", "  - name: production", "    rules:"]
    for name, rule in sorted(rules.items()):
        if rule["datasource"] != "prometheus":
            continue
        expr = f"({rule['expr']}) {OPERATORS[rule['op']]} {rule['threshold']}"
        lines += [
            f"      - alert: {alert_name(name)}",
            # JSON strings are valid YAML double-quoted scalars
            f"        expr: {json.dumps(expr)}",
            f"        for: {rule['for']}",
        ]
    print("\n".join(lines))


if __name__ == "__main__":
    main(sys.argv[1])
