package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.GizmoDrag;

import org.joml.Vector2f;

/**
 * Blenders gestrichelte Straehne waehrend einer Transform-Bearbeitung (Akte 07): eine
 * gepunktete Linie vom BILDSCHIRM-Ort des Bearbeitungs-Ursprungs zur Maus, die ihr folgt —
 * bei Grab, Scale und Rotate gleichermassen (in Blender ist sie beim Rotieren der
 * Winkelarm, bei Grab/Scale der Bezug zum Ursprung).
 *
 * <p>Gezeichnet im UI-Raum ueber den {@code Batcher2D} als Punktfolge (Segmente mit
 * Luecken); der Ursprung kommt aus {@link GizmoDrag#projectToScreen} — derselben
 * Projektion, die auch die Achsenwahl benutzt. Ist der Ursprung nicht projizierbar
 * (Kamera auf dem Knochen), bleibt die Straehne weg statt in eine falsche Ecke zu
 * zeigen.</p>
 */
public final class TransformStraehne
{
    /** Segmentlaenge und Luecke in GUI-Punkten — Blenders Muster ist ~4/4 Pixel. */
    private static final float SEGMENT = 4F;
    private static final float LUECKE = 4F;
    private static final int FARBE = 0xB8FFFFFF;

    private TransformStraehne()
    {}

    /**
     * Vom Handler je Bild gerufen, nur waehrend einer laufenden Bearbeitung.
     *
     * @param zug der Schnappschuss fuer die Achsenwahl (ungefiltert) oder der lebende Zug —
     *            was der Wirt eben hat; {@code null} = keine Straehne.
     */
    public static void zeichnen(UIContext context, UIPropTransform transform, GizmoDrag zug)
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

        float dx = context.mouseX - ursprung.x;
        float dy = context.mouseY - ursprung.y;
        float laenge = (float) Math.sqrt(dx * dx + dy * dy);

        if (laenge < 1F)
        {
            return;
        }

        float rx = dx / laenge;
        float ry = dy / laenge;

        /* Punktfolge statt gedrehter Rechtecke: der Batcher kennt nur achsenparallele
         * Kaesten, und 1.5x1.5-Punkte im 4/4-Raster lesen sich als gestrichelte Linie —
         * genau der Blender-Look aus dem Nutzerbild. */
        for (float t = 0F; t < laenge; t += SEGMENT + LUECKE)
        {
            float ende = Math.min(t + SEGMENT, laenge);

            for (float s = t; s < ende; s += 1.5F)
            {
                float x = ursprung.x + rx * s;
                float y = ursprung.y + ry * s;

                context.batcher.box(x - 0.75F, y - 0.75F, x + 0.75F, y + 0.75F, FARBE);
            }
        }
    }
}
