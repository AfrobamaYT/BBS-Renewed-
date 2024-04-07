package mchorse.bbs_mod;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ActorEntityRenderer;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.ModelBlockItemRenderer;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.forms.categories.FormCategories;
import mchorse.bbs_mod.graphics.FramebufferManager;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.watchdog.WatchDog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.Collections;

public class BBSModClient implements ClientModInitializer
{
    private static TextureManager textures;
    private static FramebufferManager framebuffers;
    private static SoundManager sounds;
    private static L10n l10n;

    private static ModelManager models;
    private static FormCategories formCategories;
    private static WatchDog watchDog;
    private static ScreenshotRecorder screenshotRecorder;
    private static VideoRecorder videoRecorder;

    private static SimpleFramebuffer framebuffer;
    private static Texture texture;

    private static KeyBinding keyDashboard;
    private static KeyBinding keyModelBlockEditor;
    private static KeyBinding keyToggleRecording;

    private static UIDashboard dashboard;

    private static CameraController cameraController = new CameraController();
    private static Films films;
    private static ModelBlockItemRenderer modelBlockItemRenderer = new ModelBlockItemRenderer();

    public static TextureManager getTextures()
    {
        return textures;
    }

    public static FramebufferManager getFramebuffers()
    {
        return framebuffers;
    }

    public static SoundManager getSounds()
    {
        return sounds;
    }

    public static L10n getL10n()
    {
        return l10n;
    }

    public static ModelManager getModels()
    {
        return models;
    }

    public static FormCategories getFormCategories()
    {
        return formCategories;
    }

    public static ScreenshotRecorder getScreenshotRecorder()
    {
        return screenshotRecorder;
    }

    public static VideoRecorder getVideoRecorder()
    {
        return videoRecorder;
    }

    public static CameraController getCameraController()
    {
        return cameraController;
    }

    public static Films getFilms()
    {
        return films;
    }

    public static SimpleFramebuffer getFramebuffer()
    {
        return framebuffer;
    }

    public static UIDashboard getDashboard()
    {
        if (dashboard == null)
        {
            dashboard = new UIDashboard();
        }

        return dashboard;
    }

    public static void renderToFramebuffer()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        int windowWidth = mc.getWindow().getFramebufferWidth();
        int windowHeight = mc.getWindow().getFramebufferHeight();

        int width = BBSSettings.videoWidth.get();
        int height = BBSSettings.videoHeight.get();

        if (framebuffer == null)
        {
            framebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        }

        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height)
        {
            framebuffer.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
        }

        /* Replace some global values */
        mc.getWindow().setFramebufferWidth(width);
        mc.getWindow().setFramebufferHeight(height);

        /* Render world to texture */
        framebuffer.beginWrite(true);

        RenderSystem.getModelViewStack().push();
        RenderSystem.getModelViewStack().loadIdentity();
        RenderSystem.applyModelViewMatrix();

        mc.gameRenderer.setRenderingPanorama(true);
        mc.gameRenderer.renderWorld(mc.getTickDelta(), 0, new MatrixStack());
        mc.gameRenderer.setRenderingPanorama(false);

        framebuffer.endWrite();

        /* Reset global stuff */
        mc.getWindow().setFramebufferWidth(windowWidth);
        mc.getWindow().setFramebufferHeight(windowHeight);
    }

    @Override
    public void onInitializeClient()
    {
        AssetProvider provider = BBSMod.getProvider();

        textures = new TextureManager(provider);
        framebuffers = new FramebufferManager();
        sounds = new SoundManager(provider);
        l10n = new L10n();
        l10n.register((lang) -> Collections.singletonList(Link.assets("strings/" + lang + ".json")));
        l10n.reload();

        models = new ModelManager(provider);
        formCategories = new FormCategories();
        formCategories.setup();
        watchDog = new WatchDog(BBSMod.getAssetsFolder(), (runnable) -> MinecraftClient.getInstance().execute(runnable));
        watchDog.register(textures);
        watchDog.register(models);
        watchDog.register(sounds);
        watchDog.start();
        screenshotRecorder = new ScreenshotRecorder(BBSMod.getGamePath("screenshots"));
        videoRecorder = new VideoRecorder(BBSMod.getGamePath("movies"));
        films = new Films();

        KeybindSettings.registerClasses();

        BBSMod.setupConfig(Icons.KEY_CAP, "keybinds", new File(BBSMod.getSettingsFolder(), "keybinds.json"), KeybindSettings::register);
        BBSData.load(BBSMod.getDataFolder());

        BBSSettings.tooltipStyle.modes(
            UIKeys.ENGINE_TOOLTIP_STYLE_LIGHT,
            UIKeys.ENGINE_TOOLTIP_STYLE_DARK
        );

        BBSSettings.keystrokeMode.modes(
            UIKeys.ENGINE_KEYSTROKES_POSITION_AUTO,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_LEFT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_LEFT
        );

        /* Replace audio clip with client version that plays audio */
        BBSMod.getFactoryCameraClips()
            .register(Link.bbs("audio"), AudioClientClip.class, new ClipFactoryData(Icons.SOUND, 0xffc825));

        /* Keybind shenanigans */
        keyDashboard = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".dashboard",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "category." + BBSMod.MOD_ID + ".main"
        ));

        keyModelBlockEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".block_editor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_HOME,
            "category." + BBSMod.MOD_ID + ".main"
        ));

        keyToggleRecording = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".toggle_recording",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            "category." + BBSMod.MOD_ID + ".main"
        ));

        WorldRenderEvents.AFTER_ENTITIES.register((context) ->
        {
            if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
            {
                screen.renderInWorld(context);
            }
        });

        WorldRenderEvents.LAST.register((context) ->
        {
            if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
            {
                screen.lastRender(context);
            }

            films.render(context);

            if (texture == null)
            {
                texture = new Texture();
                texture.setFormat(TextureFormat.RGB_U8);
                texture.setFilter(GL11.GL_NEAREST);
            }

            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

            texture.bind();
            texture.setSize(framebuffer.textureWidth, framebuffer.textureHeight);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
            texture.unbind();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            dashboard = null;
        });

        ClientTickEvents.START_CLIENT_TICK.register((client) ->
        {
            BBSRendering.startTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
            {
                screen.update();
            }

            cameraController.update();
            films.update();
            modelBlockItemRenderer.update();

            while (keyDashboard.wasPressed())
            {
                UIScreen.open(getDashboard());
            }

            while (keyModelBlockEditor.wasPressed())
            {
                ItemStack stack = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.MAINHAND);

                if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == BBSMod.MODEL_BLOCK)
                {
                    ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);

                    if (item != null)
                    {
                        UIScreen.open(new UIModelBlockEditorMenu(item));
                    }
                }
            }

            while (keyToggleRecording.wasPressed())
            {
                videoRecorder.toggleRecording(texture);
            }

            videoRecorder.recordFrame();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((e) ->
        {
            watchDog.stop();
        });

        BBSRendering.setup();

        /* Network */
        ClientNetwork.setup();

        /* Entity renderers */
        EntityRendererRegistry.register(BBSMod.ACTOR_ENTITY, ActorEntityRenderer::new);

        BlockEntityRendererFactories.register(BBSMod.MODEL_BLOCK_ENTITY, ModelBlockEntityRenderer::new);

        BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.MODEL_BLOCK_ITEM, modelBlockItemRenderer);
    }
}