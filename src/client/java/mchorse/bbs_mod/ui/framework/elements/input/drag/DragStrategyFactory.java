package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.Axis;

/**
 * Resolves an edit request to the drag strategy that will drive it. The
 * ray-vs-additive decision lives here, in one place: hotkey operations
 * honor the 3D-ray setting, the uniform scale always takes the additive
 * lever, a two-axis rotate has no ray implementation, and anything without
 * a drag snapshot falls back to additive as well.
 */
public final class DragStrategyFactory
{
    /** The control style of an edit, beyond its operation and axes. */
    public enum Variant
    {
        /** Plain axis or two-axis handle drag. */
        AXIS,
        /** Screen-space (view-plane) translate. */
        SCREEN,
        /** Uniform (three-axis) scale off one lever. */
        UNIFORM_SCALE,
        /** Rotation about the view axis (the outer ring). */
        VIEW,
        /** Free rotation by cursor motion. */
        TRACKBALL,
        /** Free rotation by gripping a point on the ball. */
        ARCBALL;
    }

    private DragStrategyFactory()
    {}

    /**
     * Blenders Constraint-Wechsel im laufenden Zug (Akte 13, transform.cc:
     * setUserConstraint aendert nur die Constraint-Wahl, die Werte rechnen
     * weiter aus der Gesamtbewegung ab Griff): die neue Achsen-Geste ankert
     * am GRIFF-Punkt (imval, wrap-mitgefuehrt) und wendet die gesamte
     * Mausbewegung sofort durch den neuen Constraint an — statt an der
     * aktuellen Maus neu zu beginnen (das spulte sichtbar auf die
     * Startwerte zurueck). Ohne Anker ({@code griffX/Y < 0}: Bearbeitung
     * ohne Zeiger begonnen) ankert sie wie bisher an der aktuellen Maus.
     */
    public static DragStrategy wechselnMitGriff(DragContext ctx, TransformOp op, Axis axis, Axis axis2, boolean hotkeyMode,
        int griffX, int griffY, int mausX, int mausY, DragCursor cursor)
    {
        DragStrategy strategy = create(ctx, op, axis, axis2, Variant.AXIS, hotkeyMode);

        if (griffX >= 0 && griffY >= 0)
        {
            boolean fein = strategy.usesFineCursor();

            strategy.begin(griffX, griffY);
            strategy.update(fein ? cursor.x(mausX) : mausX, fein ? cursor.y(mausY) : mausY);
        }
        else
        {
            strategy.begin(mausX, mausY);
        }

        return strategy;
    }

    public static DragStrategy create(DragContext ctx, TransformOp op, Axis axis, Axis axis2, Variant variant, boolean hotkeyMode)
    {
        /* Simple keyboard transformation: a hotkey edit moves the channels straight by the cursor,
         * no ray, no space cycle; dragging the gizmo itself is unaffected (BBS FS b89d70e0b) */
        if (hotkeyMode && BBSSettings.simpleKeyboardTransform.get())
        {
            return new AdditiveDrag(ctx, op, axis, axis2, variant == Variant.UNIFORM_SCALE, true);
        }

        boolean rayAllowed = (!hotkeyMode || BBSSettings.transformHotkeys3dRay.get()) && ctx.drag() != null;

        /* Blenders Skalier-Mathe (2D-Distanz-Verhaeltnis zum Pivot) fuer die
         * HOTKEY-Geste — der Gizmo-Griff behaelt seinen Achs-Hebel (dort zieht
         * man am Handle selbst). Kein Strahl, braucht nur den projizierbaren
         * Ursprung; Schalter aus -> altes Verhalten (additiv/Achs-Hebel). */
        boolean spring = hotkeyMode && BBSSettings.transformBlenderFeedback.get() && ctx.drag() != null;

        if (spring && op == TransformOp.SCALE && variant != Variant.UNIFORM_SCALE)
        {
            return new SpringScaleDrag(ctx, axis, axis2, false);
        }

        switch (variant)
        {
            case UNIFORM_SCALE:
                if (spring)
                {
                    return new SpringScaleDrag(ctx, axis, axis2, true);
                }

                /* The ray's lever runs along a single axis, which reads wildly
                 * for a centre grab and makes the scale explode. */
                return new AdditiveDrag(ctx, TransformOp.SCALE, axis, axis2, true);

            case SCREEN:
                return rayAllowed ? new ScreenTranslateDrag(ctx) : new AdditiveDrag(ctx, TransformOp.TRANSLATE, axis, axis2, false);

            case VIEW:
                return rayAllowed ? new ViewRotateDrag(ctx) : new AdditiveDrag(ctx, TransformOp.ROTATE, axis, axis2, false);

            case TRACKBALL:
                return rayAllowed ? new TrackballDrag(ctx) : new AdditiveDrag(ctx, TransformOp.ROTATE, axis, axis2, false);

            case ARCBALL:
                return rayAllowed ? new ArcballDrag(ctx) : new AdditiveDrag(ctx, TransformOp.ROTATE, axis, axis2, false);

            default:
                break;
        }

        /* A two-axis rotate has no ray implementation, so it stays additive. */
        boolean ray = rayAllowed && (op != TransformOp.ROTATE || axis2 == null);

        if (!ray)
        {
            return new AdditiveDrag(ctx, op, axis, axis2, false);
        }

        switch (op)
        {
            case TRANSLATE:
                return new TranslateDrag(ctx, axis, axis2);
            case SCALE:
                return new ScaleDrag(ctx, axis, axis2);
            default:
                return new RingRotateDrag(ctx, axis);
        }
    }
}
