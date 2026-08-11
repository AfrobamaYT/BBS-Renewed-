package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.utils.Axis;

/**
 * A step of a transform hotkey's walk. Tokens match the entries of the
 * translate/scale/rotate hotkey order settings.
 *
 * <p>Unveraendert aus {@code UIPropTransform} herausgeloest — die Datei ist ein gelisteter
 * Monolith und darf nur noch schrumpfen.</p>
 */
public enum HotkeyTarget
{
    VIEW("view", null, true),
    SPHERE("sphere", null, true),
    SCREEN("screen", null, true),
    /** Scale's non-axis step: one lever drives all three axes (Blender's plain S). */
    ALL("all", null, false),
    X("x", Axis.X, false),
    Y("y", Axis.Y, false),
    Z("z", Axis.Z, false);

    public final String token;
    public final Axis axis;
    /** Whether the step is driven by the 3D ray and so needs a rendered gizmo. */
    public final boolean needsRay;

    HotkeyTarget(String token, Axis axis, boolean needsRay)
    {
        this.token = token;
        this.axis = axis;
        this.needsRay = needsRay;
    }

    public static HotkeyTarget byToken(String token)
    {
        for (HotkeyTarget target : values())
        {
            if (target.token.equals(token))
            {
                return target;
            }
        }

        return null;
    }
}
