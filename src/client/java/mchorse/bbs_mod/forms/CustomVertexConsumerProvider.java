package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CustomVertexConsumerProvider extends VertexConsumerProvider.Immediate
{
    private Map<VertexFormat, Runnable> runnables = new HashMap<>();
    private boolean ui;

    public CustomVertexConsumerProvider(BufferBuilder fallback, Map<RenderLayer, BufferBuilder> layers)
    {
        super(fallback, layers);
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

    public void draw()
    {
        super.draw();

        if (this.ui)
        {
            /* Force back the depth func because it seems like stuff rendered by a vertex
             * consumer is resetting the depth func to GL_LESS, and since this vertex consumer
             * is designed  */
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    public void draw(RenderLayer layer)
    {
        BufferBuilder builder = this.layerBuffers.getOrDefault(layer, this.fallbackBuffer);
        boolean same = Objects.equals(this.currentLayer, layer.asOptional());

        if (!same && builder == this.fallbackBuffer)
        {
            return;
        }
        else if (!this.activeConsumers.remove(builder))
        {
            return;
        }

        Runnable runnable = this.runnables.get(layer.getVertexFormat());

        if (builder.isBuilding()) {

            layer.startDrawing();

            if (runnable != null)
            {
                runnable.run();
            }

            BufferRenderer.drawWithGlobalProgram(builder.end());

            layer.endDrawing();
        }

        if (same)
        {
            this.currentLayer = Optional.empty();
        }
    }
}