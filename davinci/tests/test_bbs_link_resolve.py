"""bbs_link.Link against a REAL DaVinci Resolve (Studio, external scripting).

Start Resolve first (`/opt/resolve/bin/resolve -nogui` is enough), then:

    python3 davinci/tests/test_bbs_link_resolve.py

Works only in the project "BBS-Link-Test" (created if missing) and a fresh timeline per run;
the user's own projects are never opened. The ground truth is the snapshot this test writes.
"""
import os
import subprocess
import sys
import tempfile
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402

PROJECT = "BBS-Link-Test"


def make_media(folder):
    video = os.path.join(folder, bbs_link.PLACEHOLDER_NAME)
    audio = os.path.join(folder, "ton.wav")
    for args in (["-f", "lavfi", "-i", "color=c=0x334455:s=320x180:r=60:d=120",
                  "-c:v", "libx264", "-pix_fmt", "yuv420p", video],
                 ["-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=20", audio]):
        subprocess.run(["ffmpeg", "-v", "error", "-y"] + args, check=True)
    return video, audio


class LinkAgainstResolve(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        app = bbs_link.resolve_app()
        manager = app.GetProjectManager()
        current = manager.GetCurrentProject()
        # With a GUI, loading the project that is already open answers None (measured).
        project = current if current and current.GetName() == PROJECT else \
            manager.LoadProject(PROJECT) or manager.CreateProject(PROJECT)
        if project is None or project.GetName() != PROJECT:
            raise RuntimeError("could not open the test project %s" % PROJECT)
        project.SetSetting("timelineFrameRate", "60")

        cls.folder = tempfile.mkdtemp(prefix="bbs-link-")
        cls.video, cls.audio = make_media(cls.folder)
        cls.link = bbs_link.Link(app, cls.video)

    def film(self, clips):
        return {"film_id": "test-%d-%d" % (os.getpid(), int(time.time() * 1000)), "clips": clips,
                "title": "test-%d-%s" % (os.getpid(), self._testMethodName)}

    def camera(self, uuid, layer, tick, duration, title, enabled=True):
        return {"uuid": uuid, "kind": "camera", "layer": layer, "tick": tick,
                "duration": duration, "title": title, "enabled": enabled}

    def sound(self, uuid, layer, tick, duration, offset, title):
        return {"uuid": uuid, "kind": "audio", "layer": layer, "tick": tick, "duration": duration,
                "offset": offset, "title": title, "audio_path": self.audio, "enabled": True}

    def assert_mirrors(self, film):
        timeline = self.link.timeline_for(film)
        linked, _, _ = self.link.read(timeline)
        want = {c["uuid"]: {k: v for k, v in c.items() if k != "audio_path"} for c in film["clips"]}
        for clip in want.values():
            if clip["kind"] == "camera":
                clip.pop("offset", None)
        self.assertEqual(want, linked)
        return timeline

    def test_build_and_edit(self):
        film = self.film([self.camera("c1", 0, 0, 40, "tracker 1"),
                          self.camera("c2", 0, 40, 25, "keyframe clip2"),
                          self.camera("c3", 1, 10, 7, "overlay", enabled=False),
                          self.sound("a1", 0, 5, 60, 12, "musik")])

        changes = self.link.apply(film)
        self.assertEqual(sorted(changes["placed"]), ["a1", "c1", "c2", "c3"])
        timeline = self.assert_mirrors(film)
        _, items, _ = self.link.read(timeline)
        kept = items["c2"].GetUniqueId()

        film["clips"][0]["tick"] = 100            # move
        film["clips"][1]["title"] = "renamed"     # rename only
        film["clips"][2]["enabled"] = True        # switch on
        del film["clips"][3]                      # delete the sound
        changes = self.link.apply(film)

        self.assertEqual(changes, {"removed": ["a1"], "placed": ["c1"], "changed": ["c2", "c3"],
                                   "deferred": [], "failed": {}, "silent": []})
        timeline = self.assert_mirrors(film)
        _, items, _ = self.link.read(timeline)
        self.assertEqual(kept, items["c2"].GetUniqueId(),
                         "a renamed clip must keep its Resolve item (and what was done to it)")

    def test_unlinked_items_stay(self):
        film = self.film([self.camera("c1", 0, 0, 20, "eins")])
        timeline = self.link.timeline_for(film)
        foreign = self.link.pool.AppendToTimeline([{
            "mediaPoolItem": self.link.media_item(self.audio), "startFrame": 0, "endFrame": 30,
            "trackIndex": 1, "mediaType": 2, "recordFrame": timeline.GetStartFrame() + 600}])
        self.assertEqual(len(foreign), 1)

        self.link.apply(film)
        film["clips"] = []
        self.link.apply(film)

        linked, _, unlinked = self.link.read(timeline)
        self.assertEqual(linked, {})
        self.assertEqual(len(unlinked), 1, "an item the user placed in Resolve must survive")
        self.assertEqual(unlinked[0][0]["path"], self.audio)

    def test_bbs_restart_rebinds_instead_of_replacing(self):
        film = self.film([self.camera("c1", 0, 0, 40, "tracker 1"),
                          self.sound("a1", 0, 10, 30, 4, "musik")])
        self.link.apply(film)
        _, items, _ = self.link.read(self.link.timeline_for(film))
        before = {u: i.GetUniqueId() for u, i in items.items()}

        film["clips"][0]["uuid"], film["clips"][1]["uuid"] = "neu-c1", "neu-a1"
        changes = self.link.apply(film)

        self.assertEqual((changes["placed"], changes["removed"]), ([], []))
        _, items, _ = self.link.read(self.link.timeline_for(film))
        self.assertEqual({u: i.GetUniqueId() for u, i in items.items()},
                         {"neu-c1": before["c1"], "neu-a1": before["a1"]})

    def test_a_clip_that_lost_its_marker_is_taken_back(self):
        # Ctrl+Z in Resolve takes back the marker the link set last (measured 2026-10-12).
        film = self.film([self.camera("c1", 0, 0, 40, "tracker 1")])
        self.link.apply(film)
        _, items, _ = self.link.read(self.link.timeline_for(film))
        before = items["c1"].GetUniqueId()
        bbs_link._unmark(items["c1"])
        self.link.ids.clear()

        changes = self.link.apply(film)

        self.assertEqual((changes["placed"], changes["removed"]), ([], []))
        _, items, foreign = self.link.read(self.link.timeline_for(film))
        self.assertEqual(({u: i.GetUniqueId() for u, i in items.items()}, foreign), ({"c1": before}, []))

    def test_camera_clips_wait_for_the_placeholder(self):
        film = self.film([self.camera("c1", 0, 0, 40, "tracker 1"),
                          self.sound("a1", 0, 10, 30, 0, "musik")])
        film["placeholder"] = ""
        changes = self.link.apply(film)
        self.assertEqual((changes["placed"], changes["deferred"]), (["a1"], ["c1"]))

        film["placeholder"] = self.video
        self.assertEqual(self.link.apply(film)["placed"], ["c1"])

        film["placeholder"] = ""
        film["clips"][0]["tick"] = 80
        changes = self.link.apply(film)
        self.assertEqual(changes["removed"], [], "a waiting camera clip must not be cleared")
        self.assertEqual(self.link.read(self.link.timeline_for(film))[0]["c1"]["tick"], 0)
        self.link.placeholder_path = self.video

    def test_silence_resolve_cannot_show(self):
        film = self.film([self.sound("late", 0, 10, 50, -15, "spaet"),     # 15 ticks silence first
                          self.sound("long", 1, 0, 500, 300, "lang"),      # file ends after 100
                          self.sound("mute", 2, 0, 10, -20, "stumm")])     # never audible
        changes = self.link.apply(film)
        self.assertEqual(sorted(changes["placed"]), ["late", "long"])

        linked, _, _ = self.link.read(self.link.timeline_for(film))
        self.assertEqual({u: (c["tick"], c["duration"], c["offset"]) for u, c in linked.items()},
                         {"late": (25, 35, 0), "long": (0, 100, 300)})
        self.assertEqual(self.link.apply(film)["placed"], [], "a second apply must not churn")

    def test_edits_map_back_to_bbs_terms(self):
        base = {"uuid": "s", "kind": "audio", "layer": 0, "tick": 10, "duration": 50,
                "offset": -15, "title": "t", "enabled": True}
        form = {"kind": "audio", "layer": 0, "tick": 25, "duration": 35, "offset": 0,
                "title": "t", "enabled": True}
        shown = (form, 15, 0)

        moved = bbs_link.back(base, shown, dict(form, tick=45))
        self.assertEqual((moved["tick"], moved["duration"], moved["offset"]), (30, 50, -15))

        head = bbs_link.back(base, shown, dict(form, tick=30, offset=5, duration=30))
        self.assertEqual((head["tick"], head["duration"], head["offset"]), (30, 30, 5))

        long_base = dict(base, tick=0, duration=500, offset=300)
        long_form = dict(form, tick=0, duration=100, offset=300)
        tail = bbs_link.back(long_base, (long_form, 0, 400), dict(long_form, duration=60))
        self.assertEqual((tail["tick"], tail["duration"], tail["offset"]), (0, 60, 300))
        kept = bbs_link.back(long_base, (long_form, 0, 400), dict(long_form, tick=20))
        self.assertEqual((kept["tick"], kept["duration"], kept["offset"]), (20, 500, 300))

    def test_one_bad_clip_does_not_stop_the_others(self):
        film = self.film([self.sound("gone", 0, 0, 20, 0, "weg"),
                          self.camera("c1", 0, 0, 20, "eins")])
        film["clips"][0]["audio_path"] = ""                     # BBS: file missing
        film["clips"].append(self.sound("bad", 1, 0, 20, 0, "kaputt"))
        film["clips"][-1]["audio_path"] = "/nicht/da.wav"       # Resolve cannot import it
        changes = self.link.apply(film)
        self.assertEqual(changes["placed"], ["c1"])
        self.assertEqual(changes["silent"], ["gone"])
        self.assertEqual(list(changes["failed"]), ["bad"])

    def test_a_changed_file_is_read_again(self):
        path = os.path.join(self.folder, "wechselt.wav")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                        "sine=frequency=500:sample_rate=48000:duration=3", path], check=True)
        self.assertEqual(bbs_link.Link(self.link.app, self.video).media_ticks(path), 60)
        time.sleep(1.2)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                        "sine=frequency=500:sample_rate=48000:duration=7", path], check=True)
        self.assertEqual(bbs_link.Link(self.link.app, self.video).media_ticks(path), 140)

    def test_tick_frame_rounding(self):
        self.assertEqual(bbs_link.to_frames(7, 60), 21)
        self.assertEqual(bbs_link.to_ticks(21, 60), 7)
        self.assertEqual(bbs_link.to_frames(7, 24), 8)      # 8.4 frames: rounded, not exact
        self.assertEqual(bbs_link.to_ticks(8, 24), 7)


class SessionAgainstResolve(LinkAgainstResolve):
    """The Resolve half of the live link: what it reports, what it refuses to apply."""

    test_build_and_edit = test_unlinked_items_stay = test_tick_frame_rounding = None
    test_bbs_restart_rebinds_instead_of_replacing = test_camera_clips_wait_for_the_placeholder = None
    test_silence_resolve_cannot_show = test_edits_map_back_to_bbs_terms = None
    test_one_bad_clip_does_not_stop_the_others = test_a_changed_file_is_read_again = None

    def test_late_sound_keeps_its_silence_when_renamed(self):
        film = self.film([self.sound("late", 0, 10, 50, -15, "spaet")])
        session = bbs_link.Session(self.link)
        session.on_film(film)
        _, items, _ = self.link.read(session.timeline)
        items["late"].SetName("neu")

        change = session.poll()["changed"][0]
        self.assertEqual(change["base"]["offset"], -15)
        self.assertEqual((change["now"]["tick"], change["now"]["offset"], change["now"]["title"]),
                         (10, -15, "neu"))
        self.assertIsNone(session.poll())

    def started(self):
        film = self.film([self.camera("c1", 0, 0, 40, "tracker 1"),
                          self.sound("a1", 0, 10, 30, 0, "musik")])
        counter = iter(range(1, 100))
        session = bbs_link.Session(self.link, new_uuid=lambda: "neu%d" % next(counter))
        self.assertIsNotNone(session.on_film(film))
        return film, session

    def test_no_echo(self):
        _, session = self.started()
        self.assertIsNone(session.poll(), "applying a snapshot must not report it back")

    def test_reports_edits_with_their_base(self):
        film, session = self.started()
        _, items, _ = self.link.read(session.timeline)
        items["c1"].SetName("umbenannt")
        items["a1"].SetClipEnabled(False)

        report = session.poll()
        self.assertEqual(report["seq"], 1)
        got = {c["uuid"]: (c["base"]["title"], c["now"]["title"], c["now"]["enabled"])
               for c in report["changed"]}
        self.assertEqual(got, {"c1": ("tracker 1", "umbenannt", True),
                               "a1": ("musik", "musik", False)})
        self.assertIsNone(session.poll(), "an edit is reported once")

    def test_reports_removal(self):
        _, session = self.started()
        _, items, _ = self.link.read(session.timeline)
        self.assertTrue(session.timeline.DeleteClips([items["c1"]], False))

        report = session.poll()
        self.assertEqual([r["uuid"] for r in report["removed"]], ["c1"])
        self.assertEqual(report["removed"][0]["base"]["title"], "tracker 1")

    def test_adopts_a_sound_the_user_placed(self):
        _, session = self.started()
        self.assertTrue(session.timeline.AddTrack("audio"))
        self.link.pool.AppendToTimeline([{
            "mediaPoolItem": self.link.media_item(self.audio), "startFrame": 60, "endFrame": 120,
            "trackIndex": 2, "mediaType": 2, "recordFrame": session.timeline.GetStartFrame() + 300}])

        report = session.poll()
        self.assertEqual(len(report["added"]), 1)
        clip = report["added"][0]
        self.assertEqual((clip["uuid"], clip["kind"], clip["layer"], clip["tick"],
                          clip["duration"], clip["offset"], clip["path"]),
                         ("neu1", "audio", 1, 100, 20, 20, self.audio))
        self.assertIsNone(session.poll(), "an adopted sound is linked from then on")

    def test_a_refused_sound_stays_the_users(self):
        film, session = self.started()
        self.link.pool.AppendToTimeline([{
            "mediaPoolItem": self.link.media_item(self.audio), "startFrame": 0, "endFrame": 60,
            "trackIndex": 1, "mediaType": 2, "recordFrame": session.timeline.GetStartFrame() + 900}])
        uuid = session.poll()["added"][0]["uuid"]

        session.disown(uuid)
        _, _, foreign = self.link.read(session.timeline)
        self.assertEqual(len(foreign), 1, "the refused sound is the user's own item again")
        self.assertIsNone(session.poll(), "and it is not offered a second time")

        film["ack"] = session.seq
        session.on_film(film)
        self.assertEqual(len(self.link.read(session.timeline)[2]), 1, "a new film does not delete it")

    def test_playhead_both_ways_without_echo(self):
        _, session = self.started()
        self.assertIsNone(session.poll_playhead(), "where Resolve stands at connect is not a move")

        session.move_playhead(123)                           # BBS moved its cursor
        start = session.timeline.GetStartFrame()
        self.assertEqual(bbs_link.tc_to_frame(session.timeline.GetCurrentTimecode(), 60), start + 369)
        self.assertIsNone(session.poll_playhead(), "our own move is not reported back")

        session.timeline.SetCurrentTimecode(bbs_link.frame_to_tc(start + 600, 60))   # the user scrubs
        self.assertEqual(session.poll_playhead(), 200)
        self.assertIsNone(session.poll_playhead())

    def test_stale_snapshot_is_not_applied(self):
        film, session = self.started()
        _, items, _ = self.link.read(session.timeline)
        items["c1"].SetName("in resolve")
        self.assertEqual(session.poll()["seq"], 1)

        film["ack"] = 0
        self.assertIsNone(session.on_film(film), "BBS has not seen our edit yet")
        self.assertEqual(self.link.read(session.timeline)[0]["c1"]["title"], "in resolve")

        film["ack"] = 1
        self.assertEqual(session.on_film(film)["changed"], ["c1"])

    def test_restart_finds_clips_by_marker(self):
        film, session = self.started()
        fresh = bbs_link.Link(self.link.app, self.video)
        linked, _, foreign = fresh.read(fresh.timeline_for(film))
        self.assertEqual(sorted(linked), ["a1", "c1"])
        self.assertEqual(foreign, [])

    def test_copy_in_resolve_is_not_the_original(self):
        _, session = self.started()
        _, items, _ = self.link.read(session.timeline)
        copy = self.link.pool.AppendToTimeline([{
            "mediaPoolItem": self.link.media_item(self.audio), "startFrame": 0, "endFrame": 60,
            "trackIndex": 1, "mediaType": 2, "recordFrame": session.timeline.GetStartFrame() + 600}])[0]
        copy.AddMarker(0, "Blue", "bbs", "", 1, bbs_link.MARKER_PREFIX + "a1")

        report = session.poll()
        self.assertEqual(report["changed"], [], "the original a1 did not change")
        self.assertEqual([c["uuid"] for c in report["added"]], ["neu1"])

    def test_a_waiting_camera_clip_is_not_reported_deleted(self):
        film = self.film([self.camera("c1", 0, 0, 40, "tracker 1"),
                          self.sound("a1", 0, 10, 30, 0, "musik")])
        film["placeholder"] = ""                  # BBS has not made the placeholder yet
        session = bbs_link.Session(self.link)
        try:
            session.on_film(film)
            report = session.poll()
            self.assertTrue(report is None or not report["removed"],
                            "a camera clip that never reached Resolve was reported deleted: %s" % report)
        finally:
            self.link.placeholder_path = self.video

    def test_another_project_gets_the_film_laid_out_again(self):
        film, session = self.started()
        manager = self.link.app.GetProjectManager()
        other = PROJECT + "-2"
        try:
            self.assertTrue(manager.LoadProject(other) or manager.CreateProject(other))
            manager.GetCurrentProject().SetSetting("timelineFrameRate", "60")
            self.assertTrue(session.left_project(), "the switch must be noticed")
            session.on_film(dict(film, ack=session.seq))
            linked, _, _ = self.link.read(session.timeline)
            self.assertEqual(sorted(linked), ["a1", "c1"])
            self.assertEqual(session.timeline.GetName(), bbs_link.TIMELINE_PREFIX + film["title"])
            self.assertFalse(session.left_project(), "the same project again is no switch")
        finally:
            manager.LoadProject(PROJECT)


if __name__ == "__main__":
    unittest.main(verbosity=2)
