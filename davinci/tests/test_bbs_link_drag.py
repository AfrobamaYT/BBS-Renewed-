"""While a mouse button is down in Resolve, the film waits; the playhead does not.

Resolve reports every intermediate state of a drag; reading or writing the film mid-drag undid
the drag and left a cut-off copy behind (measured 2026-10-12). No Resolve needed.

    python3 davinci/tests/test_bbs_link_drag.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402


class StandInSession:
    def __init__(self):
        self.calls = []

    def left_project(self):
        return False

    def on_film(self, fields):
        self.calls.append("film")
        return None

    def poll_playhead(self):
        self.calls.append("playhead")
        return None

    def poll(self):
        self.calls.append("clips")
        return None


class DuringADrag(unittest.TestCase):
    def client(self, held):
        client = bbs_link.Client.__new__(bbs_link.Client)
        client.session = StandInSession()
        client.pending_film, client.pending_playhead = {"film_id": "f", "clips": []}, None
        client.media_job, client.media_at, client.MEDIA_EVERY = None, float("inf"), float("inf")
        self.addCleanup(setattr, bbs_link, "mouse_held", bbs_link.mouse_held)
        bbs_link.mouse_held = lambda: held
        return client

    def test_a_held_button_keeps_the_film_back(self):
        client = self.client(True)
        client.resolve_round()
        self.assertEqual(client.session.calls, ["playhead"])
        self.assertIsNotNone(client.pending_film, "BBS' film must still be applied after the drag")

    def test_after_the_drag_everything_runs(self):
        client = self.client(False)
        client.resolve_round()
        self.assertEqual(client.session.calls, ["film", "playhead", "clips"])
        self.assertIsNone(client.pending_film)


if __name__ == "__main__":
    unittest.main(verbosity=2)
