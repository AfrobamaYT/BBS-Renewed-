"""The DaVinci link end to end: a real BBS instance, a real DaVinci Resolve, both directions.

Needs: a BBS test instance with its film editor reachable over the Blender bridge's EXEC
(bridge port), the DaVinci link on its port, and Resolve (Studio, external scripting) with the
test project open. Never point it at the user's own instance or projects.

    python3 davinci/tests/e2e_live.py --bridge 9899 --link 9999 --film c2

Every check compares numbers from both timelines; nothing is judged by looking.
"""
import argparse
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import bbs_link  # noqa: E402

from e2e_tools import FILM, MC, RESULTS, Bbs, check, converge, mirrored  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bridge", type=int, required=True)
    parser.add_argument("--link", type=int, required=True)
    parser.add_argument("--film", required=True)
    args = parser.parse_args()

    bbs = Bbs(args.bridge)
    app = bbs_link.resolve_app()
    if app.GetProjectManager().GetCurrentProject().GetName() != "BBS-Link-Test":
        sys.exit("Resolve must have the test project BBS-Link-Test open")

    current = bbs.ev("%s == null ? \"-\" : %s.getId()" % (FILM, FILM)).strip('"')
    if current != args.film:
        # Opening a film that is already open re-runs the panel switch, which can crash 26.3's
        # GUI by itself (scissor 0x36, task 113) -- so only open when needed.
        bbs.later('com.blenderbridge.BBSCompat.openFilm("%s");' % args.film)
    end = time.time() + 40
    while bbs.ev("%s == null ? \"-\" : %s.getId()" % (FILM, FILM)).strip('"') != args.film:
        if time.time() > end:
            sys.exit("film %s did not open" % args.film)
        time.sleep(1)

    client = bbs_link.Client(app, args.link, log=lambda m: print("    [link] " + m))
    client.connect()
    link, session = client.link, client.session

    print("\n--- 1. BBS -> Resolve: the film appears clip for clip")
    converge(client, lambda: session.film is not None and session.film.get("placeholder"), 60, "first film")
    converge(client, lambda: False, 2)
    snap = bbs.snapshot()
    want, have = mirrored(link, session.timeline, snap)
    check("every audible clip is in Resolve (%d of %d clips)" % (len(want), len(snap["clips"])),
          set(want) == set(have), "missing %s, extra %s" % (set(want) - set(have), set(have) - set(want)))
    check("tick, length, layer, name, offset and on/off match for each",
          want == have, [(u, want.get(u), have.get(u)) for u in want if want.get(u) != have.get(u)][:3])
    for _ in range(3):
        client.step()
    again = client.session.link.apply(dict(session.film, ack=session.seq))
    check("a second snapshot changes nothing in Resolve", not (again["placed"] or again["removed"]), again)

    print("\n--- 2. BBS edits reach Resolve")
    cam = next(i for i, c in enumerate(snap["clips"]) if c["kind"] == "camera")
    sound = next(i for i, c in enumerate(snap["clips"]) if c["kind"] == "audio" and link.shown(c))
    cam_uuid, sound_uuid = snap["clips"][cam]["uuid"], snap["clips"][sound]["uuid"]
    bbs.later("%s.tick.set(%s.tick.get() + 40); %s.title.set(\"e2e-umbenannt\");"
              % (bbs.clip(cam), bbs.clip(cam), bbs.clip(sound)))

    def bbs_edit_arrived():
        linked, _, _ = link.read(session.timeline)
        return linked.get(cam_uuid, {}).get("tick") == snap["clips"][cam]["tick"] + 40 \
            and linked.get(sound_uuid, {}).get("title") == "e2e-umbenannt"
    check("a moved camera clip and a renamed sound appear in Resolve",
          converge(client, bbs_edit_arrived, 20, "BBS edit"))

    print("\n--- 3. Resolve edits reach BBS, as one undo step")
    _, items, _ = link.read(session.timeline)
    items[sound_uuid].SetName("aus-resolve")
    items[cam_uuid].SetClipEnabled(False)

    def resolve_edit_arrived():
        clips = {c["uuid"]: c for c in bbs.snapshot()["clips"]}
        return clips[sound_uuid]["title"] == "aus-resolve" and clips[cam_uuid]["enabled"] is False
    check("a rename and a switch-off made in Resolve land in BBS",
          converge(client, resolve_edit_arrived, 20, "Resolve edit"))

    bbs.later("mchorse.bbs_mod.ui.dashboard.UIDashboard d = com.blenderbridge.BBSCompat.peekDashboard();"
              " d.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class).getUndoHandler().getUndoManager()"
              ".undo(d.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class).getData());")

    def undone():
        clips = {c["uuid"]: c for c in bbs.snapshot()["clips"]}
        linked, _, _ = link.read(session.timeline)
        return clips.get(sound_uuid, {}).get("title") == "e2e-umbenannt" and clips.get(cam_uuid, {}).get("enabled") \
            and linked.get(sound_uuid, {}).get("title") == "e2e-umbenannt"
    check("one undo in BBS takes back both and Resolve follows", converge(client, undone, 20, "undo"))

    print("\n--- 4. Removing in Resolve removes in BBS")
    linked, items, _ = link.read(session.timeline)
    deleted = linked[sound_uuid]
    session.timeline.DeleteClips([items[sound_uuid]], False)
    check("the deleted sound is gone from BBS",
          converge(client, lambda: sound_uuid not in {c["uuid"] for c in bbs.snapshot()["clips"]}, 20, "delete"))

    print("\n--- 5. A sound placed in Resolve becomes a BBS audio clip")
    # Six seconds: a very short clip makes 26.3's timeline crash on its own (task 113).
    import subprocess, tempfile
    probe = os.path.join(tempfile.mkdtemp(prefix="bbs-e2e-"), "e2e-ton.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                    "sine=frequency=330:sample_rate=48000:duration=8", probe], check=True)
    before = {c["uuid"] for c in bbs.snapshot()["clips"]}
    # Into the gap the deleted sound left, on a layer the film already shows: a sound on a new
    # layer can crash 26.3's timeline by itself (task 113).
    gap = deleted
    link.pool.AppendToTimeline([{"mediaPoolItem": link.media_item(probe), "startFrame": 0,
                                 "endFrame": min(360, bbs_link.to_frames(gap["duration"], 60)),
                                 "trackIndex": gap["layer"] + 1, "mediaType": 2,
                                 "recordFrame": session.timeline.GetStartFrame() + bbs_link.to_frames(gap["tick"], 60)}])
    expect = (gap["layer"], gap["tick"], min(120, gap["duration"]))

    def adopted():
        new = [c for c in bbs.snapshot()["clips"] if c["uuid"] not in before]
        return len(new) == 1 and new[0]["kind"] == "audio" \
            and (new[0]["layer"], new[0]["tick"], new[0]["duration"]) == expect \
            and "/audio/davinci/" in new[0]["audio_path"]
    check("BBS has a new audio clip on the same layer, tick and length, file in assets/audio/davinci",
          converge(client, adopted, 20, "adoption"))
    first = next(c for c in bbs.snapshot()["clips"] if c["uuid"] not in before)
    _, items, _ = link.read(session.timeline)
    item_id = items[first["uuid"]].GetUniqueId()
    converge(client, lambda: False, 3)
    linked, items, _ = link.read(session.timeline)
    check("the adopted item stays the same Resolve item, as long as in BBS",
          items[first["uuid"]].GetUniqueId() == item_id and linked[first["uuid"]]["duration"] == first["duration"],
          (linked[first["uuid"]], first["duration"]))

    print("\n--- 5b. A different sound with the same file name keeps both")
    other = os.path.join(tempfile.mkdtemp(prefix="bbs-e2e-"), "e2e-ton.wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                    "sine=frequency=660:sample_rate=48000:duration=8", other], check=True)
    end = max(c["tick"] + c["duration"] for c in bbs.snapshot()["clips"])
    before = {c["uuid"] for c in bbs.snapshot()["clips"]}
    link.pool.AppendToTimeline([{"mediaPoolItem": link.media_item(other), "startFrame": 0, "endFrame": 120,
                                 "trackIndex": gap["layer"] + 1, "mediaType": 2,
                                 "recordFrame": session.timeline.GetStartFrame() + bbs_link.to_frames(end + 40, 60)}])
    found = {}

    def second():
        new = [c for c in bbs.snapshot()["clips"] if c["uuid"] not in before]
        found.update(new[0] if new else {})
        return len(new) == 1
    converge(client, second, 20, "second adoption")
    clips = {c["uuid"]: c for c in bbs.snapshot()["clips"]}
    check("the second sound gets its own file",
          found.get("audio_path") and found["audio_path"] != clips[first["uuid"]]["audio_path"], found.get("audio_path"))
    check("the first clip still plays the first sound, byte for byte",
          open(clips[first["uuid"]]["audio_path"], "rb").read() == open(probe, "rb").read()
          and open(found["audio_path"], "rb").read() == open(other, "rb").read())

    print("\n--- 5c. An .mp3 placed in Resolve arrives in BBS as a playable .wav")
    mp3 = os.path.join(tempfile.mkdtemp(prefix="bbs-e2e-"), "e2e-musik-%d.mp3" % os.getpid())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                    "sine=frequency=440:sample_rate=44100:duration=6", mp3], check=True)
    end = max(c["tick"] + c["duration"] for c in bbs.snapshot()["clips"])
    before = {c["uuid"] for c in bbs.snapshot()["clips"]}
    link.pool.AppendToTimeline([{"mediaPoolItem": link.media_item(mp3), "startFrame": 0, "endFrame": 120,
                                 "trackIndex": gap["layer"] + 1, "mediaType": 2,
                                 "recordFrame": session.timeline.GetStartFrame() + bbs_link.to_frames(end + 20, 60)}])
    got = {}

    def converted():
        new = [c for c in bbs.snapshot()["clips"] if c["uuid"] not in before]
        got.update(new[0] if new else {})
        return len(new) == 1
    converge(client, converted, 30, "mp3 adoption")
    path = got.get("audio_path", "")
    check("the clip points at a .wav in assets/audio/davinci", path.endswith("/audio/davinci/e2e-musik-%d.wav" % os.getpid()), path)
    check("that .wav is a real RIFF/WAVE file", os.path.isfile(path) and open(path, "rb").read(12)[8:12] == b"WAVE")

    print("\n--- 6. Both sides edit the same clip: BBS wins, Resolve follows")
    snap = bbs.snapshot()
    cam_index = next(i for i, c in enumerate(snap["clips"]) if c["uuid"] == cam_uuid)
    _, items, _ = link.read(session.timeline)
    items[cam_uuid].SetName("resolve-sagt")
    bbs.later("%s.title.set(\"bbs-sagt\");" % bbs.clip(cam_index))
    time.sleep(1.0)                           # BBS changes first, Resolve's report arrives after

    def bbs_won():
        clips = {c["uuid"]: c for c in bbs.snapshot()["clips"]}
        linked, _, _ = link.read(session.timeline)
        return clips[cam_uuid]["title"] == "bbs-sagt" and linked.get(cam_uuid, {}).get("title") == "bbs-sagt"
    check("BBS keeps its title and Resolve shows it", converge(client, bbs_won, 20, "conflict"))

    print("\n--- 8. DaVinci's sounds are in BBS' sound picker")
    # The pool is scanned in 0.1 s steps between rounds (smooth tab); a full pass over a pool of
    # ~260 items took 18 s (measured 2026-10-12). Wait for the item, not for a fixed time.
    def picker():
        names = bbs.ev("new com.google.gson.Gson().toJsonTree(mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel.EXTRA.get(0)"
                       ".names().stream().filter(n -> n.endsWith(\"/e2e-musik-%d.mp3\")).toList()).toString()" % os.getpid())
        return names, json.loads(json.loads(names)) if names.startswith('"') else json.loads(names)
    converge(client, lambda: bool(picker()[1]), 40, "pool media in the picker")
    names, listed = picker()
    check("the .mp3 from the pool is listed as DaVinci/...", len(listed) == 1 and listed[0].startswith("DaVinci/"), names)
    if listed:
        taken = bbs.ev("mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel.EXTRA.get(0)"
                       ".take(\"%s\").toString()" % listed[0]).strip('"')
        check("picking it gives an asset link to the converted .wav",
              taken == "assets:audio/davinci/e2e-musik-%d.wav" % os.getpid(), taken)

    print("\n--- 9. The playhead: BBS cursor and Resolve playhead stay on the same moment")
    panel = "com.blenderbridge.BBSCompat.peekDashboard().getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class)"
    start = session.timeline.GetStartFrame()
    bbs.later("%s.setCursor(123);" % panel)
    check("moving the BBS cursor to tick 123 puts Resolve on frame start+369",
          converge(client, lambda: bbs_link.tc_to_frame(session.timeline.GetCurrentTimecode(), 60) == start + 369,
                   10, "playhead to Resolve"))
    session.timeline.SetCurrentTimecode(bbs_link.frame_to_tc(start + 600, 60))
    check("scrubbing Resolve to frame start+600 puts the BBS cursor on tick 200",
          converge(client, lambda: bbs.ev("%s.getCursor()" % panel).strip('"') == "200", 10, "playhead to BBS"))
    sent = client.seq
    converge(client, lambda: False, 3)
    check("then both stay put: no message ping-pong", client.seq == sent, client.seq - sent)

    print("\n--- 7. The Resolve side restarts: nothing is replaced")
    fresh = bbs_link.Client(app, args.link, log=lambda m: print("    [link2] " + m))
    client.socket.close()
    fresh.connect()
    placed = []
    original = fresh.session.on_film

    def watch(film):
        changes = original(film)
        if changes:
            placed.extend(changes["placed"] + changes["removed"])
        return changes
    fresh.session.on_film = watch
    converge(fresh, lambda: fresh.session.film is not None, 20, "reconnect")
    check("a fresh Resolve-side session re-finds every item", placed == [], placed)
    fresh.socket.close()

    print()
    bad = RESULTS.count(False)
    print("e2e: %d of %d checks %s" % (len(RESULTS) - bad, len(RESULTS), "passed" if not bad else "FAILED"))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
