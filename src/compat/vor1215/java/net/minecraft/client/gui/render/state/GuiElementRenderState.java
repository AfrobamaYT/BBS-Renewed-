package net.minecraft.client.gui.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;

/* BBS-Shim fuer Minecraft vor 1.21.6: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public interface GuiElementRenderState extends ScreenArea
{
    void buildVertices(VertexConsumer consumer);

    RenderPipeline pipeline();

    TextureSetup textureSetup();

    ScreenRectangle scissorArea();
}
