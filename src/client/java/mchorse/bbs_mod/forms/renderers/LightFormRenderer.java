package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * LightFormRenderer — der renderer einer {@link LightForm}.
 *
 * <h2>WARUM ER EXISTIEREN MUSS, OBWOHL ER IN 3D NICHTS ZEICHNET</h2>
 * <p>BBS ruft an mehreren stellen {@code FormUtilsClient.getRenderer(form).collectMatrices(...)}
 * OHNE null-check — z.b. {@code BaseFilmController.renderAxes} (die gizmo-achsen des im editor
 * AUSGEWAEHLTEN actors) und {@code getGizmoBoneEvaluatedRotation}. Eine registrierte form ohne
 * renderer ist dort eine sichere NPE auf dem render-thread: lichter aus Blender importieren,
 * den licht-actor anklicken, crash. Deshalb gehoert zu jedem eintrag in der form-registry
 * ({@code BBSMod}) zwingend ein eintrag in {@code FormUtilsClient} — beides oder keins.</p>
 *
 * <h2>WAS ER ZEICHNET</h2>
 * <p>In 3D: nichts. Ein licht ist keine geometrie; sichtbar wird es erst durch einen
 * beleuchtungs-pass (noch nicht gebaut — siehe {@link LightForm}-klassendoc "STAND").
 * {@code FormRenderer.render3D} ist per default leer, also wird es hier bewusst NICHT
 * ueberschrieben; die koerperteile (body parts) rendert die basisklasse weiterhin, damit man
 * geometrie an ein licht haengen kann wie an jede andere form.</p>
 * <p>In der UI (form-liste, palette): das licht-icon, eingefaerbt mit der lichtfarbe — so sieht
 * man in der liste sofort, welches licht welche farbe hat, ohne es zu oeffnen. Ein abgeschaltetes
 * licht ({@code enabled == false}) wird grau gezeichnet.</p>
 */
public class LightFormRenderer extends FormRenderer<LightForm>
{
    public LightFormRenderer(LightForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = Colors.GRAY;

        if (this.form.enabled.get())
        {
            Color c = this.form.color.get();

            /* Colors.opaque, weil getRGBColor() alpha-los ist — sonst waere das icon transparent.
             * Der alpha-kanal der lichtfarbe ist bedeutungslos und darf hier nicht durchschlagen. */
            color = Colors.opaque(c == null ? Colors.WHITE : c.getRGBColor());
        }

        context.batcher.icon(Icons.LIGHT, color, (x1 + x2) / 2F, (y1 + y2) / 2F, 0.5F, 0.5F);
    }
}
