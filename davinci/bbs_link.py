"""BBS <-> DaVinci Resolve live link, Resolve side (task #79, docs/DAVINCI-LINK-PLAN.md).

Runs inside Resolve (Workspace > Scripts, works on Free and Studio) or, on Studio only, as an
external process. It mirrors the open BBS film into a timeline "BBS - <film>" and reports edits
made in Resolve back to BBS.

Units: BBS counts ticks (20 per second), Resolve counts frames at the timeline rate. One BBS clip
is one timeline item; its session identity rides on a marker whose customData is "bbs:<uuid>",
because BBS data gets no new fields (Blender-link invariant).

Measured against Resolve Studio 21.1 (2026-10-11): AppendToTimeline places items exactly at
recordFrame; its endFrame is exclusive (start 0, end 119 -> 119 frames); a timeline item cannot be
moved or trimmed through the API, only deleted and appended again.
"""
import os
import sys

TICKS_PER_SECOND = 20
MARKER_PREFIX = "bbs:"
# Resolve silently refuses timeline names containing ":" (measured 2026-10-11).
TIMELINE_PREFIX = "BBS - "
# The project the link makes when the open one holds the user's own edit (see Link.claim).
PROJECT_PREFIX = "BBS Link - "

# Long, neutral video that every camera clip is cut from until BBS renders real footage.
PLACEHOLDER_NAME = "bbs-platzhalter.mp4"


def resolve_app():
    """The running Resolve, inside (the script host's `resolve`) or outside (Studio)."""
    app = globals().get("resolve") or getattr(sys.modules.get("__main__"), "resolve", None)
    if app is not None:
        return app

    sys.path.append("/opt/resolve/Developer/Scripting/Modules")
    import DaVinciResolveScript

    app = DaVinciResolveScript.scriptapp("Resolve")
    if app is None:
        raise RuntimeError("no running DaVinci Resolve (external scripting needs Studio and "
                           "Preferences > System > General > External scripting = Local)")
    return app


def frames_per_tick(fps):
    return float(fps) / TICKS_PER_SECOND


def to_frames(ticks, fps):
    return int(round(ticks * frames_per_tick(fps)))


def to_ticks(frames, fps):
    return int(round(frames / frames_per_tick(fps)))


def tc_to_frame(timecode, fps):
    """Non-drop timecode at the timeline's nominal rate (23.976 counts as 24, like Resolve)."""
    h, m, s, f = (int(x) for x in timecode.replace(";", ":").split(":"))
    return ((h * 60 + m) * 60 + s) * int(round(fps)) + f


def frame_to_tc(frame, fps):
    rate = int(round(fps))
    seconds, f = divmod(int(frame), rate)
    minutes, s = divmod(seconds, 60)
    h, m = divmod(minutes, 60)
    return "%02d:%02d:%02d:%02d" % (h, m, s, f)


class Busy(RuntimeError):
    """Resolve answered None where it always answers with a value: a modal dialog is open (an
    edit returns None then, measured 2026-10-11 with "Your GPU memory is full" up). Nothing read
    in this state may count -- an empty answer would look like every clip was deleted."""


def need(value, what):
    if value is None:
        raise Busy("Resolve is busy (a dialog open?): no answer for %s" % what)
    return value


class Link:
    """One BBS film mirrored into one timeline of the current project."""

    def __init__(self, app, placeholder_path):
        self.app = app
        self.placeholder_path = placeholder_path
        self._media = {}
        self._paths = None
        # Resolve's own item id -> our uuid. It survives moves and trims within a session; the
        # marker is only the fallback after a restart (a head trim could cut it off).
        self.ids = {}
        self.say = print

    @property
    def project(self):
        """Read every time: the user may switch projects while the link runs."""
        project = self.app.GetProjectManager().GetCurrentProject()
        if project is None:
            raise Busy("Resolve has no open project (project manager open?)")
        if project.GetUniqueId() != getattr(self, "_project_id", None):
            # Pool items and item ids belong to one project; never carry them into another.
            self._project_id = project.GetUniqueId()
            self._media = {}
            self._paths = None
            self.ids = {}
        return project

    @property
    def pool(self):
        return self.project.GetMediaPool()

    # -- media --------------------------------------------------------------

    def _all_pool_items(self):
        out, todo = [], [self.pool.GetRootFolder()]
        while todo:
            folder = todo.pop()
            out += folder.GetClipList() or []
            todo += folder.GetSubFolderList() or []
        return out

    def media_item(self, path):
        """The pool item for a file, imported once; fails loudly if Resolve refuses it."""
        path = os.path.abspath(path)
        if path in self._media:
            return self._media[path]

        # One pass over the pool per project, not one per clip: with Resolve slow after a project
        # first opened (50 ms a call), 9 clips x 166 items locked the link for over a minute
        # (measured 2026-10-11). A miss looks once more: the user may have added it.
        built = False
        for _ in range(2):
            if self._paths is None or not built and path not in self._paths:
                built, paths = True, {}
                for item in self._all_pool_items():   # may reset _paths: a new project
                    paths.setdefault(os.path.abspath(item.GetClipProperty("File Path") or ""), item)
                self._paths = paths
            item = self._paths.get(path)
            if item is not None:
                # Resolve keeps a pool item's length when the file under its path changes
                # (3 s stayed 3 s after the file became 7 s, measured); ReplaceClip re-reads it.
                if stale(item, path) and not item.ReplaceClip(path):
                    raise RuntimeError("Resolve could not re-read %s" % path)
                self._media[path] = item
                return item

        imported = self.pool.ImportMedia([path]) or []
        if len(imported) != 1:
            raise RuntimeError("Resolve did not import %s" % path)
        self._media[path] = self._paths[path] = imported[0]
        return imported[0]

    def media_ticks(self, path):
        """How long a sound file is, in ticks. Resolve leaves "Frames" empty for audio and
        only gives a timecode at the clip's rate (measured 2026-10-11)."""
        props = self.media_item(path).GetClipProperty() or {}
        fps = float(props.get("FPS") or 0)
        parts = [int(x) for x in (props.get("Duration") or "").replace(";", ":").split(":") if x.isdigit()]
        if fps <= 0 or len(parts) != 4:
            raise RuntimeError("Resolve gives no length for %s: %r" % (path, props.get("Duration")))
        h, m, sec, frames = parts
        return int((h * 3600 + m * 60 + sec + frames / fps) * TICKS_PER_SECOND)

    def camera_path(self):
        """What camera clips are cut from: the film's own media (a render once there is one)."""
        return getattr(self, "camera_media", None) or self.placeholder_path

    def shown(self, clip):
        """The clip as Resolve can show it, with the silence it cannot: (form, pad, tail).

        BBS lets a sound start late (negative offset: `pad` ticks of silence) and run past the
        end of its file (`tail`); Resolve has neither, and past a file's end it misbehaves (0..700
        of 600 frames places nothing, 500..700 places 200). So Resolve gets the audible part only,
        or None if nothing is audible.
        """
        form = {k: v for k, v in clip.items() if k in ("uuid", "kind", "layer", "tick", "duration",
                                                        "title", "enabled", "offset", "audio_path",
                                                        "video_path")}
        if clip["kind"] == "camera":
            form.update(offset=form["tick"], src=os.path.abspath(self.camera_path() or ""))
            return form, 0, 0
        path = media_path(clip)
        if not path:
            return None                       # BBS has no file for it: nothing to hear or see

        pad = max(0, -clip.get("offset", 0))
        form["tick"] += pad
        form["offset"] = clip.get("offset", 0) + pad
        form["duration"] -= pad
        tail = max(0, form["offset"] + form["duration"] - self.media_ticks(path))
        form["duration"] -= tail
        return (form, pad, tail) if form["duration"] > 0 else None

    # -- project ------------------------------------------------------------

    def claim(self, film):
        """Never lay a film into a project that holds the user's own edit. Reported 2026-10-14:
        started in a full project, the link put its timeline there and switched to it -- it
        looked like the user's timeline had been replaced. Now, at the start, that project is
        saved and left as it is, and the film goes into a project of the link's own; a full
        project the user opens later while the link runs is left alone until they come back."""
        project = self.project
        if project.GetUniqueId() == getattr(self, "_claimed", None):
            return
        if not own_work(project):
            self._claimed = project.GetUniqueId()
            return
        if getattr(self, "home", None) is not None:
            raise Busy("project %r has timelines of its own, BBS Link leaves it alone; open %r to "
                       "go on" % (project.GetName(), self.home))

        manager, old = self.app.GetProjectManager(), project.GetName()
        if not manager.SaveProject():
            raise Busy("Resolve did not save project %r, so BBS Link does not leave it" % old)
        name = PROJECT_PREFIX + (film.get("title") or film["film_id"]).replace(":", "-")
        fps, note = project.GetSetting("timelineFrameRate"), ""
        if name in (manager.GetProjectListInCurrentFolder() or []):
            new = manager.LoadProject(name)
        else:
            new = manager.CreateProject(name)
            # The frame rate before the first timeline, after that it is fixed. GetSetting gives
            # a number, SetSetting takes only text (59.94 refused, "59.94" taken; measured 2026-10-14).
            rate = fps if isinstance(fps, str) else "%g" % fps
            if new is not None and not new.SetSetting("timelineFrameRate", rate):
                note = " Resolve kept its own frame rate, not %s." % rate
        if new is None:
            raise Busy("Resolve did not open project %r" % name)
        self.home, self._claimed = name, new.GetUniqueId()
        self.say("Your project '%s' has timelines of its own, so BBS Link does not touch it: it was "
                 "saved and stays as it is. The film goes into the project '%s' instead, next to "
                 "yours in the Project Manager.%s" % (old, name, note))

    # -- timeline -----------------------------------------------------------

    def fps(self, timeline):
        return float(need(timeline.GetSetting("timelineFrameRate"), "frame rate"))

    def timeline_for(self, film):
        """The film's timeline, created on first use; made current either way."""
        name = TIMELINE_PREFIX + (film.get("title") or film["film_id"]).replace(":", "-")
        for i in range(1, need(self.project.GetTimelineCount(), "timelines") + 1):
            timeline = need(self.project.GetTimelineByIndex(i), "timeline %d" % i)
            if timeline.GetName() == name:
                self.project.SetCurrentTimeline(timeline)
                return timeline

        timeline = self.pool.CreateEmptyTimeline(name)
        if timeline is None:
            # Measured 2026-10-11: Resolve started by BBS' tab opens on its project manager, and
            # until the user opens a project every new timeline is refused. Wait, don't die.
            raise Busy("Resolve refused to create timeline %r (project manager open? "
                       "open a project in Resolve)" % name)
        self.project.SetCurrentTimeline(timeline)
        return timeline

    def _ensure_tracks(self, timeline, kind, count):
        while timeline.GetTrackCount(kind) < count:
            if not timeline.AddTrack(kind):
                raise RuntimeError("Resolve refused to add a %s track" % kind)

    def read(self, timeline):
        """Linked items as BBS clips {uuid: clip}, their Resolve items {uuid: item}, and the
        user's own items [(clip, item)] -- those carry no identity marker."""
        fps, base = self.fps(timeline), timeline.GetStartFrame()
        linked, foreign = {}, []

        for kind, bbs_kind in (("video", "camera"), ("audio", "audio")):
            for track in range(1, need(timeline.GetTrackCount(kind), "track count") + 1):
                for item in need(timeline.GetItemListInTrack(kind, track), "%s track %d" % (kind, track)):
                    uuid = self.ids.get(item.GetUniqueId())
                    if uuid is None:
                        for marker in (item.GetMarkers() or {}).values():
                            data = marker.get("customData") or ""
                            if data.startswith(MARKER_PREFIX):
                                uuid = data[len(MARKER_PREFIX):]
                        # A copy made in Resolve carries the original's marker but not its id.
                        if uuid is not None and uuid in self.ids.values():
                            uuid = None
                        if uuid is not None:
                            self.ids[item.GetUniqueId()] = uuid

                    media = item.GetMediaPoolItem()
                    path = media.GetClipProperty("File Path") if media else ""
                    # A video file on a video track is a video, not a camera clip: camera clips
                    # show BBS' placeholder.
                    own = "video" if bbs_kind == "camera" and path and not is_placeholder(path) else bbs_kind
                    clip = {"kind": own, "layer": track - 1, "title": item.GetName(),
                            "tick": to_ticks(item.GetStart() - base, fps),
                            "duration": to_ticks(item.GetDuration(), fps),
                            "enabled": bool(item.GetClipEnabled())}
                    clip["offset"] = to_ticks(item.GetLeftOffset(), fps)
                    if own == "camera":
                        clip["src"] = os.path.abspath(path) if path else ""

                    if uuid is None or uuid in linked:
                        clip["path"] = path
                        foreign.append((clip, item))
                    else:
                        clip["uuid"] = uuid
                        linked[uuid] = (clip, item)

        return {k: v[0] for k, v in linked.items()}, {k: v[1] for k, v in linked.items()}, foreign

    def _append(self, timeline, clip):
        fps, base = self.fps(timeline), timeline.GetStartFrame()
        length = to_frames(clip["duration"], fps)

        if clip["kind"] == "audio":
            media, kind, media_type = self.media_item(clip["audio_path"]), "audio", 2
            start = to_frames(clip.get("offset", 0), fps)
        elif clip["kind"] == "video":
            # startFrame/endFrame count the FILE's frames: a 30 fps video on a 24 fps timeline
            # came out 2.4 s long instead of 3 s (measured 2026-10-14 in Resolve 21).
            media, kind, media_type = self.media_item(clip["video_path"]), "video", 1
            rate = float(media.GetClipProperty("FPS") or 0) or fps
            start = to_frames(clip.get("offset", 0), rate)
            length = to_frames(clip["duration"], rate)
        else:
            # Cut at the film time from the film's own media, so a render of the film shows here.
            media, kind, media_type = self.media_item(self.camera_path()), "video", 1
            rate = float(media.GetClipProperty("FPS") or 0) or fps
            start = to_frames(clip["tick"], rate)
            length = to_frames(clip["duration"], rate)

        self._ensure_tracks(timeline, kind, clip["layer"] + 1)
        placed = self.pool.AppendToTimeline([{
            "mediaPoolItem": media, "startFrame": start, "endFrame": start + length,
            "trackIndex": clip["layer"] + 1, "mediaType": media_type,
            "recordFrame": base + to_frames(clip["tick"], fps)}]) or []

        if len(placed) != 1 or not placed[0]:
            raise RuntimeError("Resolve did not place clip %r" % clip.get("title"))

        item = placed[0]
        self.ids[item.GetUniqueId()] = clip["uuid"]
        if clip.get("title"):
            item.SetName(clip["title"])
        if not clip.get("enabled", True):
            item.SetClipEnabled(False)
        mark(item, clip["uuid"])
        return item

    def apply(self, film):
        """Make the film's timeline equal to the BBS snapshot. Returns what changed.

        Changed placement means delete + append (the API cannot move an item); a clip whose
        placement is unchanged keeps its Resolve item and everything done to it there.
        """
        self.claim(film)
        timeline = self.timeline_for(film)
        if "placeholder" in film:
            self.placeholder_path = film["placeholder"]
        self.camera_media = film.get("camera_media") or None
        if self.camera_media and self.placeholder_path:
            media = self.media_item(self.camera_media)
            # BBS rendered the film over it: Resolve re-reads the file, every camera clip cut
            # from it shows the film and keeps what was done to it here.
            # Before reading the timeline: ReplaceClip renames every item to the file's name
            # (measured in Resolve 21.1), and only a read after it puts the titles back below.
            if stale(media, self.camera_media) and not media.ReplaceClip(self.camera_media):
                raise RuntimeError("Resolve could not re-read the film render %s" % self.camera_media)
        current, items, foreign = self.read(timeline)
        failed = {}
        self.forms = {}
        for clip in film["clips"]:
            try:
                self.forms[clip["uuid"]] = self.shown(clip)
            except RuntimeError as error:
                failed[clip["uuid"]] = str(error)
                self.forms[clip["uuid"]] = None
        wanted = {uuid: form[0] for uuid, form in self.forms.items() if form is not None}
        removed, added, changed = [], [], []

        # After a BBS restart every clip has a new session uuid. An item that sits exactly where
        # an unbound clip wants to be, under the same name, IS that clip: rebind it instead of
        # replacing it, so what was done to it in Resolve survives.
        for uuid in [u for u in current if u not in wanted]:
            for new, clip in wanted.items():
                if new not in current and placement(clip) == placement(current[uuid]) \
                        and clip.get("title", "") == current[uuid]["title"]:
                    item = items.pop(uuid)
                    mark(item, new)
                    self.ids[item.GetUniqueId()] = new
                    current[new] = dict(current.pop(uuid), uuid=new)
                    items[new] = item
                    break

        # The same for an item that lost its marker -- Ctrl+Z in Resolve takes back the marker
        # the link set last (measured 2026-10-12): left as the user's own, the clip was laid a
        # second time on top of it and Resolve refused that one.
        for clip, item in foreign:
            for new, want in wanted.items():
                if new not in current and placement(want) == placement(clip) \
                        and want.get("title", "") == clip["title"]:
                    mark(item, new)
                    self.ids[item.GetUniqueId()] = new
                    current[new] = {k: v for k, v in clip.items() if k != "path"} | {"uuid": new}
                    items[new] = item
                    break

        # Without a placeholder (BBS is still making it) camera clips can be neither placed nor
        # judged: leave the existing ones alone instead of clearing the track.
        deferred = {u for u, c in wanted.items() if c["kind"] == "camera" and not self.camera_path()}

        for uuid, clip in current.items():
            if uuid in deferred:
                continue
            if uuid not in wanted or placement(clip) != placement(wanted[uuid]):
                removed.append(uuid)

        if removed and not timeline.DeleteClips([items[u] for u in removed], False):
            raise Busy("Resolve refused to delete %d items (a dialog open?)" % len(removed))
        self.ids = {k: v for k, v in self.ids.items() if v not in removed}

        for uuid, clip in wanted.items():
            if uuid in deferred and uuid not in current:
                continue
            if uuid not in current or uuid in removed:
                # One clip Resolve cannot take must not stop the others.
                try:
                    self._append(timeline, clip)
                    added.append(uuid)
                except RuntimeError as error:
                    failed[uuid] = str(error)
                continue

            item = items[uuid]
            if current[uuid]["title"] != clip.get("title", ""):
                item.SetName(clip.get("title", ""))
                changed.append(uuid)
            if current[uuid]["enabled"] != clip.get("enabled", True):
                item.SetClipEnabled(clip.get("enabled", True))
                changed.append(uuid)

        # Not in Resolve because it could not be placed (camera clip still waiting for the
        # placeholder, or Resolve refused it) is not "deleted by the user": with a form left
        # here the next poll reported it removed and BBS deleted the clip. Measured 2026-10-12:
        # the camera clip vanished from BBS when the first film arrived before BBS' HELLO.
        for uuid in failed:
            self.forms[uuid] = None
        for uuid in deferred:
            if uuid not in current:
                self.forms[uuid] = None

        return {"removed": [u for u in removed if u not in wanted], "placed": added,
                "changed": sorted(set(changed)), "deferred": sorted(deferred), "failed": failed,
                "silent": sorted(u for u, f in self.forms.items() if f is None and u not in failed)}


def own_work(project):
    """Does the project hold an edit of the user's: a timeline the link did not make, with
    anything on it? A project the link made is its own, whatever was added to it."""
    if project.GetName().startswith(PROJECT_PREFIX):
        return False
    for i in range(1, need(project.GetTimelineCount(), "timelines") + 1):
        timeline = need(project.GetTimelineByIndex(i), "timeline %d" % i)
        if timeline.GetName().startswith(TIMELINE_PREFIX):
            continue
        for kind in ("video", "audio", "subtitle"):
            for track in range(1, need(timeline.GetTrackCount(kind), "track count") + 1):
                if need(timeline.GetItemListInTrack(kind, track), "%s track %d" % (kind, track)):
                    return True
    return False


_pointer = None


def mouse_held():
    """True while a mouse button is down on Resolve's screen: a drag is under way.

    Resolve's API reports every intermediate state of a drag, the dragged clip already trimming
    its neighbours. The link took those as edits, sent them to BBS and wrote BBS' answer back
    mid-drag: the drag was undone and a cut-off copy of the clip stayed behind (measured
    2026-10-12; the same drag with the link stopped landed cleanly). X11 and Windows; elsewhere
    it never holds."""
    global _pointer
    import ctypes
    import ctypes.util
    if sys.platform == "win32":
        return any(ctypes.windll.user32.GetAsyncKeyState(key) & 0x8000 for key in (0x01, 0x02, 0x04))
    if _pointer is None:
        _pointer = False
        name = ctypes.util.find_library("X11")
        if name and os.environ.get("DISPLAY"):
            x11 = ctypes.cdll.LoadLibrary(name)
            x11.XOpenDisplay.restype = ctypes.c_void_p
            x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
            x11.XDefaultRootWindow.restype = ctypes.c_ulong
            x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
            x11.XQueryPointer.argtypes = [ctypes.c_void_p, ctypes.c_ulong] + [ctypes.c_void_p] * 7
            display = x11.XOpenDisplay(None)
            if display:
                _pointer = (x11, display, x11.XDefaultRootWindow(display))
    if not _pointer:
        return False
    x11, display, root = _pointer
    window, number, mask = ctypes.c_ulong(), ctypes.c_int(), ctypes.c_uint()
    x11.XQueryPointer(display, root, ctypes.byref(window), ctypes.byref(window), ctypes.byref(number),
                      ctypes.byref(number), ctypes.byref(number), ctypes.byref(number), ctypes.byref(mask))
    return bool(mask.value & 0x1F00)                  # Button1Mask .. Button5Mask


def fs(path):
    """A path for the operating system. Inside Resolve the script host runs with an ASCII file
    system encoding: a sound named with an emoji ended the link (UnicodeEncodeError in
    getmtime, 2026-10-15). Bytes in UTF-8 reach the file whatever the encoding."""
    return path.encode("utf-8", "surrogateescape") if isinstance(path, str) else path


def stale(item, path):
    """Is the file newer than what Resolve read? Resolve stores seconds as text."""
    import time
    seen = item.GetClipProperty("Date Modified") or ""
    try:
        read_at = time.mktime(time.strptime(seen, "%a %b %d %H:%M:%S %Y"))
    except ValueError:
        return False
    return os.path.getmtime(fs(path)) - read_at >= 1.0


MEDIA_PATHS = ("audio_path", "video_path")


def media_path(clip):
    return clip.get("audio_path") or clip.get("video_path") or ""


def is_placeholder(path):
    """What BBS cuts camera clips from: <BBS settings>/davinci/placeholder-<fps>.mp4, or a film's
    own media there (film-<id>-<fps>.mp4: the placeholder until BBS renders the film over it)."""
    folder, name = os.path.split(path)
    return name.startswith(("placeholder-", "film-")) and os.path.basename(folder) == "davinci"


def mark(item, uuid):
    """Give an item our identity marker; a copy made in Resolve brings the original's along,
    which sits on the same frame and would make Resolve refuse the new one."""
    for marker in (item.GetMarkers() or {}).values():
        data = marker.get("customData") or ""
        if data.startswith(MARKER_PREFIX):
            item.DeleteMarkerByCustomData(data)
    if not item.AddMarker(0, "Blue", "bbs", "", 1, MARKER_PREFIX + uuid):
        raise RuntimeError("Resolve refused the identity marker on %r" % item.GetName())


def placement(clip):
    """What decides where an item sits; any difference means delete + append in Resolve."""
    return (clip["kind"], clip["layer"], clip["tick"], clip["duration"], clip.get("offset", 0),
            clip.get("src", "") if clip["kind"] == "camera" else "")


class Session:
    """The Resolve half of the live link for one film: applies BBS snapshots, reports edits.

    Order of trust: BBS owns the film. A snapshot is applied only once BBS has seen every edit
    this side reported (its `ack` reaches our `seq`); an older snapshot would undo the user's
    edit in Resolve and cost the item everything done to it there.
    """

    def __init__(self, link, new_uuid=None):
        self.link = link
        self.film = None
        self.timeline = None
        self.known = {}
        self.seq = 0
        self.new_uuid = new_uuid or (lambda: __import__("uuid").uuid4().hex)
        self.refused = set()
        self.playhead = None
        self.project_id = None

    def left_project(self):
        """The user opened another project in Resolve since the film was laid out: forget the
        old project's timeline and say so, so the last film is laid out again in this one.
        Measured 2026-10-11: without this the link kept asking the closed project's timeline
        and waited for ever ("busy")."""
        if self.film is None or self.project_id is None:
            return False
        if self.link.project.GetUniqueId() == self.project_id:
            return False
        self.timeline, self.playhead, self.project_id = None, None, None
        self.known, self.forms = {}, {}
        return True

    def disown(self, uuid):
        """BBS could not take a sound we offered: make the item the user's own again, for good."""
        if self.timeline is None:
            return
        _, items, _ = self.link.read(self.timeline)
        item = items.get(uuid)
        if item is not None:
            _unmark(item)
            self.refused.add(item.GetUniqueId())
            self.link.ids.pop(item.GetUniqueId(), None)
        self.known.pop(uuid, None)
        self.forms.pop(uuid, None)

    def on_film(self, film):
        """Apply a BBS snapshot unless it predates an edit we reported. Returns the changes."""
        if film.get("ack", 0) < self.seq:
            return None

        changes = self.link.apply(film)
        self.film = film
        self.timeline = self.link.timeline_for(film)
        self.project_id = self.link.project.GetUniqueId()
        if self.playhead is None:
            # Where Resolve stands now is not a move: only what the user does from here is.
            fps = self.link.fps(self.timeline)
            self.playhead = tc_to_frame(need(self.timeline.GetCurrentTimecode(), "playhead"), fps)
            # After a project opens, Resolve's viewer stays black until the playhead moves
            # (measured 2026-10-11 in BBS' tab): one frame there and back makes it draw.
            self.timeline.SetCurrentTimecode(frame_to_tc(self.playhead + 1, fps))
            self.timeline.SetCurrentTimecode(frame_to_tc(self.playhead, fps))
        # What BBS holds (sent back verbatim as the base of an edit) and what Resolve shows.
        self.known = {c["uuid"]: {k: v for k, v in c.items() if k not in MEDIA_PATHS}
                      for c in film["clips"]}
        self.forms = dict(self.link.forms)
        return changes

    def move_playhead(self, tick):
        """BBS moved its cursor: put Resolve's playhead on the same moment."""
        if self.timeline is None:
            return
        fps = self.link.fps(self.timeline)
        frame = self.timeline.GetStartFrame() + to_frames(tick, fps)
        self.timeline.SetCurrentTimecode(frame_to_tc(frame, fps))
        self.playhead = tc_to_frame(need(self.timeline.GetCurrentTimecode(), "playhead"), fps)

    def poll_playhead(self):
        """The tick Resolve's playhead moved to since we last agreed, or None."""
        if self.timeline is None or self.playhead is None:
            return None
        fps = self.link.fps(self.timeline)
        frame = tc_to_frame(need(self.timeline.GetCurrentTimecode(), "playhead"), fps)
        if frame == self.playhead:
            return None
        self.playhead = frame
        return max(0, to_ticks(frame - self.timeline.GetStartFrame(), fps))

    def poll(self):
        """What the user changed in Resolve since the last look, or None.

        {"film_id", "seq", "changed": [{"uuid", "base", "now"}], "removed": [{"uuid", "base"}],
         "added": [clip with a fresh uuid and "path"]}. Sounds and videos are adopted from Resolve
        (a video becomes a video form in BBS); the user's titles and generators have no file.
        """
        if self.timeline is None:
            return None

        current, _, foreign = self.link.read(self.timeline)
        changed, removed, added = [], [], []

        for uuid, base in list(self.known.items()):
            shown = self.forms.get(uuid)
            if shown is None:
                continue                      # silent in Resolve: nothing the user could edit
            if uuid not in current:
                removed.append({"uuid": uuid, "base": base})
                del self.known[uuid]
            elif edited(shown[0], current[uuid]):
                now = back(base, shown, current[uuid])
                changed.append({"uuid": uuid, "base": base, "now": now})
                self.known[uuid] = now
                self.forms[uuid] = self.link.shown(dict(now, **{k: shown[0][k] for k in MEDIA_PATHS
                                                                 if k in shown[0]}))

        for clip, item in foreign:
            if clip["kind"] == "camera" or not clip.get("path") or item.GetUniqueId() in self.refused:
                continue
            clip["uuid"] = self.new_uuid()
            self.link.ids[item.GetUniqueId()] = clip["uuid"]
            mark(item, clip["uuid"])
            plain = {k: v for k, v in clip.items() if k != "path"}
            self.known[clip["uuid"]] = plain
            self.forms[clip["uuid"]] = (dict(plain, **{clip["kind"] + "_path": clip["path"]}), 0, 0)
            added.append(clip)

        if not (changed or removed or added):
            return None

        self.seq += 1
        return {"film_id": self.film["film_id"], "seq": self.seq, "changed": changed,
                "removed": removed, "added": added}


def _unmark(item):
    for marker in (item.GetMarkers() or {}).values():
        data = marker.get("customData") or ""
        if data.startswith(MARKER_PREFIX):
            item.DeleteMarkerByCustomData(data)


def back(base, shown, now):
    """An edit made in Resolve, in BBS terms. `shown` is (form, pad, tail) as placed.

    The silence Resolve cannot show stays where the user did not touch: moving a clip keeps its
    late start, trimming its head starts it at the new point, trimming its tail drops the silence
    after the file's end.
    """
    form, pad, tail = shown
    out = dict(base, layer=now["layer"], title=now["title"], enabled=now["enabled"])
    if base["kind"] == "camera":
        out.update(tick=now["tick"], duration=now["duration"])
        return out

    head = now["offset"] == form["offset"]
    end = now["offset"] + now["duration"] == form["offset"] + form["duration"]
    keep_pad = pad if head else 0
    out["tick"] = now["tick"] - keep_pad
    out["offset"] = now["offset"] - keep_pad
    out["duration"] = now["duration"] + keep_pad + (tail if end else 0)
    return out


def trace(way, message):
    """BBS_LINK_TRACE=1 in BBS' environment: every message in and out, timed, in the link's log --
    how the edits that cut a film short were traced to Resolve itself (2026-10-12)."""
    if not os.environ.get("BBS_LINK_TRACE"):
        return
    import time
    fields = message.get("fields") or {}
    brief = {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in fields.items() if k != "media"}
    print("%.3f %s %s %s" % (time.monotonic(), way, message.get("type"), brief), flush=True)


def edited(before, after):
    return placement(before) != placement(after) or any(
        before.get(k) != after.get(k) for k in ("title", "enabled"))


def media_list(link):
    """Everything in the project's media pool, as BBS lists it: name, file, kind, folder."""
    for result in media_scan(link):
        if result is not None:
            return result


def media_scan(link):
    """media_list() in steps: yields None after each pool item, the list at the end.

    One item costs a scripting call. Right after a project first opened in BBS' tab, Resolve took
    50..500 ms per call for minutes (measured 2026-10-11; later loads: under 1 ms): 166 items
    took longer than the 5 s between scans, and the link did nothing else. In steps, film and
    playhead go on between."""
    out, todo = [], [(link.pool.GetRootFolder(), "")]
    while todo:
        folder, parent = todo.pop()
        path = parent + "/" + folder.GetName() if parent else folder.GetName()
        for item in folder.GetClipList() or []:
            props = item.GetClipProperty() or {}
            kind = (props.get("Type") or "").lower()
            # Timelines, compound and multicam clips live in the pool too (108 of 166 items in the
            # test project); none of them is a file BBS could use. "Video + Audio" is a video.
            if not props.get("File Path") or any(k in kind for k in ("timeline", "compound", "multicam")):
                continue
            out.append({"name": item.GetName(), "path": props.get("File Path") or "",
                        "kind": "video" if "video" in kind else "audio" if "audio" in kind else "still",
                        "duration": props.get("Duration") or "", "folder": path, "source": "pool"})
            yield None
        todo += [(sub, path) for sub in folder.GetSubFolderList() or []]
    yield sorted(out, key=lambda m: (m["folder"], m["name"]))


# -- Power Bins ---------------------------------------------------------------
# Not in the scripting API. They live in the user-level database (User.db, SQLite) of the
# project library: Sm2MpMedia -> Sm2MpFolder, the file in BtAudioInfo/BtVideoInfo.Clip as a
# zstd frame holding protobuf (1 = directory, 2 = file name). Measured 2026-10-11: 64 entries,
# all decoded, all files present.

def library_roots():
    home = os.path.expanduser("~")
    support = {"win32": os.path.join(os.environ.get("APPDATA", home), "Blackmagic Design", "DaVinci Resolve", "Support"),
               "darwin": os.path.join(home, "Library", "Application Support", "Blackmagic Design", "DaVinci Resolve")}
    base = support.get(sys.platform, os.path.join(home, ".local", "share", "DaVinciResolve"))
    return [os.path.join(base, "Support", "Resolve Project Library"), os.path.join(base, "Resolve Project Library")]


def user_db():
    """The active library's User.db: the most recently written one (an old library can sit beside
    it -- on this PC one without Support/, which read as 'empty')."""
    import glob
    found = [p for root in library_roots()
             for p in glob.glob(os.path.join(root, "Resolve Projects", "Users", "*", "User.db"))]
    return max(found, key=os.path.getmtime) if found else None


def unzstd(frame):
    for load in (lambda: __import__("compression.zstd", fromlist=["zstd"]).decompress,
                 lambda: __import__("zstandard").ZstdDecompressor().decompressobj().decompress):
        try:
            return load()(frame)
        except ImportError:
            continue
    return unzstd_raw(frame)


def unzstd_raw(frame):
    """Enough zstd for frames made of raw and RLE blocks (what Resolve writes for short blobs);
    a compressed block needs Python 3.14 or the zstandard package and fails loudly."""
    fhd = frame[4]
    single, dict_size = (fhd >> 5) & 1, (0, 1, 2, 4)[fhd & 3]
    size_bytes = (1 if single else 0, 2, 4, 8)[fhd >> 6]
    i = 5 + (0 if single else 1) + dict_size + size_bytes
    out = b""
    while True:
        header = int.from_bytes(frame[i:i + 3], "little")
        last, kind, size = header & 1, (header >> 1) & 3, header >> 3
        i += 3
        if kind == 0:
            out, i = out + frame[i:i + size], i + size
        elif kind == 1:
            out, i = out + frame[i:i + 1] * size, i + 1
        else:
            raise RuntimeError("compressed zstd block: needs Python 3.14 or the zstandard package")
        if last:
            return out


def protobuf_strings(buf):
    """Length-delimited fields of a protobuf message, by field number (first occurrence)."""
    out, i = {}, 0
    def varint():
        nonlocal i
        value = shift = 0
        while True:
            b = buf[i]; i += 1
            value |= (b & 0x7F) << shift; shift += 7
            if b < 0x80:
                return value
    while i < len(buf):
        key = varint()
        number, wire = key >> 3, key & 7
        if wire == 2:
            n = varint(); out.setdefault(number, buf[i:i + n]); i += n
        elif wire == 0:
            varint()
        elif wire in (1, 5):
            i += 8 if wire == 1 else 4
        else:
            break
    return out


def power_bins(db=None):
    """Every Power Bin item whose file exists, as media_list() lists pool items."""
    import shutil, sqlite3, tempfile
    db = db or user_db()
    if not db:
        return []
    copy = os.path.join(tempfile.mkdtemp(prefix="bbs-powerbins-"), "User.db")
    shutil.copyfile(db, copy)                 # never hold Resolve's live database open
    out = []
    try:
        con = sqlite3.connect("file:%s?mode=ro" % copy, uri=True)
        for table, kind in (("BtAudioInfo", "audio"), ("BtVideoInfo", "video")):
            rows = con.execute("select m.Name, f.Name, i.Clip from Sm2MpMedia m "
                               "join Sm2MpFolder f on f.Sm2MpFolder_id = m.MpFolder "
                               "join %s i on i.Sm2MpMedia_id = m.Sm2MpMedia_id" % table)
            for name, folder, clip in rows:
                try:
                    fields = protobuf_strings(unzstd(clip[clip.index(b"\x28\xb5\x2f\xfd"):]))
                    path = os.path.join(fields[1].decode("utf-8"), fields[2].decode("utf-8"))
                except (ValueError, KeyError, IndexError, RuntimeError):
                    continue
                if os.path.isfile(fs(path)):
                    out.append({"name": name, "path": path, "kind": kind, "duration": "",
                                "folder": "Power Bins/" + folder, "source": "powerbin"})
        con.close()
    finally:
        shutil.rmtree(os.path.dirname(copy), ignore_errors=True)
    return sorted(out, key=lambda m: (m["folder"], m["name"]))


class Client:
    """The link's connection to BBS: NUL-delimited JSON over loopback, like the Blender bridge."""

    MEDIA_EVERY = 5.0
    MEDIA_STEP = 0.1       # seconds of pool scanning per round

    def __init__(self, app, port, interval=0.25, log=print, windows=False):
        self.app, self.port, self.interval, self.log = app, port, interval, log
        self.windows = windows
        self.link = Link(app, None)
        self.link.say = self.tell
        self.session = Session(self.link)
        self.socket = None
        self.buffer = b""
        self.seq = 0
        self.media = None
        self.media_at = 0.0
        self.media_job = None
        self.failed = {}
        self.pending_film = None
        self.pending_playhead = None
        self.busy = None

    def tell(self, text):
        """Something the user must not miss: in the console and, started from Workspace >
        Scripts, in a Resolve window they close (Studio; the free Resolve has no UIManager)."""
        self.log("BBS Link: " + text)
        if not self.windows:
            return
        try:
            bmd = getattr(sys.modules.get("__main__"), "bmd", None)
            if bmd is None:
                sys.path.append("/opt/resolve/Developer/Scripting/Modules")
                import DaVinciResolveScript as bmd
            ui = self.app.Fusion().UIManager
            dispatcher = bmd.UIDispatcher(ui)
            window = dispatcher.AddWindow(
                {"ID": "BBSLinkNote", "WindowTitle": "BBS Link", "Geometry": [300, 300, 520, 150]},
                ui.VGroup([ui.Label({"Text": text, "WordWrap": True}),
                           ui.HGroup({"Weight": 0}, [ui.HGap(0, 1), ui.Button({"ID": "OK", "Text": "OK"})])]))

            def close(event):
                dispatcher.ExitLoop()
            window.On.OK.Clicked = close
            window.On.BBSLinkNote.Close = close
            window.Show()
            dispatcher.RunLoop()
            window.Hide()
        except Exception as error:
            self.log("BBS Link: no window for this message (%s)" % error)

    def send(self, kind, fields):
        import json
        self.seq += 1
        message = {"type": kind, "uuid": "film", "seq": self.seq, "origin": "davinci", "fields": fields}
        trace("out", message)
        self.socket.sendall(json.dumps(message).encode("utf-8") + b"\0")

    def connect(self):
        import socket
        self.socket = socket.create_connection(("127.0.0.1", self.port), timeout=5)
        self.socket.setblocking(False)
        self.buffer = b""
        self.media = None
        project = self.link.project
        self.send("HELLO", {"app": "davinci", "product": self.app.GetProductName(),
                            "version": self.app.GetVersionString(), "project": project.GetName(),
                            "fps": str(project.GetSetting("timelineFrameRate"))})
        self.log("BBS Link: connected to BBS on port %d" % self.port)

    def receive(self):
        import json
        while True:
            try:
                chunk = self.socket.recv(65536)
            except BlockingIOError:
                break
            if not chunk:
                raise ConnectionError("BBS closed the link")
            self.buffer += chunk
        *frames, self.buffer = self.buffer.split(b"\0")
        messages = [json.loads(f.decode("utf-8")) for f in frames if f]
        for message in messages:
            trace("in", message)
        return messages

    def step(self):
        """One round: apply what BBS sent, report what changed here, refresh the media list."""
        import time
        for message in self.receive():
            fields = message.get("fields") or {}
            if message.get("type") == "FILM":
                self.pending_film = fields          # the newest film wins; applied below
            elif message.get("type") == "PLAYHEAD":
                self.pending_playhead = fields
            elif message.get("type") == "QUIT":
                # BBS is closing the Resolve it runs in its tab: save first, then quit, so no
                # edit is lost and nothing keeps running unseen.
                # Not self.link.project: in the project manager there is none, and its Busy
                # kept Resolve from ever quitting (measured 2026-10-11).
                manager = self.app.GetProjectManager()
                project = manager and manager.GetCurrentProject()
                if project:
                    self.log("BBS Link: BBS closes Resolve; project %r saved: %s"
                             % (project.GetName(), manager.SaveProject()))
                self.app.Quit()
                raise SystemExit(0)
            elif message.get("type") == "CLIPS_RESULT":
                for no in fields.get("refused") or []:
                    self.session.disown(no["uuid"])
                    self.log("BBS Link: BBS could not take a file, it stays yours in Resolve: %s"
                             % no.get("error"))
                if not fields.get("ok") or fields.get("conflicts"):
                    self.log("BBS Link: BBS answered %s" % fields)

        # Everything that talks to Resolve may meet a dialog: then it waits and tries again on
        # the next round instead of ending the link (it ended it, measured in the GUI test).
        try:
            self.resolve_round()
            self.busy = None
        except Busy as error:
            if str(error) != self.busy:
                self.log("BBS Link: %s -- waiting" % error)
            self.busy = str(error)

    def resolve_round(self):
        import time
        # The playhead follows a drag on the ruler; the film waits until the button is up.
        held = mouse_held()
        if not held and self.pending_film is None and self.session.left_project():
            self.log("BBS Link: Resolve opened project %r -- laying the film out there"
                     % self.link.project.GetName())
            self.pending_film = dict(self.session.film, ack=self.session.seq)
        if self.pending_film is not None and not held:
            fields = self.pending_film
            changes = self.session.on_film(fields)
            self.pending_film = None
            if changes and (changes["placed"] or changes["removed"] or changes["changed"]):
                titles = {c["uuid"]: c.get("title", "") for c in fields.get("clips", [])}
                self.log("BBS Link: film updated: placed %s, removed %d, changed %s"
                         % ([titles.get(u, u) for u in changes["placed"]], len(changes["removed"]),
                            [titles.get(u, u) for u in changes["changed"]]))
            if changes and changes["failed"] and changes["failed"] != self.failed:
                for uuid, error in changes["failed"].items():
                    self.log("BBS Link: clip %s not placed: %s" % (uuid, error))
            if changes:
                self.failed = changes["failed"]

        if self.pending_playhead is not None:
            fields = self.pending_playhead
            if self.session.film and fields.get("film_id") == self.session.film["film_id"]:
                self.session.move_playhead(fields["tick"])
            self.pending_playhead = None

        # The playhead first: one call, before the many that read every clip.
        moved = self.session.poll_playhead()
        if moved is not None:
            self.send("PLAYHEAD", {"film_id": self.session.film["film_id"], "tick": moved})

        report = None if held else self.session.poll()
        if report:
            # Each edit sent to BBS changes the film there: say which field went from what to what.
            for c in report["changed"]:
                diff = {k: (c["base"].get(k), v) for k, v in c["now"].items() if c["base"].get(k) != v}
                self.log("BBS Link: edit to BBS: %s %s" % (c["base"].get("title"), diff))
            self.send("CLIPS_CHANGED", report)

        if self.media_job is None and time.time() - self.media_at >= self.MEDIA_EVERY:
            self.media_job = media_scan(self.link)
        deadline = time.time() + self.MEDIA_STEP
        while self.media_job is not None and time.time() < deadline:
            try:
                pool = next(self.media_job)
            except BaseException:
                self.media_job = None     # a generator that raised is finished; start over
                raise
            if pool is None:
                continue
            self.media_job, self.media_at = None, time.time()
            media = pool + power_bins()
            if media != self.media:
                self.send("MEDIA", {"items": media})
                self.media = media

    def claim_port(self):
        """Only the newest BBS Link for a BBS speaks to it. Started three times (2026-10-15), three
        links laid the same film into one timeline, each deleted the others' items, reported that
        as the user's edit -- and BBS deleted the tracker clip. Each start writes its token here;
        a link that finds another token gives way."""
        import tempfile
        import time
        self.token = "%d %f" % (os.getpid(), time.time())
        self.lock = os.path.join(tempfile.gettempdir(), "bbs-link-%d.owner" % self.port)
        with open(self.lock, "w") as f:
            f.write(self.token)

    def superseded(self):
        try:
            with open(self.lock) as f:
                return f.read() != self.token
        except OSError:
            return False

    def run(self, stop=lambda: False):
        import time
        last = None
        if getattr(self, "token", None) is None:
            self.claim_port()
        while not stop():
            if self.superseded():
                self.log("BBS Link: a newer BBS Link took over, this one stops")
                if self.socket is not None:
                    self.socket.close()
                return
            try:
                if self.socket is None:
                    self.connect()
                    last = None
                self.step()
            except (OSError, ConnectionError) as error:
                # Every new kind of failure is said once: a link that silently retries
                # forever looks exactly like one that works.
                if self.socket is not None:
                    self.log("BBS Link: connection lost (%s), retrying" % error)
                    self.socket.close()
                elif str(error) != last:
                    self.log("BBS Link: cannot reach BBS on port %d (%s), retrying" % (self.port, error))
                last = str(error)
                self.socket = None
                time.sleep(2)
                continue
            time.sleep(self.interval)


def main(app=None, port=None):
    import time
    inside = app is not None              # Workspace > Scripts passes `resolve`; BBS' tab does not
    port = port or int(os.environ.get("BBS_DAVINCI_PORT", "9980"))
    # Started together with Resolve (BBS' tab): Resolve takes scripts only once it is up.
    # Every 0.25 s, not every second: Resolve's own start is ~13 s, the rest is our waiting.
    for attempt in range(240):
        try:
            app = app or resolve_app()
            break
        except RuntimeError as error:
            if attempt == 239:
                raise SystemExit("BBS Link: %s -- with the free Resolve, start Workspace > Scripts > "
                                 "BBS Link inside Resolve instead" % error)
            time.sleep(0.25)
    # BBS' tab took over from a Resolve on the desktop: open what that one had open. A fresh
    # Resolve shows its project manager and refuses project calls for a few seconds after start.
    name = os.environ.get("BBS_DAVINCI_PROJECT")
    for attempt in range(120 if name else 0):
        manager = app.GetProjectManager()
        current = manager and manager.GetCurrentProject()
        # LoadProject answers None for the project already open (measured in the GUI).
        if manager and (current and current.GetName() == name or manager.LoadProject(name)):
            print("BBS Link: opened project %r, as on the desktop before" % name, flush=True)
            break
        time.sleep(0.25)
    else:
        if name:
            print("BBS Link: could not open project %r -- open it in Resolve" % name, flush=True)
    Client(app, port, windows=inside).run()


if __name__ == "__main__":
    main(port=int(sys.argv[1]) if len(sys.argv) > 1 else None)
