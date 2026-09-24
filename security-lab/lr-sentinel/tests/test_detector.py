import unittest
from lr_sentinel.detector import detect


class DetectorTests(unittest.TestCase):
    def test_auth_failures_followed_by_success(self):
        events = [
            {"ts": f"2026-09-24T10:00:{i:02d}Z", "type": "auth", "src_ip": "10.0.0.8", "user": "admin", "status": "fail"}
            for i in [0, 10, 20, 30, 40]
        ]
        events.append({"ts": "2026-09-24T10:00:50Z", "type": "auth", "src_ip": "10.0.0.8", "user": "admin", "status": "success"})
        self.assertIn("AUTH-002", {a.rule_id for a in detect(events)})

    def test_port_scan(self):
        events = [
            {"ts": f"2026-09-24T10:00:{i:02d}Z", "type": "net", "src_ip": "10.0.0.8", "dst_ip": "10.0.0.9", "dst_port": 20+i}
            for i in range(10)
        ]
        self.assertIn("NET-001", {a.rule_id for a in detect(events)})

    def test_beacon(self):
        events = [
            {"ts": f"2026-09-24T10:{m:02d}:00Z", "type": "net", "src_ip": "10.0.0.8", "dst_ip": "203.0.113.9", "dst_port": 443}
            for m in range(6)
        ]
        self.assertIn("NET-002", {a.rule_id for a in detect(events)})

    def test_dns(self):
        events = [{
            "ts": "2026-09-24T10:00:00Z",
            "type": "dns",
            "src_ip": "10.0.0.8",
            "query": "a9F0kLm3Qp8Vr2Ts7Wx5Yz1Bc6De4Gh9Jk0Mn2Pq.example.test",
            "qtype": "TXT"
        }]
        self.assertIn("DNS-001", {a.rule_id for a in detect(events)})


if __name__ == "__main__":
    unittest.main()
