package mchorse.bbs_mod.graphics;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * Die zwei Lichtrichtungen, mit denen BBS seine Form-Vorschauen in der Oberflaeche
 * ausleuchtet. In 1.21.1 war das ein Aufruf: {@code setupLevelDiffuseLighting(a, b)}.
 *
 * <p>⛔ Vanillas {@link Lighting.Entry}-Voreinstellungen ersetzen ihn NICHT. Gemessen am
 * 2026-08-10 im A/B gegen 1.21.1: der beim Zeichnen gebundene Block trug
 * {@code Light0 = (-0.937, -0.263, -0.247)} — keine der fuenf Voreinstellungen. Gegen die
 * Modellnormalen (ueberwiegend +y/+z) sind damit beide Skalarprodukte <= 0, also bleibt der
 * Ambient-Boden {@code MINECRAFT_AMBIENT_LIGHT 0.4} aus {@code light.glsl} uebrig: Faktor
 * 0.400 auf 13749 Modellpixeln, waehrend 1.21.1 dort 1.0 zeichnet.
 *
 * <p>⚠️ LEVEL, ITEMS_FLAT und ITEMS_3D tragen im Overworld dieselben Vektoren. Ein Wechsel
 * zwischen ihnen aendert kein Pixel — das sieht wie „der Block wird nicht gelesen" aus und
 * ist es nicht.
 */
public final class UiLicht
{
    private static final Vector3f LICHT_A = new Vector3f(0F, 1F, -0.2F).normalize();
    private static final Vector3f LICHT_B = new Vector3f(-0.85F, 0.85F, 1F).normalize();

    /**
     * Die Richtungen des Editors, woertlich aus 1.21.1 (UIModelRenderer): {@code (0, 0.85, ∓1)},
     * normalisiert und mit {@code camera.view} gedreht — das Licht folgt der Umlaufkamera.
     */
    private static final Vector3f EDITOR_A = new Vector3f(0F, 0.85F, -1F).normalize();
    private static final Vector3f EDITOR_B = new Vector3f(0F, 0.85F, 1F).normalize();

    private static GpuBuffer puffer;

    /** Die Ansicht der Editor-Kamera, oder {@code null} fuer die festen Vorschau-Richtungen. */
    private static Matrix3f ansicht;

    private UiLicht()
    {}

    /**
     * Meldet die Kamera einer Vorschau an, damit ihr Licht mitdreht.
     *
     * <p>⛔ WOFUER (2026-08-11, ueber den Umlaufwinkel gemessen). In 1.21.1 drehten sich die
     * beiden Richtungen mit {@code camera.view}; 26.2 verlor das beim Portieren, weil
     * {@code setupLevelDiffuseLighting} entfiel. Mit festen Richtungen weicht die Helligkeit
     * je nach Blickwinkel ab — gemessen an {@code player/steve_bends}, Rot-Mittel ueber die
     * Modellpunkte:
     *
     * <pre>
     *   yaw    1.21.1   26.2   Faktor
     *     0      157     141    0,90
     *    60      124     150    1,21   &lt;- heller, nicht dunkler
     *   120      126     117    0,93
     *   200      156     127    0,81
     * </pre>
     *
     * <p>Ein konstanter Dunkelfaktor kann das nicht sein; es ist eine andere Richtung. Wer
     * anmeldet, meldet auch wieder ab — sonst erbt die naechste Vorschau diese Kamera.
     */
    public static void ansicht(Matrix3f kameraAnsicht)
    {
        ansicht = kameraAnsicht == null ? null : new Matrix3f(kameraAnsicht);
    }

    /** Zurueck auf die festen Vorschau-Richtungen (Listen-Miniaturen haben keine Kamera). */
    public static void ansichtLoeschen()
    {
        ansicht = null;
    }

    /** Nur auf dem Render-Thread, und nur unmittelbar vor dem Vorschau-Zeichnen. */
    public static void setzen()
    {
        RenderSystem.setShaderLights(scheibe());
    }

    public static GpuBufferSlice scheibe()
    {
        Matrix3f dreh = ansicht;
        Vector3f a = dreh == null ? LICHT_A : dreh.transform(new Vector3f(EDITOR_A));
        Vector3f b = dreh == null ? LICHT_B : dreh.transform(new Vector3f(EDITOR_B));

        try (MemoryStack stapel = MemoryStack.stackPush())
        {
            java.nio.ByteBuffer inhalt = Std140Builder.onStack(stapel, Lighting.UBO_SIZE)
                .putVec3(a).putVec3(b).get();

            /* Ein Puffer, je Aufruf neu beschrieben — dasselbe Muster wie der Modellblock in
             * ModelVAORenderer. Neu ANLEGEN je Bild waere ein Leck. */
            if (puffer == null)
            {
                puffer = RenderSystem.getDevice().createBuffer(() -> "BBS UI-Lichtrichtungen",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, inhalt);
            }
            else
            {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(puffer.slice(), inhalt);
            }
        }

        return puffer.slice(0, Lighting.UBO_SIZE);
    }
}
