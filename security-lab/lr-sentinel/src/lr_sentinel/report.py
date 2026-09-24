from __future__ import annotations

import html
import json
from pathlib import Path
from .detector import Alert


def write_json(alerts: list[Alert], path: str | Path) -> None:
    Path(path).write_text(
        json.dumps([a.to_dict() for a in alerts], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def write_html(alerts: list[Alert], path: str | Path) -> None:
    rows = []
    for a in alerts:
        rows.append(
            "<tr>"
            f"<td>{html.escape(a.severity.upper())}</td>"
            f"<td>{a.score}</td>"
            f"<td>{html.escape(a.rule_id)}</td>"
            f"<td>{html.escape(a.title)}</td>"
            f"<td>{html.escape(a.entity)}</td>"
            f"<td><code>{html.escape(json.dumps(a.evidence, ensure_ascii=False))}</code></td>"
            "</tr>"
        )
    document = f"""<!doctype html>
<meta charset="utf-8">
<title>LR-Sentinel Report</title>
<style>
body{{font:15px system-ui;margin:40px;line-height:1.5}}
table{{border-collapse:collapse;width:100%}}
th,td{{border:1px solid #ddd;padding:10px;text-align:left;vertical-align:top}}
th{{background:#f6f8fa}}
code{{white-space:pre-wrap;word-break:break-word}}
</style>
<h1>LR-Sentinel Detection Report</h1>
<p>Alerts: <strong>{len(alerts)}</strong></p>
<table>
<thead><tr><th>Severity</th><th>Score</th><th>Rule</th><th>Finding</th><th>Entity</th><th>Evidence</th></tr></thead>
<tbody>{''.join(rows)}</tbody>
</table>
"""
    Path(path).write_text(document, encoding="utf-8")
