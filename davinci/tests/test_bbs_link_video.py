"""Videos on Resolve's video tracks travel like sounds: a video file the user drops on the timeline
is offered to BBS (kind "video"), a video BBS sends is placed from its own file, and a trim in
Resolve comes back as start, length and offset. Camera clips (the placeholder) stay camera clips.

No Resolve needed: a stand-in keeps the pool, the timeline and its items.

    python3 davinci/tests/test_bbs_link_video.py
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402

FPS = 30.0


class PoolItem:
    def __init__(self, path, seconds, fps=FPS):
        self.path, self.seconds, self.fps = path, seconds, fps

    def GetClipProperty(self, name=None):
        props = {"File Path": self.path, "FPS": str(self.fps), "Date Modified": "",
                 "Duration": "00:00:%02d:00" % self.seconds}
        return props if name is None else props.get(name)


class Item:
    count = 0

    def __init__(self, media, start, length, left=0):
        Item.count += 1
        self.id, self.media, self.start, self.length, self.left = "i%d" % Item.count, media, start, length, left
        self.name, self.enabled, self.markers = os.path.basename(media.path), True, {}

    def GetUniqueId(self):
        return self.id

    def GetMediaPoolItem(self):
        return self.media

    def GetName(self):
        return self.name

    def SetName(self, name):
        self.name = name

    def GetStart(self):
        return self.start

    def GetDuration(self):
        return self.length

    def GetLeftOffset(self):
        return self.left

    def GetClipEnabled(self):
        return self.enabled

    def SetClipEnabled(self, on):
        self.enabled = on

    def GetMarkers(self):
        return self.markers

    def AddMarker(self, frame, color, name, note, length, data):
        self.markers[frame] = {"customData": data}
        return True

    def DeleteMarkerByCustomData(self, data):
        self.markers = {k: v for k, v in self.markers.items() if v["customData"] != data}


class Timeline:
    def __init__(self, name):
        self.name, self.tracks = name, {"video": [[]], "audio": [[]], "subtitle": []}

    def GetName(self):
        return self.name

    def GetSetting(self, name):
        return FPS

    def GetStartFrame(self):
        return 0

    def GetTrackCount(self, kind):
        return len(self.tracks[kind])

    def AddTrack(self, kind):
        self.tracks[kind].append([])
        return True

    def GetItemListInTrack(self, kind, track):
        return list(self.tracks[kind][track - 1])

    def DeleteClips(self, items, ripple):
        for track in self.tracks["video"] + self.tracks["audio"]:
            track[:] = [i for i in track if i not in items]
        return True

    def GetCurrentTimecode(self):
        return "00:00:00:00"

    def SetCurrentTimecode(self, timecode):
        return True


class Folder:
    def __init__(self, clips):
        self.clips = clips

    def GetClipList(self):
        return self.clips

    def GetSubFolderList(self):
        return []


class Pool:
    def __init__(self, project):
        self.project = project

    def GetRootFolder(self):
        return Folder(self.project.media)

    def ImportMedia(self, paths):
        items = [PoolItem(p, 20, 60.0 if p.endswith("60.mp4") else FPS) for p in paths]
        self.project.media += items
        return items

    def CreateEmptyTimeline(self, name):
        timeline = Timeline(name)
        self.project.timelines.append(timeline)
        return timeline

    def AppendToTimeline(self, infos):
        out = []
        for info in infos:
            kind = "video" if info["mediaType"] == 1 else "audio"
            # As Resolve: start/end count the file's frames, the item lives in timeline frames.
            scale = FPS / info["mediaPoolItem"].fps
            item = Item(info["mediaPoolItem"], info["recordFrame"],
                        round((info["endFrame"] - info["startFrame"]) * scale), round(info["startFrame"] * scale))
            self.project.timelines[-1].tracks[kind][info["trackIndex"] - 1].append(item)
            out.append(item)
        return out


class Project:
    def __init__(self):
        self.timelines, self.media = [], []

    def GetName(self):
        return bbs_link.PROJECT_PREFIX + "test"

    def GetUniqueId(self):
        return "p"

    def GetTimelineCount(self):
        return len(self.timelines)

    def GetTimelineByIndex(self, i):
        return self.timelines[i - 1]

    def SetCurrentTimeline(self, timeline):
        return True

    def GetMediaPool(self):
        return Pool(self)

    def GetSetting(self, name):
        return FPS


class App:
    def __init__(self):
        self.project = Project()

    def GetProjectManager(self):
        return self

    def GetCurrentProject(self):
        return self.project


class VideoOnTheTimeline(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.dir, "davinci"))
        self.placeholder = os.path.join(self.dir, "davinci", "placeholder-30.mp4")
        self.video = os.path.join(self.dir, "clip.mp4")
        for path in (self.placeholder, self.video):
            open(path, "wb").close()
        self.app = App()
        self.link = bbs_link.Link(self.app, None)
        self.session = bbs_link.Session(self.link, new_uuid=iter("u%d" % n for n in range(99)).__next__)
        self.film = {"film_id": "f", "title": "f", "ack": 0, "placeholder": self.placeholder, "clips": [
            {"uuid": "cam", "kind": "camera", "layer": 0, "tick": 0, "duration": 40, "title": "idle", "enabled": True}]}
        self.session.on_film(self.film)
        self.timeline = self.app.project.timelines[0]

    def test_dropped_video_is_offered_as_video(self):
        media = self.link.media_item(self.video)
        self.timeline.tracks["video"].append([Item(media, 30, 60, 15)])

        edit = self.session.poll()

        self.assertEqual([], edit["changed"])
        self.assertEqual([], edit["removed"])
        [clip] = edit["added"]
        self.assertEqual(("video", 1, 20, 40, 10), (clip["kind"], clip["layer"], clip["tick"], clip["duration"], clip["offset"]))
        self.assertEqual(self.video, clip["path"])
        self.assertIsNone(self.session.poll(), "offered once, not on every look")

    def test_camera_clip_stays_camera(self):
        _, items, foreign = self.link.read(self.timeline)
        self.assertEqual([], foreign)
        self.assertEqual("camera", self.link.read(self.timeline)[0]["cam"]["kind"])

    def test_video_from_bbs_is_placed_from_its_file_and_trim_comes_back(self):
        video = {"uuid": "vid", "kind": "video", "layer": 1, "tick": 20, "duration": 40, "title": "clip",
                 "enabled": True, "offset": 10, "video_path": self.video}
        self.session.on_film(dict(self.film, clips=self.film["clips"] + [video]))

        [item] = self.timeline.tracks["video"][1]
        self.assertIs(self.link.media_item(self.video), item.media)
        self.assertEqual((30, 60, 15), (item.start, item.length, item.left))

        item.start, item.length, item.left = 45, 45, 15     # moved later, tail trimmed
        edit = self.session.poll()

        [change] = edit["changed"]
        self.assertEqual("vid", change["uuid"])
        self.assertNotIn("video_path", change["base"])
        self.assertEqual((30, 30, 10), (change["now"]["tick"], change["now"]["duration"], change["now"]["offset"]))
        self.assertEqual([], edit["added"])

    def test_video_at_another_rate_keeps_its_length(self):
        video60 = os.path.join(self.dir, "clip60.mp4")
        open(video60, "wb").close()
        video = {"uuid": "v60", "kind": "video", "layer": 1, "tick": 20, "duration": 60, "title": "clip60",
                 "enabled": True, "offset": 20, "video_path": video60}
        self.session.on_film(dict(self.film, clips=self.film["clips"] + [video]))

        [item] = self.timeline.tracks["video"][1]
        self.assertEqual((30, 90, 30), (item.start, item.length, item.left), "3 s at 1 s into the file, on 30 fps")
        self.assertIsNone(self.session.poll(), "placed as BBS has it: nothing to report back")


if __name__ == "__main__":
    unittest.main()
