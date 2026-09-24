from __future__ import annotations

import argparse
import json
from pathlib import Path
from .detector import detect
from .report import write_html, write_json


def load_jsonl(path: str | Path) -> list[dict]:
    events = []
    for number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as exc:
            raise SystemExit(f"{path}:{number}: invalid JSON: {exc}") from exc
        if "ts" not in item or "type" not in item:
            raise SystemExit(f"{path}:{number}: every event needs 'ts' and 'type'")
        events.append(item)
    return events


def main() -> None:
    parser = argparse.ArgumentParser(description="Explainable defensive event detector")
    parser.add_argument("input", help="JSONL event file")
    parser.add_argument("--json", dest="json_out", default="alerts.json")
    parser.add_argument("--html", dest="html_out", default="report.html")
    args = parser.parse_args()

    alerts = detect(load_jsonl(args.input))
    write_json(alerts, args.json_out)
    write_html(alerts, args.html_out)
    print(f"events analyzed; {len(alerts)} alert(s) -> {args.json_out}, {args.html_out}")


if __name__ == "__main__":
    main()
