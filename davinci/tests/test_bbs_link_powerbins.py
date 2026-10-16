"""Power Bins from a stand-in User.db with Resolve's layout (no Resolve, no user data).

    python3 davinci/tests/test_bbs_link_powerbins.py
"""
import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402


def protobuf(folder, name):
    out = b""
    for number, text in ((1, folder), (2, name), (3, "Thu Oct  1 15:58:40 2026")):
        data = text.encode("utf-8")
        out += bytes([number << 3 | 2, len(data)]) + data
    return out + b"\x48\x80\x01"                       # a varint field after them


def raw_frame(payload):
    """A zstd frame with one raw block, the way Resolve writes short blobs."""
    header = bytes([0x20, len(payload)])               # single segment, 1-byte content size
    block = ((len(payload) << 3) | 1).to_bytes(3, "little")
    return b"\x28\xb5\x2f\xfd" + header + block + payload


class PowerBins(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="powerbins-")
        self.song = os.path.join(self.dir, "lied.mp3")
        open(self.song, "wb").write(b"ID3")
        self.db = os.path.join(self.dir, "User.db")
        con = sqlite3.connect(self.db)
        con.executescript("""
            create table Sm2MpFolder (Sm2MpFolder_id text, Name text);
            create table Sm2MpMedia (Sm2MpMedia_id text, Name text, MpFolder text);
            create table BtAudioInfo (Sm2MpMedia_id text, Clip blob);
            create table BtVideoInfo (Sm2MpMedia_id text, Clip blob);
            insert into Sm2MpFolder values ('f1', 'Master'), ('f2', 'Musik');
            insert into Sm2MpMedia values ('m1', 'lied.mp3', 'f1'), ('m2', 'weg.mp3', 'f2'),
                                          ('m3', 'clip.mp4', 'f2');
        """)
        prefix = b"\x00\x00\x00\x02\x00\x00\x00\x8f\x81"   # what precedes the frame in Resolve
        con.execute("insert into BtAudioInfo values ('m1', ?)",
                    (prefix + raw_frame(protobuf(self.dir, "lied.mp3")),))
        con.execute("insert into BtAudioInfo values ('m2', ?)",
                    (prefix + raw_frame(protobuf(self.dir, "weg.mp3")),))
        con.execute("insert into BtVideoInfo values ('m3', ?)",
                    (prefix + raw_frame(protobuf(self.dir, "lied.mp3")),))
        con.commit()
        con.close()

    def test_lists_existing_files_with_their_bin(self):
        items = bbs_link.power_bins(self.db)
        self.assertEqual([(m["name"], m["folder"], m["kind"], m["path"]) for m in items],
                         [("lied.mp3", "Power Bins/Master", "audio", self.song),
                          ("clip.mp4", "Power Bins/Musik", "video", self.song)])

    def test_raw_frames_without_a_zstd_library(self):
        frame = raw_frame(protobuf("/a", "b.wav"))
        self.assertEqual(bbs_link.protobuf_strings(bbs_link.unzstd_raw(frame)), {1: b"/a", 2: b"b.wav",
                                                                                  3: b"Thu Oct  1 15:58:40 2026"})

    def test_the_database_is_not_held_open(self):
        bbs_link.power_bins(self.db)
        os.rename(self.db, self.db + ".moved")          # would fail on Windows if a handle stayed open
        self.assertTrue(os.path.exists(self.db + ".moved"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
