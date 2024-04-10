package mchorse.bbs_mod.forms;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;

import java.util.HashMap;
import java.util.Map;

public class CustomVertexConsumerProvider implements VertexConsumerProvider
{
    private BufferBuilder builder;
    private RenderLayer currentLayer;

    private Map<VertexFormat, Runnable> runnables = new HashMap<>();

    public CustomVertexConsumerProvider()
    {
        this.builder = new BufferBuilder(1536);
    }

    public void hijackVertexFormat(VertexFormat format, Runnable runnable)
    {
        this.runnables.put(format, runnable);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer)
    {
        if (this.currentLayer != null && this.currentLayer != layer)
        {
            this.draw();
        }

        this.currentLayer = layer;

        this.builder.begin(layer.getDrawMode(), layer.getVertexFormat());

        return this.builder;
    }

    public void draw()
    {
        if (this.builder.isBuilding())
        {
            BufferBuilder.BuiltBuffer builtBuffer = this.builder.end();
            Runnable remove = this.runnables.remove(this.currentLayer.getVertexFormat());

            this.currentLayer.startDrawing();

            if (remove != null)
            {
                remove.run();
            }

            BufferRenderer.drawWithGlobalProgram(builtBuffer);

            this.currentLayer.endDrawing();

            this.currentLayer = null;
        }
    }
}