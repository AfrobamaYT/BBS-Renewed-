// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.blenderbridge.BridgeServer;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * The film editor's cursor and Resolve's playhead, kept on the same moment both ways.
 *
 * <p>Both sides remember the last tick they agree on; only a cursor that differs from it is sent,
 * and applying one sets it, so neither side echoes the other. While BBS plays, playback wins: a
 * playhead from Resolve does not jump the running film.</p>
 */
public final class DavinciPlayhead
{
    public static final String TYPE_PLAYHEAD = "PLAYHEAD";

    private static int agreed = -1;

    private DavinciPlayhead()
    {}

    static void reset()
    {
        agreed = -1;
    }

    /** BBS -> Resolve: the editor's cursor whenever it moved away from the agreed tick. */
    static void tick(BridgeServer server, String filmId)
    {
        UIFilmPanel panel = DavinciFilmSync.editorPanel();

        if (panel == null || panel.getCursor() == agreed)
        {
            return;
        }

        agreed = panel.getCursor();

        JsonObject fields = new JsonObject();

        fields.addProperty("film_id", filmId);
        fields.addProperty("tick", agreed);
        fields.addProperty("playing", panel.isRunning());
        server.send(DavinciFilmSync.envelope(TYPE_PLAYHEAD, fields));
    }

    /** Resolve -> BBS: move the cursor, unless the film is playing or another film is open. */
    static void handle(JsonObject fields)
    {
        UIFilmPanel panel = DavinciFilmSync.editorPanel();

        if (panel == null || panel.isRunning() || !panel.getData().getId().equals(fields.get("film_id").getAsString()))
        {
            return;
        }

        agreed = Math.max(0, fields.get("tick").getAsInt());
        panel.setCursor(agreed);
    }
}
