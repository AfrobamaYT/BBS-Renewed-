package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public class CustomVertexConsumerProvider extends VertexConsumerProvider.Immediate
{
    private static Map<VertexFormat, Runnable> runnables = new HashMap<>();

    private boolean ui;

    public static void drawLayer(RenderLayer layer)
    {
        Runnable runnable = runnables.get(layer.getVertexFormat());

        if (runnable != null)
        {
            runnable.run();
        }
    }

    public static void hijackVertexFormat(VertexFormat format, Runnable runnable)
    {
        runnables.put(format, runnable);
    }

    public static void clearRunnables()
    {
        runnables.clear();
    }

    public CustomVertexConsumerProvider(BufferBuilder fallback, Map<RenderLayer, BufferBuilder> layers)
    {
        super(fallback, layers);
    }

    public void setUI(boolean ui)
    {
        this.ui = ui;
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
}