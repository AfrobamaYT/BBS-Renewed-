package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.Messpunkt;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.GizmoDrag;

import org.joml.Vector2f;

/**
 * Blenders gestrichelte Straehne vom projizierten Bearbeitungs-Ursprung zur Maus
 * (transform_draw_cursors.cc). Blender zeigt sie NUR bei Scale und Rotate
 * (HLP_SPRING/HLP_ANGLE) — beim reinen Grab ist sie aus (HLP_NONE); die
 * MMB-Achsenwahl hat ihre eigene, feiner gemusterte Linie
 * (transform_constraints.cc: dash_width 2 statt 8).
 *
 * <p>Muster in PIXELN an der MAUS verankert und rueckwaerts zum Ursprung gelaufen:
 * die Striche stehen unterm Zeiger still und "entstehen" beim Wegziehen am
 * Objektpunkt (Nutzer-Abnahme gegen echtes Blender — wirkt smoother als die
 * Objekt-Verankerung, weil der Blick an der Maus haengt). Am Ursprung sitzt
 * Blenders oranger Origin-Punkt, von dem die Straehne ausgeht. Vor dem hellen
 * Strich liegt ein um 1px versetzter dunkler (Blenders bg/fg-Doppelpass), damit
 * die 1px-Linie auf hellem Grund lesbar bleibt. Unprojizierbarer Ursprung ->
 * keine Straehne.</p>
 */
public final class TransformStraehne
{
    /** Blenders Helpline: DASH_LENGTH 8px, udash_factor 0.5 -> 4px Strich, 4px Luecke. */
    private static final float SEGMENT = 4F;
    private static final float LUECKE = 4F;

    /** Blenders Constraint-Linie der MMB-Wahl: dash_width 2, udash 0.5 -> 1px/1px. */
    private static final float SEGMENT_FEIN = 1F;
    private static final float LUECKE_FEIN = 1F;

    private static final int FARBE = 0xE8FFFFFF;
    private static final int SCHATTEN = 0x80000000;

    /** Blenders Origin-Dot des aktiven Objekts (Theme "active": orange). */
    private static final int PUNKT_ORANGE = 0xFFFFA028;
    private static final int PUNKT_RAND = 0xB0000000;

    private TransformStraehne()
    {}

    /**
     * Vom Handler je Bild gerufen, nur waehrend einer laufenden Bearbeitung.
     *
     * @param zug  Schnappschuss mit projizierbarem Ursprung; {@code null} = keine Straehne.
     * @param fein Blenders MMB-Constraint-Muster (1px/1px, ohne Schatten) statt der
     *             Scale/Rotate-Helpline (4px/4px mit Schattenpass).
     */
    public static void zeichnen(UIContext context, GizmoDrag zug, boolean fein)
    {
        if (!BBSSettings.transformBlenderFeedback.get() || zug == null)
        {
            return;
        }

        Vector2f ursprung = new Vector2f();

        if (!zug.projectToScreen(zug.gizmoOrigin, ursprung))
        {
            return;
        }

        if (Messpunkt.istAn())
        {
            Messpunkt.zaehle("straehne", String.format("fein=%s ur=%.1f,%.1f maus=%d,%d", fein, ursprung.x, ursprung.y, context.mouseX, context.mouseY));
        }

        float dx = context.mouseX - ursprung.x;
        float dy = context.mouseY - ursprung.y;
        float laenge = (float) Math.sqrt(dx * dx + dy * dy);

        if (laenge < 1F)
        {
            return;
        }

        float rx = dx / laenge;
        float ry = dy / laenge;
        float segment = fein ? SEGMENT_FEIN : SEGMENT;
        float luecke = fein ? LUECKE_FEIN : LUECKE;
        float schritt = Math.min(1F, segment);

        /* Punktfolge statt gedrehter Rechtecke: der Batcher kennt nur achsenparallele
         * Kaesten; 1px-Punkte im Pixel-Raster lesen sich als Blenders gestrichelte
         * 1px-Linie. t laeuft VON DER MAUS rueckwaerts zum Ursprung (Anker an der
         * Maus, s.o.). */
        for (float t = 0F; t < laenge; t += segment + luecke)
        {
            float ende = Math.min(t + segment, laenge);

            for (float s = t; s < ende; s += schritt)
            {
                float x = context.mouseX - rx * s;
                float y = context.mouseY - ry * s;

                if (!fein)
                {
                    context.batcher.box(x + 0.5F, y + 0.5F, x + 1.5F, y + 1.5F, SCHATTEN);
                }

                context.batcher.box(x - 0.5F, y - 0.5F, x + 0.5F, y + 0.5F, FARBE);
            }
        }

        /* Blenders Origin-Punkt: der orange Dot, aus dem die Straehne zieht. Nach
         * der Linie gezeichnet, damit er den Strich-Rest am Ursprung ueberdeckt. */
        context.batcher.box(ursprung.x - 2.5F, ursprung.y - 2.5F, ursprung.x + 2.5F, ursprung.y + 2.5F, PUNKT_RAND);
        context.batcher.box(ursprung.x - 1.5F, ursprung.y - 1.5F, ursprung.x + 1.5F, ursprung.y + 1.5F, PUNKT_ORANGE);
    }
}
