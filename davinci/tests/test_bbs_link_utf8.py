"""Inside Resolve the script host runs with an ASCII file system encoding. A sound or video named
with an emoji must not end the link (2026-10-15: UnicodeEncodeError in getmtime, link gone).

Runs bbs_link in a child Python with that encoding.

    python3 davinci/tests/test_bbs_link_utf8.py
"""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHILD = r'''
import sys
sys.path.insert(0, %r)
import bbs_link

class Item:
    def GetClipProperty(self, name):
        return "Mon Jan 01 00:00:00 2001"

print(sys.getfilesystemencoding(), bbs_link.stale(Item(), %s))
'''


class EmojiPathsWithAsciiFileSystem(unittest.TestCase):
    def test_stale_reads_an_emoji_path(self):
        folder = tempfile.mkdtemp()
        path = os.path.join(folder, "ton \U0001f633.wav")
        open(path, "wb").close()
        env = dict(os.environ, LC_ALL="C", LANG="C", PYTHONCOERCECLOCALE="0", PYTHONUTF8="0")
        run = subprocess.run([sys.executable, "-X", "utf8=0", "-B", "-c", CHILD % (HERE, ascii(path))],
                             env=env, capture_output=True, text=True, encoding="utf-8")
        self.assertEqual("", run.stderr)
        self.assertEqual("ascii True", run.stdout.strip(), "as in Resolve, and the file is newer than 2001")


if __name__ == "__main__":
    unittest.main()
