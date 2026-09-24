from __future__ import annotations

from dataclasses import dataclass, asdict
from pathlib import Path
import re
from typing import Iterable


TEXT_EXTENSIONS = {
    ".py", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".java", ".kt", ".kts",
    ".go", ".rs", ".rb", ".php", ".sh", ".bash", ".zsh", ".ps1", ".yml", ".yaml",
    ".json", ".toml", ".ini", ".cfg", ".conf", ".properties", ".gradle", ".xml",
    ".md", ".txt", ".env", ".dockerfile"
}

SKIP_DIRS = {".git", "node_modules", "dist", "build", ".gradle", ".idea", ".venv", "venv"}
SIGNING_EXTENSIONS = {".jks", ".keystore", ".p12", ".pfx"}
SECRET_PATTERNS = [
    ("SECRET-PRIVATE-KEY", "critical", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("SECRET-GITHUB-TOKEN", "critical", re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{30,}\b")),
    ("SECRET-AWS-ACCESS-KEY", "critical", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
]
DANGEROUS_SHELL = [
    ("CI-CURL-PIPE-SHELL", "high", re.compile(r"\bcurl\b[^\n|]*\|\s*(?:sudo\s+)?(?:bash|sh)\b")),
    ("CI-WGET-PIPE-SHELL", "high", re.compile(r"\bwget\b[^\n|]*\|\s*(?:sudo\s+)?(?:bash|sh)\b")),
    ("FS-CHMOD-777", "medium", re.compile(r"\bchmod\s+(?:-R\s+)?777\b")),
]
WORKFLOW_ACTION = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)", re.MULTILINE)
FULL_SHA = re.compile(r"^[^@]+@[0-9a-fA-F]{40}$")


@dataclass(frozen=True)
class Finding:
    rule_id: str
    severity: str
    path: str
    line: int | None
    message: str
    evidence: str

    def to_dict(self) -> dict:
        return asdict(self)


def _is_text_candidate(path: Path) -> bool:
    if path.name in {"Dockerfile", "Makefile"}:
        return True
    return path.suffix.lower() in TEXT_EXTENSIONS or path.name.startswith(".env")


def _iter_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.is_file():
            yield path


def _line_number(text: str, start: int) -> int:
    return text.count("\n", 0, start) + 1


def scan(root: str | Path) -> list[Finding]:
    root = Path(root).resolve()
    findings: list[Finding] = []

    for path in _iter_files(root):
        rel = path.relative_to(root).as_posix()
        suffix = path.suffix.lower()
        name_lower = path.name.lower()
        encoded_signing = any(marker in name_lower for marker in (".jks.", ".keystore.", ".p12.", ".pfx."))

        if suffix in SIGNING_EXTENSIONS or encoded_signing:
            findings.append(Finding(
                "SIGNING-MATERIAL", "critical", rel, None,
                "Signing material is tracked inside the repository.",
                path.name,
            ))
            continue

        if not _is_text_candidate(path):
            continue

        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue

        for rule_id, severity, pattern in SECRET_PATTERNS:
            for match in pattern.finditer(text):
                findings.append(Finding(
                    rule_id, severity, rel, _line_number(text, match.start()),
                    "Potential credential or private-key material is present in a tracked text file.",
                    match.group(0)[:32] + ("..." if len(match.group(0)) > 32 else ""),
                ))

        for rule_id, severity, pattern in DANGEROUS_SHELL:
            for match in pattern.finditer(text):
                findings.append(Finding(
                    rule_id, severity, rel, _line_number(text, match.start()),
                    "Potentially unsafe shell pattern found.",
                    match.group(0).strip()[:120],
                ))

        if path.name.endswith((".yml", ".yaml")) and ".github/workflows/" in f"/{rel}":
            write_all = re.search(r"^\s*permissions:\s*write-all\s*$", text, re.MULTILINE)
            if write_all:
                findings.append(Finding(
                    "GHA-WRITE-ALL", "high", rel, _line_number(text, write_all.start()),
                    "Workflow grants write-all permissions.",
                    "permissions: write-all",
                ))
            for match in WORKFLOW_ACTION.finditer(text):
                ref = match.group(1).strip()
                if ref.startswith("./") or ref.startswith("docker://"):
                    continue
                if not FULL_SHA.match(ref):
                    findings.append(Finding(
                        "GHA-UNPINNED-ACTION", "medium", rel, _line_number(text, match.start()),
                        "Third-party GitHub Action is not pinned to a full commit SHA.",
                        ref,
                    ))

    order = {"critical": 0, "high": 1, "medium": 2, "low": 3}
    findings.sort(key=lambda f: (order.get(f.severity, 9), f.path, f.line or 0, f.rule_id))
    return findings
