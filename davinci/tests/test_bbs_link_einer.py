"""Only the newest BBS Link speaks to a BBS (2026-10-15: started three times, three links fought
over one timeline and BBS deleted the tracker clip).

    python3 davinci/tests/test_bbs_link_einer.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402


class Stand(bbs_link.Client):
    def __init__(self, port):
        self.port, self.socket, self.steps, self.log = port, None, 0, lambda text: None

    def connect(self):
        self.socket = Socket()

    def step(self):
        self.steps += 1


class Socket:
    closed = False

    def close(self):
        self.closed = True


class NewestLinkWins(unittest.TestCase):
    def test_older_link_stops_when_a_newer_one_starts(self):
        old, new = Stand(59871), Stand(59871)
        old.interval = new.interval = 0
        old.claim_port()
        self.assertFalse(old.superseded())

        new.claim_port()
        old.run(stop=lambda: old.steps > 5)

        self.assertTrue(old.superseded())
        self.assertFalse(new.superseded())
        self.assertLessEqual(old.steps, 1, "the old link gives way instead of speaking to BBS")

    def test_another_port_is_another_bbs(self):
        a, b = Stand(59872), Stand(59873)
        a.claim_port()
        b.claim_port()
        self.assertFalse(a.superseded())


if __name__ == "__main__":
    unittest.main()
