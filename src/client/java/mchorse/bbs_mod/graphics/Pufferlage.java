package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.Minecraft;

/**
 * WO LANDET DIE FIGUR BEIM FUELLEN DES AUSWAHLPUFFERS — die Frage, die nach zwei Tagen
 * uebrigblieb.
 *
 * <h2>⛔ Warum es diese Klasse gibt (2026-08-03)</h2>
 *
 * <p>Der orange Auswahl-Umriss steht bei Kamerabewegung ~100 px neben der Figur und sitzt im
 * Stillstand passgenau. Neun Fixe scheiterten. Gemessen ist inzwischen:</p>
 *
 * <ul>
 * <li>Der <b>Auswahlpuffer selbst</b> enthaelt die Figur bereits versetzt — Puffer-Dump gegen
 *     Schuss aus demselben Moment, der Stillstandswert war vorher berechnet (1286 erwartet,
 *     1280 gemessen). Damit sind Blit, Umriss-Shader, Doppelpuffer und Drosselung raus.</li>
 * <li><b>Jeder Eingang der Kamerakette stimmt</b>: Blickrichtung, Position, Projektionsart,
 *     PoseStack-Translation, Sammel- gegen Zeichenphase — alles identisch mit dem
 *     Weltdurchgang, fuenf Runden mitten in der Bewegung ({@link Zeichenlage}, Sonde
 *     {@code sicht.*}).</li>
 * </ul>
 *
 * <p>Also rechnet nicht die Kamera falsch, sondern die Figur landet woanders. Diese Klasse
 * misst deshalb die <b>fertige Zeichenlage</b> unmittelbar vor dem Fuellen: die Matrix, mit
 * der wirklich gezeichnet wird, die Weltposition der Figur und den Abspielzustand.</p>
 *
 * <h2>⚠ Eine Sonde mit sechs Werten kostet so viel wie eine mit einem</h2>
 *
 * <p>Genau daran hing der Fall: jede Vermutung wurde einzeln gebaut, getauscht und geprueft
 * (~20 Minuten je Runde, neunmal). Darum stehen hier alle Werte nebeneinander — der
 * abweichende IST die Ursache, ohne einen zweiten Durchgang.</p>
 *
 * <pre>
 *   python tools/dev.py ev 'mchorse.bbs_mod.graphics.Messpunkt.bericht()'   -&gt; Zeile ~fuellen.lage
 * </pre>
 */
public final class Pufferlage
{
    private Pufferlage()
    {}

    /**
     * Die zwei Kameraquellen des Fuellwegs nebeneinander — DIE Sonde, die den Fehler fand.
     *
     * <p>{@code BBSRendering.camera} ist die Kopie, die {@code WorldRendererMixin} am HEAD von
     * {@code LevelRenderer.render} frisch stempelt. Der {@code worldRenderContext} ist der
     * Kontext, mit dem das WELTBILD gezeichnet wurde — und derselbe, der an
     * {@code renderStencilEntity} weitergeht und dort die kamerarelative Verschiebung
     * liefert. Beide tragen dieselbe Ansichtsdrehung, aber aus verschiedenen Augenblicken:</p>
     *
     * <pre>
     *   Stillstand   bbs=-180.000   vanilla=-180.000   Delta 0.00 Grad
     *   Bewegung     bbs= 126.760   vanilla= 124.711   Delta 2.05 Grad
     *   Bewegung     bbs= 138.680   vanilla= 140.747   Delta 2.07 Grad
     * </pre>
     *
     * <p>2 Grad sind bei Sichtfeld ~91 Grad und 1280 px Viewport rund 29 px. Genau daran hing
     * der Umriss-Versatz. <b>Die Sonde bleibt</b>: dieselbe Verwechslung ist an jeder Stelle
     * moeglich, die eine der beiden Quellen liest, und {@link Zeichenlage} sieht sie nicht —
     * die misst {@code mainCamera()}, und die ist im ganzen Bild gleich.</p>
     *
     * @param vanillaSicht Vanillas Ansichtsdrehung aus dem Kontext, oder {@code null}
     */
    public static void sichtvergleich(org.joml.Matrix4fc vanillaSicht)
    {
        if (System.getProperty("bbs.mess") == null)
        {
            return;
        }

        try
        {
            org.joml.Vector3f b = new org.joml.Vector3f(0F, 0F, 1F);
            org.joml.Vector3f v = new org.joml.Vector3f(0F, 0F, 1F);

            new org.joml.Matrix3f(mchorse.bbs_mod.client.BBSRendering.camera).transform(b);
            new org.joml.Matrix3f(new org.joml.Matrix4f(vanillaSicht)).transform(v);

            Messpunkt.zaehle("sicht.stencil", String.format(java.util.Locale.ROOT,
                "bbs=%.3f | vanilla=%.3f", Math.toDegrees(Math.atan2(-b.x, b.z)),
                Math.toDegrees(Math.atan2(-v.x, v.z))));
        }
        catch (Throwable t)
        {
            Messpunkt.stoerung("sicht.stencil", String.valueOf(t));
        }
    }

    /**
     * Die endgueltige Zeichenlage des Auswahlpuffers in EINE Messpunkt-Zeile.
     *
     * @param stapel  der Weltstapel NACH setIdentity() und der Kamera-Multiplikation
     * @param figur   die Figur, die gleich in den Puffer gezeichnet wird, oder {@code null}
     * @param spielt  laeuft der Film gerade (steuert den Interpolationswert im Fuellweg)
     */
    public static void melden(PoseStack stapel, IEntity figur, boolean spielt)
    {
        /* Schalter zuerst: sonst laeuft der StringBuilder in jedem Bild mit, obwohl niemand
         * misst. Dieselbe Reihenfolge wie in Zeichenlage#melden, aus demselben Grund. */
        if (System.getProperty("bbs.mess") == null)
        {
            return;
        }

        try
        {
            org.joml.Matrix4f m = new org.joml.Matrix4f(stapel.last().pose());
            org.joml.Vector3f richtung = new org.joml.Vector3f(0F, 0F, 1F);

            /* Blickrichtung statt sechzehn Zahlen: zwei Rotationsmatrizen sind als Zahlen
             * nicht vergleichbar, zwei Winkel schon. */
            new org.joml.Matrix3f(m).transform(richtung);

            net.minecraft.world.phys.Vec3 kamera =
                Minecraft.getInstance().gameRenderer.mainCamera().position();

            Messpunkt.zaehle("fuellen.lage", String.format(java.util.Locale.ROOT,
                "matrix-transl=%.4f %.4f %.4f | matrix-gier=%.3f | figur=%s "
                + "| kamera=%.3f %.3f %.3f | spielt=%s | bild=%d",
                m.m30(), m.m31(), m.m32(),
                Math.toDegrees(Math.atan2(-richtung.x, richtung.z)),
                figur == null ? "null" : String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f",
                    figur.getX(), figur.getY(), figur.getZ()),
                kamera.x, kamera.y, kamera.z, spielt,
                Minecraft.getInstance().getFrameTimeNs()));
        }
        catch (Throwable t)
        {
            /* Eine Sonde, die das gemessene Programm reisst, ist wertlos — aber sie muss
             * LAUT scheitern, sonst liest sich ihr Schweigen wie ein Befund. */
            Messpunkt.stoerung("fuellen.lage", String.valueOf(t));
        }
    }
}
