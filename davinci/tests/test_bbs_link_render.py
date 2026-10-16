"""Camera clips show the film in Resolve: they are cut at their film time from the film's own
media (`camera_media`), and when BBS renders the film over that file Resolve re-reads it
(ReplaceClip) instead of the clips being laid again.

No Resolve needed: the stand-in of test_bbs_link_video.

    python3 davinci/tests/test_bbs_link_render.py
"""
import os
import sys
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bbs_link  # noqa: E402
import test_bbs_link_video as stand_in  # noqa: E402


class Media(stand_in.PoolItem):
    def __init__(self, path, seconds, fps=stand_in.FPS):
        super().__init__(path, seconds, fps)
        self.read_at, self.replaced = time.time() - 3600, 0

    def GetClipProperty(self, name=None):
        props = super().GetClipProperty()
        props["Date Modified"] = time.strftime("%a %b %d %H:%M:%S %Y", time.localtime(self.read_at))
        return props if name is None else props.get(name)

    timelines = []

    def ReplaceClip(self, path):
        self.replaced += 1
        self.read_at = os.path.getmtime(path)
        # As Resolve 21.1: every item cut from it takes the file's name.
        for timeline in Media.timelines:
            for track in timeline.tracks["video"]:
                for item in track:
                    if item.media is self:
                        item.name = os.path.basename(path)
        return True


class CameraClipsShowTheFilm(stand_in.VideoOnTheTimeline):
    def setUp(self):
        stand_in.PoolItem, self.original = Media, stand_in.PoolItem
        super().setUp()
        self.media = os.path.join(self.dir, "davinci", "film-f-30.mp4")
        open(self.media, "wb").close()
        os.utime(self.media, (time.time() - 7200, time.time() - 7200))
        self.film = dict(self.film, camera_media=self.media, clips=self.film["clips"] + [
            {"uuid": "late", "kind": "camera", "layer": 0, "tick": 100, "duration": 40, "title": "dolly", "enabled": True}])
        self.session.on_film(self.film)
        Media.timelines = [self.timeline]

    def tearDown(self):
        stand_in.PoolItem = self.original

    def items(self):
        return sorted(self.timeline.tracks["video"][0], key=lambda i: i.start)

    def test_cut_at_film_time_from_the_films_media(self):
        idle, late = self.items()
        self.assertEqual(self.media, late.media.path, "from the film's media, not the shared placeholder")
        self.assertEqual((150, 60, 150), (late.start, late.length, late.left), "tick 100 = 5 s into the film")
        self.assertEqual(0, idle.left)
        self.assertIsNone(self.session.poll(), "placed as BBS has it: nothing to report back")

    def test_render_is_reread_not_laid_again(self):
        before = [i.GetUniqueId() for i in self.items()]
        os.utime(self.media, None)                      # BBS put a fresh render in place
        self.session.on_film(dict(self.film, ack=0))

        self.assertEqual(1, self.link.media_item(self.media).replaced)
        self.assertEqual(before, [i.GetUniqueId() for i in self.items()], "items kept: what was done to them stays")
        self.assertEqual(["idle", "dolly"], [i.GetName() for i in self.items()], "BBS' titles, not the file's name")
        self.assertIsNone(self.session.poll(), "the rename by ReplaceClip is no edit to report to BBS")
        self.session.on_film(dict(self.film, ack=0))
        self.assertEqual(1, self.link.media_item(self.media).replaced, "once per render")

    # The video tests of the stand-in run here too: they must not care which media camera clips use.


if __name__ == "__main__":
    unittest.main()
