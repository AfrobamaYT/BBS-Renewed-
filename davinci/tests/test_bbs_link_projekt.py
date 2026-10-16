"""The link never lays a film into a project that holds the user's own edit (reported 2026-10-14:
started in a full project, it looked like the link had replaced the user's timeline).

No Resolve needed: a stand-in project manager keeps projects, timelines and items.

    python3 davinci/tests/test_bbs_link_projekt.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import bbs_link  # noqa: E402


class Timeline:
    def __init__(self, name, items=0):
        self.name, self.items = name, ["item"] * items

    def GetName(self):
        return self.name

    def GetSetting(self, name):
        return "59.94"

    def GetStartFrame(self):
        return 0

    def GetTrackCount(self, kind):
        return 1 if kind == "video" else 0

    def GetItemListInTrack(self, kind, track):
        return list(self.items)

    def SetCurrentTimecode(self, timecode):
        return True


class Pool:
    def __init__(self, project):
        self.project = project

    def CreateEmptyTimeline(self, name):
        timeline = Timeline(name)
        self.project.timelines.append(timeline)
        return timeline


class Project:
    def __init__(self, name, timelines=(), fps=59.94):
        self.name, self.timelines, self.settings = name, list(timelines), {"timelineFrameRate": fps}
        self.current = None

    def GetName(self):
        return self.name

    def GetUniqueId(self):
        return "id-" + self.name

    def GetTimelineCount(self):
        return len(self.timelines)

    def GetTimelineByIndex(self, i):
        return self.timelines[i - 1]

    def SetCurrentTimeline(self, timeline):
        self.current = timeline
        return True

    def GetMediaPool(self):
        return Pool(self)

    def GetSetting(self, name):
        return self.settings.get(name)

    def SetSetting(self, name, value):
        if not isinstance(value, str):
            return False                              # as Resolve: GetSetting gives 59.94, it takes "59.94"
        self.settings[name] = value
        return True


class Manager:
    def __init__(self, *projects):
        self.projects = {p.name: p for p in projects}
        self.current = projects[0]
        self.saved = []
        self.save_works = True

    def GetCurrentProject(self):
        return self.current

    def SaveProject(self):
        if self.save_works:
            self.saved.append(self.current.name)
        return self.save_works

    def GetProjectListInCurrentFolder(self):
        return list(self.projects)

    def LoadProject(self, name):
        if name == self.current.name or name not in self.projects:
            return None                               # as Resolve: the open one answers None
        self.current = self.projects[name]
        return self.current

    def CreateProject(self, name):
        if name in self.projects:
            return None
        self.current = self.projects[name] = Project(name, fps=24.0)
        return self.current


class App:
    def __init__(self, manager):
        self.manager = manager

    def GetProjectManager(self):
        return self.manager


FILM = {"film_id": "f1", "title": "Moggityfirst", "clips": []}


class ProjectGuard(unittest.TestCase):
    def link(self, *projects):
        self.manager = Manager(*projects)
        link = bbs_link.Link(App(self.manager), None)
        self.said = []
        link.say = self.said.append
        return link

    def test_a_full_project_is_saved_and_left_for_a_new_one(self):
        mine = Project("test cc mittel", [Timeline("VOLL", 326)])
        link = self.link(mine)
        link.apply(FILM)
        self.assertEqual(self.manager.saved, ["test cc mittel"])
        self.assertEqual(self.manager.current.name, "BBS Link - Moggityfirst")
        self.assertEqual([t.name for t in mine.timelines], ["VOLL"], "nothing may be added to the user's project")
        self.assertEqual([t.name for t in self.manager.current.timelines], ["BBS - Moggityfirst"])
        self.assertEqual(self.manager.current.settings["timelineFrameRate"], "59.94",
                         "the new project keeps the frame rate the film was laid out in")
        self.assertEqual(len(self.said), 1)
        self.assertIn("test cc mittel", self.said[0])
        self.assertIn("BBS Link - Moggityfirst", self.said[0])

    def test_a_project_with_only_link_timelines_or_empty_ones_is_used(self):
        for timelines in ([], [Timeline("BBS - other", 4)], [Timeline("Timeline 1", 0)]):
            link = self.link(Project("leer", timelines))
            link.apply(FILM)
            self.assertEqual(self.manager.current.name, "leer")
            self.assertEqual(self.manager.saved, [])
            self.assertEqual(self.said, [])

    def test_the_link_project_of_an_earlier_start_is_opened_again(self):
        earlier = Project("BBS Link - Moggityfirst", [Timeline("BBS - Moggityfirst", 3), Timeline("mine", 2)])
        link = self.link(Project("test cc mittel", [Timeline("VOLL", 326)]), earlier)
        link.claim(FILM)                              # apply() would read the stand-in items
        self.assertIs(self.manager.current, earlier)
        self.assertEqual(len(self.manager.projects), 2, "no second link project")
        self.assertEqual(len(earlier.timelines), 2)

    def test_a_full_project_opened_later_is_left_alone(self):
        mine = Project("test cc mittel", [Timeline("VOLL", 326)])
        other = Project("Chat", [Timeline("Schnitt", 5)])
        link = self.link(mine, other)
        link.apply(FILM)
        self.manager.current = other                  # the user opens another of theirs
        with self.assertRaises(bbs_link.Busy) as waits:
            link.apply(FILM)
        self.assertIn("BBS Link - Moggityfirst", str(waits.exception))
        self.assertEqual([t.name for t in other.timelines], ["Schnitt"])
        self.assertEqual(self.manager.current, other, "the link must not pull the user out again")
        self.manager.current = self.manager.projects["BBS Link - Moggityfirst"]
        link.apply(FILM)                              # back in the link's project: it goes on

    def test_no_save_no_switch(self):
        mine = Project("test cc mittel", [Timeline("VOLL", 326)])
        link = self.link(mine)
        self.manager.save_works = False
        with self.assertRaises(bbs_link.Busy):
            link.apply(FILM)
        self.assertIs(self.manager.current, mine)
        self.assertEqual([t.name for t in mine.timelines], ["VOLL"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
