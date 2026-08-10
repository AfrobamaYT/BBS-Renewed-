package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
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

    private static GpuBuffer puffer;

    private UiLicht()
    {}

    /** Nur auf dem Render-Thread, und nur unmittelbar vor dem Vorschau-Zeichnen. */
    public static void setzen()
    {
        RenderSystem.setShaderLights(scheibe());
    }

    public static GpuBufferSlice scheibe()
    {
        if (puffer == null)
        {
            try (MemoryStack stapel = MemoryStack.stackPush())
            {
                puffer = RenderSystem.getDevice().createBuffer(() -> "BBS UI-Lichtrichtungen",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    Std140Builder.onStack(stapel, Lighting.UBO_SIZE)
                        .putVec3(LICHT_A).putVec3(LICHT_B).get());
            }
        }

        return puffer.slice(0, Lighting.UBO_SIZE);
    }
}
