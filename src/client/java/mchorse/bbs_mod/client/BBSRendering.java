package mchorse.bbs_mod.client;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
    public static boolean customSize;

    private static Texture texture;

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
        Window window = MinecraftClient.getInstance().getWindow();

        renderingWorld = false;

        framebuffer.resize(window.getFramebufferWidth(), window.getFramebufferHeight(), false);
        framebuffer.beginWrite(true);

        /* Debug preview: if (texture != null)
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

    public static void onLastRender(WorldRenderContext context)
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

        texture.bind();
        texture.setSize(framebuffer.textureWidth, framebuffer.textureHeight);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
        texture.unbind();
    }
}