package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import mchorse.bbs_mod.compat.mixin.RenderSystemLichtAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.FogParameters;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * Die Vorgabe-Uniforms von 1.21.6+ (Projection, Fog, Globals, Lighting) fuer die Schicht vor
 * 1.21.5, gebaut aus 1.21.4s Zustand (Projektionsmatrix, FogParameters, Lichtrichtungen).
 * Ersetzt per Regel RenderSystem.getProjectionMatrixBuffer/getShaderFog/getShaderLights/
 * setShaderLights/bindDefaultUniforms/getSamplerCache/getDynamicUniforms.
 */
public final class Vorgaben
{
    private static final SamplerCache SAMPLER = new SamplerCache();
    private static final DynamicUniforms TRANSFORMS = new DynamicUniforms();
    private static GpuBufferSlice lichtGesetzt;
    private static com.mojang.blaze3d.buffers.GpuBuffer globalsPuffer;

    private Vorgaben()
    {}

    public static SamplerCache samplerCache() { return SAMPLER; }
    public static DynamicUniforms dynamicUniforms() { return TRANSFORMS; }

    public static GpuBufferSlice projektion()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            return hochladen(Std140Builder.onStack(stack, 64).putMat4f(RenderSystem.getProjectionMatrix()).get());
        }
    }

    /** 1.21.4s Nebelparameter selbst (fuer Leser, die die Farbe brauchen, nicht den Uniform). */
    public static FogParameters nebelParameter()
    {
        return Nebel.aktuell();
    }

    /** 1.21.4 kennt einen Nebel mit Form: Kugel = Umgebungsnebel, Zylinder = Sichtweitennebel. */
    public static GpuBufferSlice nebel()
    {
        FogParameters f = Nebel.aktuell();
        boolean kugel = f.shape() == FogShape.SPHERE;
        float fern = Float.MAX_VALUE;

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            return hochladen(Std140Builder.onStack(stack, 48).putVec4(f.red(), f.green(), f.blue(), f.alpha())
                .putFloat(kugel ? f.start() : fern).putFloat(kugel ? f.end() : fern)
                .putFloat(kugel ? fern : f.start()).putFloat(kugel ? fern : f.end())
                .putFloat(f.end()).putFloat(f.end()).get());
        }
    }

    public static GpuBufferSlice globals()
    {
        Minecraft mc = Minecraft.getInstance();

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            return hochladen(Std140Builder.onStack(stack, 64).putIVec3(0, 0, 0).putVec3(0F, 0F, 0F)
                .putVec2(mc.getWindow().getWidth(), mc.getWindow().getHeight()).putFloat(mc.options.glintStrength().get().floatValue())
                .putFloat(RenderSystem.getShaderGameTime()).putInt(mchorse.bbs_mod.compat.alt.AltApi.menueUnschaerfe()).putInt(0).get());
        }
    }

    /** RenderSystem.getGlobalSettingsUniform: ein fester Puffer (Vergleich per Identitaet), je Abruf neu befuellt. */
    public static com.mojang.blaze3d.buffers.GpuBuffer globalsPuffer()
    {
        GpuBufferSlice neu = globals();

        if (globalsPuffer == null)
        {
            globalsPuffer = mchorse.bbs_mod.compat.Geraet.get().createBuffer(() -> "bbs-globals",
                PufferNutzung.USAGE_UNIFORM | PufferNutzung.USAGE_COPY_DST, neu.length());
        }

        mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().copyToBuffer(neu, globalsPuffer.slice());

        return globalsPuffer;
    }

    public static GpuBufferSlice licht()
    {
        if (lichtGesetzt != null)
        {
            return lichtGesetzt;
        }

        Vector3f[] r = RenderSystemLichtAccessor.bbs$lichtrichtungen();

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            return hochladen(Std140Builder.onStack(stack, 32).putVec3(r[0]).putVec3(r[1]).get());
        }
    }

    /** 26.x setzt das Licht als fertigen Block; null kehrt zu 1.21.4s Lichtrichtungen zurueck. */
    public static void setzeLicht(GpuBufferSlice licht)
    {
        lichtGesetzt = licht;
    }

    public static void setzeLicht(Vector3f licht0, Vector3f licht1)
    {
        lichtGesetzt = null;
        RenderSystem.setShaderLights(licht0, licht1);
    }

    public static void bindDefaultUniforms(RenderPass pass)
    {
        pass.setUniform("Projection", projektion());
        pass.setUniform("Fog", nebel());
        pass.setUniform("Globals", globals());
        pass.setUniform("Lighting", licht());
    }

    /** Beim Bildwechsel: gesetztes Licht gilt nur im eigenen Bild. */
    public static void bildEnde()
    {
        lichtGesetzt = null;
        UniformDaten.bildEnde();
    }

    private static GpuBufferSlice hochladen(java.nio.ByteBuffer daten)
    {
        return UniformDaten.hochladen(daten);
    }
}
