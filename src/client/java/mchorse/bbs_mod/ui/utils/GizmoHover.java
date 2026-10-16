package mchorse.bbs_mod.ui.utils;

/**
 * The gizmo handle under the pointer, and the one being dragged, draws yellow -- as Maya marks
 * its active handle (Blender lightens it). The pick decides what a press grabs (with a hover
 * tolerance the nearest handle, not always the one that looks closest), and nothing showed it
 * (2026-10-15).
 *
 * <p>Set once per frame by {@link GizmoInteraction#update} from the viewport's hover pick, read by
 * {@link Gizmo} while it colours the visible handles. The pick pass keeps its id colours.</p>
 */
public final class GizmoHover
{
    /**
     * Maya's active-handle yellow. Lightening (Blender's way) washed a ring out against the bright
     * Minecraft sky -- the hovered blue ring nearly vanished (measured 2026-10-15).
     */
    private static final int GELB = 0xFFD84A;

    /**
     * Visible line weight of rings, view ring and bars against their pick width: the lines drew as
     * thick as the band a click finds them in (~5 px), Blender draws ~2 px. The pick pass
     * ({@link GizmoAuswahl}) keeps the full width, so the grab area stays as forgiving.
     */
    static final float LINIE = 0.55F;

    private static int ueber;

    private GizmoHover()
    {}

    public static void setzen(StencilFormFramebuffer stencil)
    {
        int index = stencil != null && stencil.hasPickedOhneWarten() ? stencil.getIndexOhneWarten() : 0;

        ueber = index >= Gizmo.STENCIL_X && index <= Gizmo.STENCIL_MAX ? index : 0;
    }

    static boolean hell(Gizmo.Handle handle, Gizmo.Handle aktiv)
    {
        return handle != null && (aktiv == null ? handle.index == ueber : handle == aktiv);
    }

    /** One colour channel (0 red, 1 green, 2 blue) of a ring */
    static float kanal(Gizmo.Handle handle, Gizmo.Handle aktiv, int kanal, float wert)
    {
        return hell(handle, aktiv) ? ((GELB >> (16 - kanal * 8)) & 0xFF) / 255F : wert;
    }

    static int farbe(Gizmo.Handle handle, Gizmo.Handle aktiv, int farbe)
    {
        return hell(handle, aktiv) ? (farbe & 0xFF000000) | GELB : farbe;
    }
}
