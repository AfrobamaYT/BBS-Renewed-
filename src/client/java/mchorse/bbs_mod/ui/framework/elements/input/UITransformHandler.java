package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformStraehne;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformZeiger;

/**
 * Klick- und Rad-Weiche waehrend einer laufenden Transform-Bearbeitung. Haengt in
 * {@code context.menu.overlay} und ist nur waehrend einer Bearbeitung im Baum
 * ({@code startEdit} haengt ihn ein, {@code disable()} wieder aus); {@code UIElement}
 * laeuft seine Kinder rueckwaerts durch, der Handler sieht Eingaben also VOR dem Sichtfeld.
 *
 * <p>Unveraendert aus {@code UIPropTransform} herausgeloest (gelisteter Monolith, darf nur
 * schrumpfen) — neu ist hier nur der Mittelklick-Zweig ({@link MittelklickAchse}).</p>
 */
public class UITransformHandler extends UIElement
{
    private UIPropTransform transform;

    public UITransformHandler(UIPropTransform transform)
    {
        this.transform = transform;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.transform.isEditing())
        {
            if (context.mouseButton == 0)
            {
                this.transform.acceptChanges();

                return true;
            }
            else if (context.mouseButton == 1)
            {
                this.transform.rejectChanges();

                return true;
            }

            /* Blender's middle-click axis pick. Consumes the button only while an edit is
             * running AND the setting is on — otherwise it falls through untouched, which
             * is what the viewport's own middle-click gestures (orbit pan, replay picks)
             * depend on.
             *
             * ⛔ Not over the transform panel itself: this handler sits in the overlay and
             * therefore sees the button BEFORE the panel's own trackpads, whose middle
             * click negates a value (UITrackpad#subMouseClicked). Swallowing it there would
             * silently kill that for as long as an edit runs. Visibility is checked too —
             * a hidden panel keeps a stale area that would block legitimate clicks. */
            boolean overPanel = this.transform.isVisible() && this.transform.area.isInside(context);

            if (!overPanel && this.transform.middleAxis.maustasteGedrueckt(context.mouseButton, context.mouseX, context.mouseY))
            {
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        /* Releasing freezes the previewed axis; the edit itself carries on. */
        if (this.transform.middleAxis.maustasteLosgelassen(context.mouseButton))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        /* While sphere-dragging the wheel rolls about the view axis; during a
         * screen-space grab it drives depth; otherwise it keeps adjusting
         * the drag sensitivity amplifier as before. */
        if (this.transform.scrollDrag(context))
        {
            return true;
        }

        UITrackpad.updateAmplifier(context);

        return true;
    }

    /** Der Handler haengt ohne Layout im Overlay (area 0x0) — das Viewport-Culling in
     *  UIElement wuerde sein render sonst NIE rufen (gemessen: handler.render-Fuehler 0,
     *  waehrend Maus/Tasten ankamen; Events laufen ohne dieses Culling). */
    @Override
    public boolean canBeRendered(mchorse.bbs_mod.ui.utils.Area viewport)
    {
        return true;
    }

    /** disable() haengt den Handler hierueber aus — Cursor und Voransicht MUESSEN dabei
     *  zurueck, sonst ueberlebt ein Hardware-Cursor die Bearbeitung. */
    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        TransformZeiger.zuruecksetzen();
        mchorse.bbs_mod.ui.utils.GizmoAchsenLinien.setVoransicht(false);
        mchorse.bbs_mod.ui.utils.GizmoAchsenLinien.setFrisch(null);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        mchorse.bbs_mod.graphics.Messpunkt.zaehle("handler.render");

        /* Blenders Transform-Rueckmeldung (Akte 07). Der Handler ist NUR waehrend einer
         * Bearbeitung im Baum — Cursor und Voransicht werden je Bild auf den Ist-Zustand
         * gesetzt und in disable()/removeFromParent ueber den Transform zurueckgenommen. */
        boolean an = mchorse.bbs_mod.BBSSettings.transformBlenderFeedback.get();
        boolean editing = this.transform.isEditing();
        boolean mmbWahl = editing && this.transform.middleAxis.istWaehlend();

        TransformZeiger.setzen(an && editing ? this.transform.getOp() : null);
        mchorse.bbs_mod.ui.utils.GizmoAchsenLinien.setVoransicht(mmbWahl);

        /* Blenders Helpline-Regeln (transform_input.cc): Scale und Rotate ziehen die
         * gestrichelte Straehne (HLP_SPRING/HLP_ANGLE), das reine Grab hat KEINE
         * (HLP_NONE) — nur die MMB-Achsenwahl zeigt ihre eigene, feiner gemusterte
         * Linie (transform_constraints.cc), auch beim Grab. */
        boolean helpline = this.transform.getOp() != mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp.TRANSLATE;

        if (an && editing)
        {
            /* Derselbe Schnappschuss wie bei der Achsenwahl: ungefiltert, ueberlebt einen
             * unprojizierbaren Ursprung (dann bleibt die Straehne einfach weg). Je Bild
             * frisch, denn er traegt die AKTUELLE Kamera — die Sperrlinien brauchen sie,
             * sonst driftet ihr Startpunkt-Anker um die Kamerabewegung seit dem Griff
             * (Akte 14). */
            java.util.function.Supplier<mchorse.bbs_mod.ui.utils.GizmoDrag> pick = this.transform.axisPickDragSupplier;
            mchorse.bbs_mod.ui.utils.GizmoDrag zug = pick != null ? pick.get() : this.transform.drag;

            mchorse.bbs_mod.ui.utils.GizmoAchsenLinien.setFrisch(zug);

            if (helpline || mmbWahl)
            {
                TransformStraehne.zeichnen(context, zug != null ? zug : this.transform.drag, !helpline);
            }
        }
    }
}
