package mchorse.bbs_mod.ui.film.markers;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.film.markers.Marker;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

/** Katalog E9: Ctrl+Right/Left jump the playhead to the next/previous marker (BBS FS, Blender); Ctrl+M sets one at the playhead. */
public final class MarkerSprung
{
    public static final KeyCombo NAECHSTER = new KeyCombo("marker_next", IKey.raw("Jump to next marker"),
        InputConstants.KEY_RIGHT, InputConstants.KEY_LCONTROL).repeatable().categoryKey("camera");
    public static final KeyCombo VORIGER = new KeyCombo("marker_prev", IKey.raw("Jump to previous marker"),
        InputConstants.KEY_LEFT, InputConstants.KEY_LCONTROL).repeatable().categoryKey("camera");

    private MarkerSprung()
    {}

    /** Ctrl+M anywhere in the film editor: the marker lane only lives in the clip editors, the dope sheet had none */
    public static boolean setzen(UIFilmPanel panel)
    {
        if (panel.getData() == null)
        {
            return false;
        }

        panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        panel.getData().markers.setzeBei(panel.getCursor());
        UIUtils.playClick();

        return true;
    }

    public static boolean springen(UIFilmPanel panel, int richtung)
    {
        if (panel.getData() == null)
        {
            return false;
        }

        int cursor = panel.getCursor();
        Integer ziel = null;

        for (Marker marker : panel.getData().markers.getList())
        {
            int t = marker.tick.get();

            if (richtung > 0 && t > cursor && (ziel == null || t < ziel)) ziel = t;
            if (richtung < 0 && t < cursor && (ziel == null || t > ziel)) ziel = t;
        }

        if (ziel == null)
        {
            panel.getContext().notifyInfo(IKey.raw(richtung > 0 ? "No marker after the playhead (Ctrl+M sets one)" : "No marker before the playhead (Ctrl+M sets one)"));

            return false;
        }

        panel.setCursor(ziel);
        UIUtils.playClick();

        return true;
    }
}
