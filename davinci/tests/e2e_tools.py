"""Helpers for e2e_live.py: drive a BBS instance over EXEC, the Resolve side step by step."""
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(HERE)), "tools"))
import kanal  # noqa: E402

FILM = "com.blenderbridge.FilmIdentity.resolveOpenFilm()"
MC = "net.minecraft.client.Minecraft.getInstance()"
RESULTS = []


def check(name, condition, detail=""):
    RESULTS.append(bool(condition))
    print("  %s %s%s" % ("OK  " if condition else "FAIL", name, "" if condition else "  -> %s" % detail))
    return condition


class Bbs:
    def __init__(self, port):
        self.port = port

    def ev(self, code):
        ok, text = kanal.exec_code(self.port, code, 60, uuid="davinci-e2e", mit_status=True)
        if not ok:
            raise RuntimeError("EXEC rejected %r: %s" % (code[:80], text))
        return text

    def later(self, statement):
        """Run on the client thread (EXEC is not the render thread on 26.3)."""
        self.ev("%s.execute(() -> { %s })" % (MC, statement))

    def snapshot(self):
        raw = self.ev("com.davincilink.DavinciFilmSync.encode(%s).toString()" % FILM)
        return json.loads(json.loads(raw) if raw.startswith('"') else raw)

    def clip(self, index):
        return "%s.camera.get().get(%d)" % (FILM, index)


def converge(client, until, seconds=20, what=""):
    """Drive the Resolve side until `until()` holds; False if it never does."""
    end = time.time() + seconds
    while time.time() < end:
        client.step()
        if until():
            return True
        time.sleep(0.25)
    if what:
        print("    (gave up waiting for %s)" % what)
    return False


def mirrored(link, timeline, snapshot):
    """Resolve's linked items against what BBS holds, clip by clip, as Resolve must show it."""
    linked, _, _ = link.read(timeline)
    want = {}
    for clip in snapshot["clips"]:
        shown = link.shown(clip)
        if shown is not None:
            form = {k: v for k, v in shown[0].items() if k != "audio_path"}
            if form["kind"] == "camera":
                form.pop("offset", None)
            want[clip["uuid"]] = form
    return want, linked
