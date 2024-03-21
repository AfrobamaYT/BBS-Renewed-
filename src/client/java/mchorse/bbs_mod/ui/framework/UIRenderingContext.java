package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

public class UIRenderingContext
{
    public Batcher2D batcher;

    public UIRenderingContext(DrawContext context)
    {
        this.batcher = new Batcher2D(context);
    }

    /* Rendering context implementations */

    public boolean isDebug()
    {
        return false;
    }

    public World getWorld()
    {
        return MinecraftClient.getInstance().world;
    }

    public TextRenderer getFont()
    {
        return MinecraftClient.getInstance().textRenderer;
    }

    public TextureManager getTextures()
    {
        return BBSModClient.getTextures();
    }
}
