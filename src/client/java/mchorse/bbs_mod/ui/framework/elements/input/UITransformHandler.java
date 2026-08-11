package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

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
}
