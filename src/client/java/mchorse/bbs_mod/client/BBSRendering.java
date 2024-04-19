package mchorse.bbs_mod.client;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

public class BBSRendering
{
    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static boolean renderingWorld;
    private static boolean customSize;

    private static Texture texture;

    public static boolean isCustomSize()
    {
        return customSize;
    }

    public static void setCustomSize(boolean customSize)
    {
        BBSRendering.customSize = customSize;

        if (!customSize)
        {
            Framebuffer efb = MinecraftClient.getInstance().worldRenderer.getEntityOutlinesFramebuffer();
            Window window = MinecraftClient.getInstance().getWindow();

            efb.resize(window.getFramebufferWidth(), window.getFramebufferHeight(), false);
        }
    }

    public static Texture getTexture()
    {
        return texture;
    }

    public static void startTick()
    {
        capturedModelBlocks.clear();
    }

    public static void setup()
    {
        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });
    }

    public static void onWorldRenderBegin()
    {
        if (!customSize)
        {
            return;
        }

        renderingWorld = true;

        Window window = MinecraftClient.getInstance().getWindow();
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

        framebuffer.resize(window.getFramebufferWidth(), window.getFramebufferHeight(), false);
        framebuffer.beginWrite(true);

        Framebuffer efb = MinecraftClient.getInstance().worldRenderer.getEntityOutlinesFramebuffer();

        if (efb != null && (efb.viewportWidth != window.getFramebufferWidth() || efb.viewportHeight != window.getFramebufferHeight()))
        {
            efb.resize(window.getFramebufferWidth(), window.getFramebufferHeight(), false);
        }
    }

    public static void onWorldRenderEnd()
    {
        if (!customSize)
        {
            return;
        }

        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

        if (texture == null)
        {
            texture = new Texture();
            texture.setFormat(TextureFormat.RGB_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();

        if (currentMenu instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                UISubtitleRenderer.renderSubtitles(currentMenu.context, SubtitleClip.getSubtitles(panel.getRunner().getContext()));
            }
        }

        texture.bind();
        texture.setSize(framebuffer.textureWidth, framebuffer.textureHeight);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
        texture.unbind();

        Window window = MinecraftClient.getInstance().getWindow();

        renderingWorld = false;

        framebuffer.resize(window.getFramebufferWidth(), window.getFramebufferHeight(), false);
        framebuffer.beginWrite(true);

        /* For preview: if (texture != null)
        {
            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            builder.vertex(-1F, -1F, 0F).texture(0F, 0F).color(Colors.WHITE).next();
            builder.vertex(-1F, 1F, 0F).texture(0F, 1F).color(Colors.WHITE).next();
            builder.vertex(1F, 1F, 0F).texture(1F, 1F).color(Colors.WHITE).next();
            builder.vertex(1F, -1F, 0F).texture(1F, 0F).color(Colors.WHITE).next();

            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.setShaderTexture(0, texture.id);
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorter.BY_Z);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

            BufferRenderer.drawWithGlobalProgram(builder.end());
        } */
    }
}