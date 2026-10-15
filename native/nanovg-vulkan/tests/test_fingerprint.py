"""Regression for the actual Windows codepage/path mismatch in run 38063152199."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build import source_digest


class FingerprintTest(unittest.TestCase):
    def test_utf8_comment_and_crlf_have_one_frozen_source_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "src/alpha.c"
            source.parent.mkdir()
            source.write_bytes(b"/* License \xe2\x80\x94 */\r\nint value = 1;\r\n")
            self.assertEqual(source_digest([source], root),
                             "54c4c5551a875db5296f28d737d1e7d1d7b6a74f98872f8409a6416c8b8b0c24")


if __name__ == "__main__":
    unittest.main()
