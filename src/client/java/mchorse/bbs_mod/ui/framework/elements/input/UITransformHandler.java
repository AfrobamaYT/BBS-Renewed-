package mchorse.bbs_mod.ui.framework.elements.input;

import com.mojang.blaze3d.platform.InputConstants;
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

    /**
     * Wirklich auf dem Schirm — die ganze Elternkette. ⛔ {@code isVisible()} prueft NUR die
     * eigene Flagge: das Transform-Panel im verborgenen Replay-Editor meldete sichtbar=true
     * (gemessen 2026-08-24), und jedes Tor, das darauf baute, griff daneben.
     */
    public static boolean aufSchirm(UIElement element)
    {
        for (UIElement e = element; e != null; e = e.getParent())
        {
            if (!e.isVisible())
            {
                return false;
            }
        }

        return true;
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
            boolean overPanel = aufSchirm(this.transform) && this.transform.area.isInside(context);

            if (!overPanel && this.transform.middleAxis.maustasteGedrueckt(context.mouseButton, context.mouseX, context.mouseY))
            {
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    /**
     * A running edit whose panel is hidden takes Enter, Escape and typed numbers (R X 90) as the
     * visible panel does. From FilmTasten, ahead of the tree: this overlay handler alone came too
     * late -- Escape went to the panel tree, digits and Enter to the film's shortcuts, and the
     * edit could not be ended by key at all (measured with the Film settings tab open).
     */
    public static boolean verborgenNehmen(UIPropTransform transform, UIContext context)
    {
        return transform != null && transform.isEditing() && !aufSchirm(transform) && transform.subKeyPressed(context);
    }

    /**
     * ⛔ ENTER/ESC auch OHNE sichtbares Panel (Umzug 2026-08-24): die Bearbeitung soll im
     * Kamera-Blick laufen, ohne den alten Pose-Editor aufzuschalten. Dessen subKeyPressed
     * sieht verborgen keine Taste — der Handler haengt im Overlay und sieht sie immer.
     * Nur im verborgenen Fall, sonst wuerde die Numerik-Eingabe des Panels uebergangen.
     */
    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.transform.isEditing() && !aufSchirm(this.transform))
        {
            if (context.isPressed(InputConstants.KEY_RETURN))
            {
                this.transform.acceptChanges();

                return true;
            }
            else if (context.isPressed(InputConstants.KEY_ESCAPE))
            {
                this.transform.rejectChanges();

                return true;
            }
        }

        return super.subKeyPressed(context);
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

        /* ⛔ VERBORGEN WEITERTREIBEN (Umzug 2026-08-24). updateDrag lief nur im render des
         * Panels — also nur sichtbar; genau deshalb schaltete der Ring-Klick frueher den
         * alten Pose-Editor auf. Der Handler existiert exakt solange eine Bearbeitung
         * laeuft und rendert jedes Bild. Dieselben Tore wie im Panel-render: Numerik
         * pausiert die Maus, der Timer entprellt den Rand-Umbruch. */
        if (this.transform.isEditing() && !aufSchirm(this.transform)
            && !this.transform.numeric.isActive() && this.transform.checker.isTime())
        {
            this.transform.updateDrag(context);
        }

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

        /* Typed numbers: the panel draws them at the cursor in its own render, which a hidden panel
         * never runs -- the input went unseen (Blender shows it in the header). */
        if (editing && !aufSchirm(this.transform) && this.transform.numeric.isActive())
        {
            mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp op = this.transform.getOp();
            boolean drehen = op == mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp.ROTATE;
            String achse = this.transform.getAxis() == null ? "" : " " + this.transform.getAxis().name()
                + (this.transform.getAxis2() == null ? "" : this.transform.getAxis2().name());
            String text = (drehen ? "Rotate" : op == mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp.SCALE ? "Scale" : "Move")
                + achse + "  " + this.transform.numeric.display() + (drehen ? "°" : "");

            context.batcher.textCard(text, context.mouseX + 12, context.mouseY + 12, mchorse.bbs_mod.utils.colors.Colors.WHITE, mchorse.bbs_mod.utils.colors.Colors.A50);
        }
    }
}
