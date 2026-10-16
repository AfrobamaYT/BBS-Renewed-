"""When Resolve has a dialog open it answers None (measured with the GUI, 2026-10-11).

No Resolve needed: a stand-in timeline answers None, and the link must neither report the
film's clips as deleted nor stop.

    python3 davinci/tests/test_bbs_link_busy.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402


class BusyTimeline:
    def GetSetting(self, name):
        return "60"

    def GetStartFrame(self):
        return 216000

    def GetTrackCount(self, kind):
        return 1

    def GetItemListInTrack(self, kind, track):
        return None                                   # a modal dialog is open

    def GetCurrentTimecode(self):
        return None


class BusyResolve(unittest.TestCase):
    def session(self):
        link = bbs_link.Link.__new__(bbs_link.Link)
        link.ids, link._media, link.placeholder_path = {}, {}, None
        session = bbs_link.Session(link)
        session.timeline = BusyTimeline()
        session.film = {"film_id": "f", "clips": []}
        session.known = {"c1": {"uuid": "c1", "kind": "camera", "layer": 0, "tick": 0,
                                "duration": 20, "title": "eins", "enabled": True}}
        session.forms = {"c1": (dict(session.known["c1"]), 0, 0)}
        session.playhead = 216000
        return session

    def test_no_answer_is_not_an_empty_track(self):
        session = self.session()
        with self.assertRaises(bbs_link.Busy):
            session.poll()
        self.assertIn("c1", session.known, "a busy Resolve must not make the clip look deleted")

    def test_the_playhead_waits_too(self):
        with self.assertRaises(bbs_link.Busy):
            self.session().poll_playhead()


if __name__ == "__main__":
    unittest.main(verbosity=2)
