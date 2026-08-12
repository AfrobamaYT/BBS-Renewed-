package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Blenders Skalier-Geste (transform_input.cc: InputSpringFlip + calcSpringFactor,
 * transform_mode_resize.cc: applyResize): das Verhaeltnis der BILDSCHIRM-Distanz
 * Maus->projizierter Pivot zur Distanz beim Griff ist der Faktor — Maus zum Objekt
 * ziehen schrumpft, wegziehen waechst, und ueber den Pivot durchziehen SPIEGELT
 * (negatives Skalieren), weil das Skalarprodukt der Griff- und Ist-Richtung kippt.
 *
 * <p>Kein 3D-Strahl: der Hebel ist rein zweidimensional, deshalb explodiert ein
 * Griff nahe dem Zentrum nicht (die Startdistanz ist auf 1px geklemmt — Blenders
 * "prevent inf"). Achsen filtern nur, WOHIN der eine Faktor schreibt; ohne Achse
 * (S) skaliert er alle drei, wie Blenders values_final = (ratio, ratio, ratio).</p>
 */
public class SpringScaleDrag extends DragStrategy
{
    private final boolean scaleAll;

    /** Projizierter Gizmo-Ursprung in Viewport-Pixeln, beim Griff verankert. */
    private final Vector2f center = new Vector2f();

    /** Cursorposition beim Griff — Blenders imval, Bezug fuer den Spiegel-Test. */
    private final Vector2f imval = new Vector2f();

    /** Startdistanz zum Pivot (Blenders mi->factor), >= 1px geklemmt. */
    private float startDistance = 1F;

    /** Vorzeichen-Uebertrag eines Re-Ankers (Wrap), damit ein gespiegelter Zug
     *  ueber die Naht gespiegelt bleibt. */
    private float carrySign = 1F;

    private final Vector3f startScale = new Vector3f();

    private float lastRatio = 1F;

    public SpringScaleDrag(DragContext ctx, Axis axis, Axis axis2, boolean scaleAll)
    {
        super(ctx, TransformOp.SCALE, axis, axis2);

        this.scaleAll = scaleAll;
    }

    @Override
    public boolean isScaleAll()
    {
        return this.scaleAll;
    }

    @Override
    public void begin(int mouseX, int mouseY)
    {
        GizmoDrag drag = this.ctx.drag();

        if (drag == null || this.ctx.transform() == null)
        {
            this.hasStart = false;

            return;
        }

        /* Re-Anker (Cursor-Wrap, Rueckkehr aus Zahleneingabe): die Startdistanz wird
         * so gesetzt, dass das AKTUELLE Verhaeltnis an der neuen Cursorposition exakt
         * weiterlaeuft — Blender braucht das nicht (virtuelle Maus laeuft ueber den
         * Rand hinaus), unser Wrap teleportiert den Zeiger wirklich. */
        if (this.hasStart)
        {
            this.imval.set(mouseX, mouseY);

            float distance = this.distance(mouseX, mouseY);
            float ratio = Math.abs(this.lastRatio);

            this.startDistance = ratio < 1.0E-4F ? Math.max(distance, 1F) : Math.max(distance / ratio, 1.0E-4F);
            this.carrySign = Math.signum(this.lastRatio) == 0F ? 1F : Math.signum(this.lastRatio);

            return;
        }

        if (!drag.projectToScreen(drag.gizmoOrigin, this.center))
        {
            this.hasStart = false;

            return;
        }

        this.imval.set(mouseX, mouseY);
        this.startDistance = Math.max(this.distance(mouseX, mouseY), 1F);
        this.carrySign = 1F;
        this.startScale.set(this.ctx.transform().scale);
        this.hasStart = true;
    }

    @Override
    public void update(int mouseX, int mouseY)
    {
        if (!this.hasStart || this.ctx.transform() == null)
        {
            return;
        }

        float ratio = (this.distance(mouseX, mouseY) / this.startDistance) * this.carrySign;

        /* Blenders Spiegel (InputSpringFlip): wandert der Cursor auf die Gegenseite
         * des Pivots, kippt das Skalarprodukt der beiden Richtungen und der Faktor
         * wird negativ. */
        float dotSides = (this.center.x - mouseX) * (this.center.x - this.imval.x)
            + (this.center.y - mouseY) * (this.center.y - this.imval.y);

        if (dotSides < 0F)
        {
            ratio = -ratio;
        }

        this.lastRatio = ratio;

        if (this.ctx.shouldSnap(TransformOp.SCALE))
        {
            ratio = (float) snap(ratio, BBSSettings.snapScale.get());
        }

        boolean all = this.scaleAll || Window.isCtrlPressed();
        Vector3f s = new Vector3f(this.startScale);

        if (all || this.axis == Axis.X || this.axis2 == Axis.X) s.x = this.startScale.x * ratio;
        if (all || this.axis == Axis.Y || this.axis2 == Axis.Y) s.y = this.startScale.y * ratio;
        if (all || this.axis == Axis.Z || this.axis2 == Axis.Z) s.z = this.startScale.z * ratio;

        this.ctx.writeScale(s.x, s.y, s.z);
    }

    private float distance(int mouseX, int mouseY)
    {
        return (float) Math.hypot(this.center.x - mouseX, this.center.y - mouseY);
    }

    @Override
    public void applyNumeric(double value)
    {
        this.numericScale(value, this.scaleAll || Window.isCtrlPressed());
    }

    /** Blenders Kopfzeile zeigt den FAKTOR ("Scale: 1.2340"), nicht Achsen-Deltas. */
    @Override
    public String readout()
    {
        StringBuilder builder = new StringBuilder();
        boolean all = this.scaleAll || Window.isCtrlPressed();

        if (all)
        {
            return String.format("%.4f", this.lastRatio);
        }

        if (this.axis == Axis.X || this.axis2 == Axis.X) appendAxis(builder, "X", this.lastRatio);
        if (this.axis == Axis.Y || this.axis2 == Axis.Y) appendAxis(builder, "Y", this.lastRatio);
        if (this.axis == Axis.Z || this.axis2 == Axis.Z) appendAxis(builder, "Z", this.lastRatio);

        return builder.length() == 0 ? null : builder.toString();
    }
}
