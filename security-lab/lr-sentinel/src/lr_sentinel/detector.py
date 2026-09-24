from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from math import log2, sqrt
from statistics import mean
from typing import Any, Iterable


@dataclass(frozen=True)
class Alert:
    rule_id: str
    title: str
    severity: str
    score: int
    entity: str
    first_seen: str
    last_seen: str
    evidence: dict[str, Any]
    explanation: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _parse_ts(value: str) -> datetime:
    value = value.replace("Z", "+00:00")
    dt = datetime.fromisoformat(value)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _entropy(value: str) -> float:
    if not value:
        return 0.0
    counts = defaultdict(int)
    for ch in value:
        counts[ch] += 1
    n = len(value)
    return -sum((c / n) * log2(c / n) for c in counts.values())


def _within(events: list[dict[str, Any]], seconds: int) -> Iterable[list[dict[str, Any]]]:
    left = 0
    for right in range(len(events)):
        right_ts = _parse_ts(events[right]["ts"])
        while left <= right and (right_ts - _parse_ts(events[left]["ts"])).total_seconds() > seconds:
            left += 1
        yield events[left:right + 1]


def detect(events: list[dict[str, Any]]) -> list[Alert]:
    normalized = sorted(events, key=lambda e: _parse_ts(e["ts"]))
    alerts: list[Alert] = []
    alerts.extend(_detect_auth(normalized))
    alerts.extend(_detect_port_scan(normalized))
    alerts.extend(_detect_beacon(normalized))
    alerts.extend(_detect_dns(normalized))
    alerts.sort(key=lambda a: (-a.score, a.first_seen, a.rule_id))
    return alerts


def _detect_auth(events: list[dict[str, Any]]) -> list[Alert]:
    groups: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for e in events:
        if e.get("type") == "auth":
            groups[(e.get("src_ip", "?"), e.get("user", "?"))].append(e)

    out: list[Alert] = []
    emitted: set[tuple[str, str, str]] = set()

    for (src, user), group in groups.items():
        for window in _within(group, 120):
            failures = [e for e in window if e.get("status") == "fail"]
            if len(failures) < 5:
                continue
            key = (src, user, failures[0]["ts"])
            if key in emitted:
                continue

            last_failure = _parse_ts(failures[-1]["ts"])
            success = next(
                (
                    e for e in group
                    if e.get("status") == "success"
                    and 0 <= (_parse_ts(e["ts"]) - last_failure).total_seconds() <= 60
                ),
                None,
            )
            if success:
                severity, score, rule_id = "critical", 95, "AUTH-002"
                title = "Repeated authentication failures followed by success"
                explanation = (
                    "Multiple failed logins were followed shortly by a successful login "
                    "for the same source and account. This pattern can indicate credential guessing."
                )
                last_seen = success["ts"]
            else:
                severity, score, rule_id = "high", 80, "AUTH-001"
                title = "Repeated authentication failures"
                explanation = (
                    "At least five failed logins occurred within two minutes for the same "
                    "source and account."
                )
                last_seen = failures[-1]["ts"]

            out.append(
                Alert(
                    rule_id=rule_id,
                    title=title,
                    severity=severity,
                    score=score,
                    entity=f"{src} -> {user}",
                    first_seen=failures[0]["ts"],
                    last_seen=last_seen,
                    evidence={
                        "failed_attempts": len(failures),
                        "source_ip": src,
                        "user": user,
                        "success_after_failures": bool(success),
                    },
                    explanation=explanation,
                )
            )
            emitted.add(key)
    return out


def _detect_port_scan(events: list[dict[str, Any]]) -> list[Alert]:
    groups: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for e in events:
        if e.get("type") == "net" and "dst_port" in e:
            groups[(e.get("src_ip", "?"), e.get("dst_ip", "?"))].append(e)

    out: list[Alert] = []
    emitted: set[tuple[str, str, str]] = set()
    for (src, dst), group in groups.items():
        for window in _within(group, 60):
            ports = sorted({int(e["dst_port"]) for e in window})
            if len(ports) < 10:
                continue
            key = (src, dst, window[0]["ts"])
            if key in emitted:
                continue
            out.append(
                Alert(
                    rule_id="NET-001",
                    title="Possible TCP/UDP port scan",
                    severity="high",
                    score=78,
                    entity=f"{src} -> {dst}",
                    first_seen=window[0]["ts"],
                    last_seen=window[-1]["ts"],
                    evidence={"unique_ports": len(ports), "ports": ports[:32]},
                    explanation=(
                        "The same source contacted at least ten distinct destination ports "
                        "on one host within sixty seconds."
                    ),
                )
            )
            emitted.add(key)
    return out


def _detect_beacon(events: list[dict[str, Any]]) -> list[Alert]:
    groups: dict[tuple[str, str, int], list[dict[str, Any]]] = defaultdict(list)
    for e in events:
        if e.get("type") == "net" and "dst_port" in e:
            groups[(e.get("src_ip", "?"), e.get("dst_ip", "?"), int(e["dst_port"]))].append(e)

    out: list[Alert] = []
    for (src, dst, port), group in groups.items():
        if len(group) < 6:
            continue
        times = [_parse_ts(e["ts"]).timestamp() for e in group]
        intervals = [b - a for a, b in zip(times, times[1:]) if b > a]
        if len(intervals) < 5:
            continue
        avg = mean(intervals)
        if avg < 5:
            continue
        variance = mean([(x - avg) ** 2 for x in intervals])
        cv = sqrt(variance) / avg if avg else 1.0
        if cv > 0.15:
            continue

        out.append(
            Alert(
                rule_id="NET-002",
                title="Regular outbound connection pattern",
                severity="medium",
                score=62,
                entity=f"{src} -> {dst}:{port}",
                first_seen=group[0]["ts"],
                last_seen=group[-1]["ts"],
                evidence={
                    "connections": len(group),
                    "mean_interval_seconds": round(avg, 2),
                    "interval_cv": round(cv, 3),
                },
                explanation=(
                    "Outbound connections recur at a highly regular interval. Periodicity alone "
                    "does not prove malicious activity, but it is useful for beacon triage."
                ),
            )
        )
    return out


def _detect_dns(events: list[dict[str, Any]]) -> list[Alert]:
    out: list[Alert] = []
    for e in events:
        if e.get("type") != "dns":
            continue
        query = str(e.get("query", "")).strip(".")
        if not query:
            continue
        labels = query.split(".")
        longest = max(labels, key=len)
        entropy = _entropy(longest)
        qtype = str(e.get("qtype", "A")).upper()
        suspicious = (
            len(longest) >= 45
            or (len(longest) >= 30 and entropy >= 3.8)
            or (qtype == "TXT" and len(longest) >= 25)
        )
        if not suspicious:
            continue
        out.append(
            Alert(
                rule_id="DNS-001",
                title="Suspicious DNS label",
                severity="medium",
                score=58 if qtype != "TXT" else 66,
                entity=e.get("src_ip", "?"),
                first_seen=e["ts"],
                last_seen=e["ts"],
                evidence={
                    "query": query,
                    "qtype": qtype,
                    "longest_label_length": len(longest),
                    "label_entropy": round(entropy, 3),
                },
                explanation=(
                    "The DNS query contains an unusually long or high-entropy label. "
                    "This heuristic is intended for triage and can produce false positives."
                ),
            )
        )
    return out
