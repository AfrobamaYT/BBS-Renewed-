package mchorse.bbs_mod.ui.utils;

import com.mojang.renderpearl.api.textures.GpuTexture;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.GpuReadback;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.ToIntBiFunction;

/** Reads the pick IDs from their actual texture, through Mojang's GPU transfer API. */
final class StencilLeser
{
    static final int KEIN_ERGEBNIS = Integer.MIN_VALUE;

    private ByteBuffer sofort;
    private GpuReadback.Request request;
    private int[] leseLage;

    /** Borrowed CPU buffer, valid until the next read or discard on this reader. */
    ByteBuffer lesenSofort(Framebuffer framebuffer, int x0, int y0, int w, int h)
    {
        if (this.sofort != null) MemoryUtil.memFree(this.sofort);
        this.sofort = null;
        this.sofort = GpuReadback.readRgba8(texture(framebuffer), new GpuReadback.Region(x0, y0, w, h), GpuReadback.Origin.NATIVE);
        return this.sofort;
    }

    void anfordern(Framebuffer framebuffer, int[] lage)
    {
        if (this.request != null) throw new IllegalStateException("Resolve or discard the previous pick request first");
        this.leseLage = lage.clone();
        this.request = GpuReadback.requestRaw(texture(framebuffer),
            new GpuReadback.Region(lage[0], lage[1], lage[2], lage[3]));
    }

    boolean ausstehend()
    {
        return this.request != null;
    }

    int aufloesen(boolean warten, ToIntBiFunction<ByteBuffer, int[]> auswertung)
    {
        if (this.request == null) return KEIN_ERGEBNIS;
        GpuReadback.Raw raw = warten ? this.request.await() : this.request.poll();
        if (raw == null) return KEIN_ERGEBNIS;
        GpuReadback.Request owned = this.request;
        this.request = null;
        ByteBuffer rgba = null;

        try (owned; raw)
        {
            rgba = raw.rgba8(GpuReadback.Origin.NATIVE);
            return auswertung.applyAsInt(rgba, this.leseLage);
        }
        finally
        {
            if (rgba != null) MemoryUtil.memFree(rgba);
            this.leseLage = null;
        }
    }

    void verwerfen()
    {
        if (this.request != null) this.request.close();
        this.request = null;
        this.leseLage = null;
        if (this.sofort != null) MemoryUtil.memFree(this.sofort);
        this.sofort = null;
    }

    private static GpuTexture texture(Framebuffer framebuffer)
    {
        return framebuffer == null
            ? Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture()
            : framebuffer.getMainTexture().gpu();
    }
}
