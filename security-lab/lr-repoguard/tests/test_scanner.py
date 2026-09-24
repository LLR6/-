import tempfile
import unittest
from pathlib import Path
from lr_repoguard.scanner import scan


class ScannerTests(unittest.TestCase):
    def test_signing_material(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "release.jks.b64").write_bytes(b"not-a-real-key")
            self.assertIn("SIGNING-MATERIAL", {f.rule_id for f in scan(tmp)})

    def test_private_key_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "config.txt").write_text(
                "-----BEGIN " + "PRIVATE KEY-----\nredacted\n",
                encoding="utf-8",
            )
            self.assertIn("SECRET-PRIVATE-KEY", {f.rule_id for f in scan(tmp)})

    def test_unpinned_action(self):
        with tempfile.TemporaryDirectory() as tmp:
            wf = Path(tmp, ".github", "workflows")
            wf.mkdir(parents=True)
            Path(wf, "ci.yml").write_text(
                "steps:\n  - uses: actions/checkout@v4\n",
                encoding="utf-8",
            )
            self.assertIn("GHA-UNPINNED-ACTION", {f.rule_id for f in scan(tmp)})

    def test_full_sha_action_is_not_flagged(self):
        with tempfile.TemporaryDirectory() as tmp:
            wf = Path(tmp, ".github", "workflows")
            wf.mkdir(parents=True)
            Path(wf, "ci.yml").write_text(
                "steps:\n  - uses: actions/checkout@0123456789abcdef0123456789abcdef01234567\n",
                encoding="utf-8",
            )
            self.assertNotIn("GHA-UNPINNED-ACTION", {f.rule_id for f in scan(tmp)})


if __name__ == "__main__":
    unittest.main()
