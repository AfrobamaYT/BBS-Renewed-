package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public class CustomVertexConsumerProvider implements VertexConsumerProvider
{
    private BufferBuilder builder;
    private RenderLayer currentLayer;
    private boolean ui;

    private Map<VertexFormat, Runnable> runnables = new HashMap<>();

    public CustomVertexConsumerProvider()
    {
        this.builder = new BufferBuilder(1536);
    }

    public void setUI(boolean ui)
    {
        this.ui = ui;
    }

    public void hijackVertexFormat(VertexFormat format, Runnable runnable)
    {
        this.runnables.put(format, runnable);
    }

    public void clearRunnables()
    {
        this.runnables.clear();
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer)
    {
        if (this.currentLayer != null && this.currentLayer != layer)
        {
            this.draw();
        }

        this.currentLayer = layer;

        if (!this.builder.isBuilding())
        {
            this.builder.begin(layer.getDrawMode(), layer.getVertexFormat());
        }

        return this.builder;
    }

    public void draw()
    {
        if (this.builder.isBuilding())
        {
            BufferBuilder.BuiltBuffer builtBuffer = this.builder.end();
            VertexFormat vertexFormat = this.currentLayer.getVertexFormat();
            Runnable runnable = this.runnables.get(vertexFormat);

            this.currentLayer.startDrawing();

            if (runnable != null)
            {
                runnable.run();
            }

            BufferRenderer.drawWithGlobalProgram(builtBuffer);

            this.currentLayer.endDrawing();

            this.currentLayer = null;
        }

        if (this.ui)
        {
            /* Force back the depth func because it seems like stuff rendered by a vertex
             * consumer is resetting the depth func to GL_LESS, and since this vertex consumer
             * is designed  */
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }
}