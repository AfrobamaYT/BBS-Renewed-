package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.client.BBSRendering;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded vertex submission for the editor's explicitly POSITION_COLOR control buffer.
 * Geometry and draw order are unchanged. Other builders, shader packs and absent Sodium
 * retain the scalar vertex path. The scope must close before unrelated writes/build().
 */
public final class ControlVertexBatch implements AutoCloseable
{
    private static boolean enabled = !Boolean.getBoolean("bbs.controls.scalarVertices");
    private static ControlVertexBatch current;

    /** Persistent storage of the control passes, see {@link #speicher}. Created lazily. */
    private static ByteBufferBuilder[] speicher;

    private final BufferBuilder builder;
    private final Writer writer;
    private final MemoryStack stack;
    private final ByteBuffer vertices;
    private final ControlVertexBatch previous;
    private boolean closed;

    @FunctionalInterface
    private interface Writer
    {
        void push(MemoryStack stack, long address, int count);
    }

    public static ControlVertexBatch begin(BufferBuilder builder)
    {
        RenderSystem.assertOnRenderThread();

        if (!enabled || !FabricLoader.getInstance().isModLoaded("sodium")
            || BBSRendering.isIrisShadersEnabled() || MemoryStack.stackGet().getPointer() < 16336)
        {
            return null;
        }

        /* The optional API is resolved only after Sodium was found. */
        return Sodium.begin(builder);
    }

    /**
     * Reused vertex storage for one control pass ({@code 0} world, {@code 1} picking).
     *
     * <p>Both passes started a fresh ByteBufferBuilder every frame (4 KB and 256 KB) and grew
     * it to ~0.5 MB by doubling copies; vanilla keeps such builders per render type as well.
     * The mesh built from it must be closed before the pass returns, as SofortZeichner and
     * GehaltenesNetz do; {@code clear()} otherwise warns and discards.
     */
    public static ByteBufferBuilder speicher(int pass)
    {
        RenderSystem.assertOnRenderThread();

        if (speicher == null)
        {
            speicher = new ByteBufferBuilder[2];
        }

        if (speicher[pass] == null)
        {
            speicher[pass] = new ByteBufferBuilder(1 << 20);
        }

        speicher[pass].clear();

        return speicher[pass];
    }

    public static boolean quad(BufferBuilder builder,
        float x1, float y1, float z1, float x2, float y2, float z2,
        float x3, float y3, float z3, float x4, float y4, float z4, int colorA, int colorB)
    {
        ControlVertexBatch batch = current;

        if (batch == null || batch.builder != builder)
        {
            return false;
        }

        if (batch.vertices.remaining() < 96)
        {
            batch.flush();
        }

        int a = ARGB.toABGR(colorA);
        int b = ARGB.toABGR(colorB);

        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN)
        {
            a = Integer.reverseBytes(a);
            b = Integer.reverseBytes(b);
        }

        ByteBuffer target = batch.vertices;
        target.putFloat(x1).putFloat(y1).putFloat(z1).putInt(a);
        target.putFloat(x2).putFloat(y2).putFloat(z2).putInt(b);
        target.putFloat(x3).putFloat(y3).putFloat(z3).putInt(b);
        target.putFloat(x1).putFloat(y1).putFloat(z1).putInt(a);
        target.putFloat(x3).putFloat(y3).putFloat(z3).putInt(b);
        target.putFloat(x4).putFloat(y4).putFloat(z4).putInt(a);
        return true;
    }

    private ControlVertexBatch(BufferBuilder builder, Writer writer)
    {
        this.builder = builder;
        this.writer = writer;
        this.previous = current;

        if (this.previous != null)
        {
            this.previous.flush();
        }

        this.stack = MemoryStack.stackPush();

        try
        {
            this.vertices = this.stack.malloc(16320).order(ByteOrder.nativeOrder());
        }
        catch (Throwable failure)
        {
            this.stack.close();
            throw failure;
        }

        current = this;
    }

    private void flush()
    {
        int bytes = this.vertices.position();

        if (bytes == 0)
        {
            return;
        }

        this.vertices.flip();
        this.writer.push(this.stack, MemoryUtil.memAddress(this.vertices), bytes / 16);
        this.vertices.clear();
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        if (current != this)
        {
            throw new IllegalStateException("Control vertex scopes must close in order");
        }

        try
        {
            this.flush();
        }
        finally
        {
            current = this.previous;
            this.closed = true;
            this.stack.close();
        }
    }

    private static final class Sodium
    {
        private static ControlVertexBatch begin(BufferBuilder builder)
        {
            net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter writer =
                net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter.tryOf(builder);

            return writer == null ? null : new ControlVertexBatch(builder,
                (stack, address, count) -> writer.push(stack, address, count, DefaultVertexFormat.POSITION_COLOR));
        }
    }
}
