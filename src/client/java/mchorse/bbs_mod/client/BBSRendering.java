package mchorse.bbs_mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.impl.client.rendering.WorldRenderContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class BBSRendering
{
    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static boolean renderingWorld;
    public static int lastAction;

    private static boolean customSize;
    private static boolean iris;

    private static Texture texture;

    public static int getMotionBlur()
    {
        return getMotionBlur(BBSSettings.videoSettings.frameRate.get(), getMotionBlurFactor());
    }

    public static int getMotionBlur(double fps, int target)
    {
        int i = 0;

        while (fps < target)
        {
            fps *= 2;

            i++;
        }

        return i;
    }

    public static int getMotionBlurFactor()
    {
        return getMotionBlurFactor(BBSSettings.videoSettings.motionBlur.get());
    }

    public static int getMotionBlurFactor(int integer)
    {
        return integer == 0 ? 0 : (int) Math.pow(2, 6 + integer);
    }

    public static int getVideoWidth()
    {
        return BBSSettings.videoSettings.width.get();
    }

    public static int getVideoHeight()
    {
        return BBSSettings.videoSettings.height.get();
    }

    public static int getVideoFrameRate()
    {
        int frameRate = BBSSettings.videoSettings.frameRate.get();

        return frameRate * (1 << getMotionBlur(frameRate, getMotionBlurFactor()));
    }

    public static File getVideoFolder()
    {
        File movies = new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        File exportPath = new File(BBSSettings.videoSettings.path.get());

        if (exportPath.isDirectory())
        {
            movies = exportPath;
        }

        movies.mkdirs();

        return movies;
    }

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
        iris = FabricLoader.getInstance().isModLoaded("iris");

        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });

        if (!iris)
        {
            return;
        }

        IrisUtils.setup();
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

    public static void onRenderChunkLayer(MatrixStack stack)
    {
        WorldRenderContextImpl worldRenderContext = new WorldRenderContextImpl();
        MinecraftClient mc = MinecraftClient.getInstance();

        worldRenderContext.prepare(
            mc.worldRenderer, stack, mc.getTickDelta(), mc.getRenderTime(), false,
            mc.gameRenderer.getCamera(), mc.gameRenderer, mc.gameRenderer.getLightmapTextureManager(),
            RenderSystem.getProjectionMatrix(), mc.getBufferBuilders().getEntityVertexConsumers(), null, false, mc.world
        );

        if (isIrisShadersEnabled())
        {
            renderCoolStuff(worldRenderContext);
        }
    }

    public static void renderHud(DrawContext drawContext, float tickDelta)
    {
        BBSModClient.getFilms().renderHud(drawContext, tickDelta);
    }

    public static void renderCoolStuff(WorldRenderContext worldRenderContext)
    {
        if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
        {
            screen.renderInWorld(worldRenderContext);
        }

        BBSModClient.getFilms().render(worldRenderContext);
    }

    public static boolean isIrisShadersEnabled()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShaderPackEnabled();
    }

    public static boolean isIrisShadowPass()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShadowPass();
    }

    public static void trackTexture(Texture texture)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.trackTexture(texture);
    }
}