from __future__ import annotations

import argparse
from collections import Counter
from .scanner import scan
from .report import write_json, write_markdown


RANK = {"low": 1, "medium": 2, "high": 3, "critical": 4}


def main() -> None:
    parser = argparse.ArgumentParser(description="Defensive repository and CI security scanner")
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--json", default="repoguard.json")
    parser.add_argument("--markdown", default="repoguard.md")
    parser.add_argument("--fail-on", choices=["low", "medium", "high", "critical"], default=None)
    args = parser.parse_args()

    findings = scan(args.root)
    write_json(findings, args.json)
    write_markdown(findings, args.markdown)

    counts = Counter(f.severity for f in findings)
    print(
        f"critical={counts['critical']} high={counts['high']} "
        f"medium={counts['medium']} low={counts['low']}"
    )

    if args.fail_on:
        threshold = RANK[args.fail_on]
        if any(RANK.get(f.severity, 0) >= threshold for f in findings):
            raise SystemExit(2)


if __name__ == "__main__":
    main()
