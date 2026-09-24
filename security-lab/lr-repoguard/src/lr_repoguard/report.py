from __future__ import annotations

import json
from pathlib import Path
from .scanner import Finding


def write_json(findings: list[Finding], path: str | Path) -> None:
    Path(path).write_text(
        json.dumps([f.to_dict() for f in findings], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def write_markdown(findings: list[Finding], path: str | Path) -> None:
    counts = {s: sum(1 for f in findings if f.severity == s) for s in ("critical", "high", "medium", "low")}
    lines = [
        "# LR-RepoGuard Report",
        "",
        f"Critical: **{counts['critical']}** · High: **{counts['high']}** · Medium: **{counts['medium']}** · Low: **{counts['low']}**",
        "",
        "| Severity | Rule | Path | Line | Message |",
        "| --- | --- | --- | ---: | --- |",
    ]
    for f in findings:
        message = f.message.replace("|", "\\|")
        lines.append(f"| {f.severity.upper()} | \`{f.rule_id}\` | \`{f.path}\` | {f.line or '-'} | {message} |")
    if not findings:
        lines.append("| — | — | — | — | No findings |")
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")
