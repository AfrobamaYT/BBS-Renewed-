// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.blenderbridge.BBSCompat;
import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeServer;
import com.blenderbridge.FilmIdentity;
import com.blenderbridge.actors.FilmBackup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The BBS half of the DaVinci link for the open film.
 *
 * <p>BBS -&gt; Resolve: every tick the open film is encoded; when it differs from what was last
 * sent (or Resolve just connected) the whole film goes out as {@code FILM}. Films hold dozens of
 * clips, and the Resolve side applies a snapshot idempotently.</p>
 *
 * <p>Resolve -&gt; BBS: {@link DavinciEdits}.</p>
 *
 * <p>IDENTITY: BBS clips get no new field (Blender-link invariant). Each clip object carries a
 * uuid for this session only; the Resolve side re-finds its items after a restart.</p>
 */
public final class DavinciFilmSync
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String TYPE_FILM = "FILM";
    public static final String TYPE_CLIPS_CHANGED = "CLIPS_CHANGED";
    public static final String TYPE_CLIPS_RESULT = "CLIPS_RESULT";
    public static final String ORIGIN_DAVINCI = "davinci";

    /** Clips and video replays (DavinciVideos) of the open film, by identity. */
    private static final Map<Object, String> IDS = new IdentityHashMap<>();

    private static int connections;
    private static String lastSent = "";
    private static long ack;
    private static long seq;

    /** The timeline rate Resolve announced in HELLO; the placeholder is made for it. */
    private static String fps = "";

    /** The Resolve project from the last HELLO; kept after a disconnect (the tab reopens it). */
    private static volatile String project = "";

    private DavinciFilmSync()
    {}

    public static void tick(BridgeServer server)
    {
        try
        {
            if (server.connectionCount() != connections)
            {
                connections = server.connectionCount();
                lastSent = "";
                ack = 0;
                fps = "";
                DavinciPlayhead.reset();
            }

            for (JsonObject message = server.pollInbox(); message != null; message = server.pollInbox())
            {
                dispatch(server, message);
            }

            if (connections == 0)
            {
                return;
            }

            Film film = FilmIdentity.resolveOpenFilm();

            if (film == null)
            {
                return;
            }

            JsonObject snapshot = encode(film);
            String key = snapshot.toString() + "#" + ack;

            if (!key.equals(lastSent))
            {
                server.send(envelope(TYPE_FILM, snapshot));
                lastSent = key;
            }

            /* After the film, so Resolve has the timeline before it is asked to move on it. */
            DavinciPlayhead.tick(server, snapshot.get("film_id").getAsString());
        }
        catch (Throwable throwable)
        {
            LOGGER.error("[davincilink] tick failed", throwable);
        }
    }

    private static void dispatch(BridgeServer server, JsonObject message)
    {
        String type = message.has(BridgeProtocol.KEY_TYPE) ? message.get(BridgeProtocol.KEY_TYPE).getAsString() : "";

        if (TYPE_CLIPS_CHANGED.equals(type))
        {
            JsonObject fields = BridgeProtocol.getFields(message);
            JsonObject result = FilmBackup.protect(() -> DavinciEdits.apply(fields));

            server.send(envelope(TYPE_CLIPS_RESULT, result));
            lastSent = "";
        }
        else if (DavinciPlayhead.TYPE_PLAYHEAD.equals(type))
        {
            DavinciPlayhead.handle(BridgeProtocol.getFields(message));
        }
        else if (DavinciMedia.handles(type))
        {
            DavinciMedia.handle(server, type, BridgeProtocol.getFields(message));
        }
        else if (BridgeProtocol.TYPE_HELLO.equals(type))
        {
            JsonObject fields = BridgeProtocol.getFields(message);

            fps = fields != null && fields.has("fps") ? fields.get("fps").getAsString() : "";
            project = fields != null && fields.has("project") ? fields.get("project").getAsString() : project;
            lastSent = "";
            LOGGER.info("[davincilink] DaVinci connected: {}", fields);
        }
        else
        {
            LOGGER.warn("[davincilink] unknown message type '{}'", type);
        }
    }

    /* ---- BBS -> Resolve ------------------------------------------------- */

    public static JsonObject encode(Film film)
    {
        JsonObject out = new JsonObject();
        JsonArray clips = new JsonArray();
        List<Object> present = new ArrayList<>(film.camera.get());

        present.addAll(DavinciVideos.linked(film));

        /* Identity, not equals: keep exactly the clip objects still in this film. */
        IDS.keySet().removeIf((clip) -> present.stream().noneMatch((p) -> p == clip));

        for (Object clip : present)
        {
            clips.add(clip instanceof Replay replay ? DavinciVideos.encode(replay) : DavinciSnapshot.encode((Clip) clip));
        }

        out.addProperty("film_id", film.getId() == null ? "" : film.getId());
        out.addProperty("title", film.getId() == null ? "" : film.getId());
        out.addProperty("ack", ack);
        out.add("clips", clips);

        if (!fps.isEmpty())
        {
            File placeholder = DavinciPlaceholder.get(fps);

            out.addProperty("placeholder", placeholder == null ? "" : placeholder.getAbsolutePath());

            /* Camera clips are cut from the film's own media, so a render can replace it (DavinciProxy). */
            File media = film.getId() == null ? null : DavinciProxy.media(film.getId(), fps);

            out.addProperty("camera_media", media == null ? "" : media.getAbsolutePath());
            /* A new render changes only the file; this makes it a new FILM, so Resolve re-reads it. */
            out.addProperty("camera_media_version", media == null ? 0L : media.lastModified());
            out.addProperty("placeholder_error", DavinciPlaceholder.error(fps));
        }

        return out;
    }

    /** The clip's uuid for this session, made on first sight. */
    static String uuidOf(Object clip)
    {
        return IDS.computeIfAbsent(clip, (c) -> UUID.randomUUID().toString());
    }

    /** The timeline rate of the connected Resolve, or "" without one. */
    public static String fps()
    {
        return fps;
    }

    public static String project()
    {
        return project;
    }

    static String idOf(Object clip)
    {
        return IDS.get(clip);
    }

    static void bind(Object clip, String uuid)
    {
        IDS.put(clip, uuid);
    }

    /** Resolve's edits up to this sequence number are in BBS; the next FILM says so. */
    static void acknowledge(long incoming)
    {
        ack = Math.max(ack, incoming);
    }

    static UIFilmPanel editorPanel()
    {
        UIDashboard dashboard = BBSCompat.peekDashboard();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        return panel == null || panel.getData() == null ? null : panel;
    }

    static JsonObject envelope(String type, JsonObject fields)
    {
        JsonObject out = BridgeProtocol.envelope(type, "film", ++seq, BridgeProtocol.ORIGIN_BBS);

        out.add(BridgeProtocol.KEY_FIELDS, fields);

        return out;
    }
}
