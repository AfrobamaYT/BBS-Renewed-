package mchorse.bbs_mod;

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
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.graphics.FramebufferManager;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.particles.ParticleManager;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.selectors.EntitySelectors;
import mchorse.bbs_mod.settings.values.ValueLanguage;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.menu.UIFilmsMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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
    private static EntitySelectors selectors;

    private static ParticleManager particles;

    private static KeyBinding keyDashboard;
    private static KeyBinding keyModelBlockEditor;
    private static KeyBinding keyFilms;
    /* private static KeyBinding keyToggleRecording; */

    private static UIDashboard dashboard;

    private static CameraController cameraController = new CameraController();
    private static ModelBlockItemRenderer modelBlockItemRenderer = new ModelBlockItemRenderer();
    private static Films films;

    private static boolean requestToggleRecording;
    private static float originalFramebufferScale;

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

    public static EntitySelectors getSelectors()
    {
        return selectors;
    }

    public static ParticleManager getParticles()
    {
        return particles;
    }

    public static CameraController getCameraController()
    {
        return cameraController;
    }

    public static Films getFilms()
    {
        return films;
    }

    public static UIDashboard getDashboard()
    {
        if (dashboard == null)
        {
            dashboard = new UIDashboard();
        }

        return dashboard;
    }

    public static int getGUIScale()
    {
        int scale = BBSSettings.userIntefaceScale.get();

        if (scale == 0)
        {
            return MinecraftClient.getInstance().options.getGuiScale().getValue();
        }

        return scale;
    }

    public static float getOriginalFramebufferScale()
    {
        return originalFramebufferScale;
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

        File parentFile = BBSMod.getSettingsFolder().getParentFile();

        particles = new ParticleManager(() -> new File(BBSMod.getAssetsFolder(), "particles"));

        models = new ModelManager(provider);
        formCategories = new FormCategories();
        formCategories.setup();
        watchDog = new WatchDog(BBSMod.getAssetsFolder(), false, (runnable) -> MinecraftClient.getInstance().execute(runnable));
        watchDog.register(textures);
        watchDog.register(models);
        watchDog.register(sounds);
        watchDog.register(formCategories);
        watchDog.start();
        screenshotRecorder = new ScreenshotRecorder(new File(parentFile, "screenshots"));
        videoRecorder = new VideoRecorder();
        selectors = new EntitySelectors();
        selectors.read();
        films = new Films();

        KeybindSettings.registerClasses();

        BBSMod.setupConfig(Icons.KEY_CAP, "keybinds", new File(BBSMod.getSettingsFolder(), "keybinds.json"), KeybindSettings::register);

        BBSSettings.language.postCallback((v) -> reloadLanguage(((ValueLanguage) v).get()));
        BBSSettings.editorSeconds.postCallback((v) ->
        {
            if (dashboard != null)
            {
                if (dashboard.getPanels().panel instanceof UIFilmPanel panel)
                {
                    panel.fillData();
                }
            }
        });

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

        UIKeys.C_KEYBIND_CATGORIES.load(KeyCombo.getCategoryKeys());
        UIKeys.C_KEYBIND_CATGORIES_TOOLTIP.load(KeyCombo.getCategoryKeys());

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

        keyFilms = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".films",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            "category." + BBSMod.MOD_ID + ".main"
        ));

        /* keyToggleRecording = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".toggle_recording",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F4,
            "category." + BBSMod.MOD_ID + ".main"
        )); */

        WorldRenderEvents.AFTER_ENTITIES.register((context) ->
        {
            if (!BBSRendering.isIrisShadersEnabled())
            {
                BBSRendering.renderCoolStuff(context);
            }
        });

        WorldRenderEvents.LAST.register((context) ->
        {
            if (requestToggleRecording)
            {
                Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

                videoRecorder.toggleRecording(framebuffer.getColorAttachment(), framebuffer.textureWidth, framebuffer.textureHeight);

                requestToggleRecording = false;
            }

            if (videoRecorder.isRecording())
            {
                videoRecorder.recordFrame();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            dashboard = null;
            films = new Films();

            ClientNetwork.resetHandshake();
        });

        ClientTickEvents.START_CLIENT_TICK.register((client) ->
        {
            BBSRendering.startTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.currentScreen instanceof UIScreen screen)
            {
                screen.update();
            }

            cameraController.update();

            if (!mc.isPaused())
            {
                films.update();
                modelBlockItemRenderer.update();
            }

            while (keyDashboard.wasPressed())
            {
                UIScreen.open(getDashboard());
            }

            while (keyModelBlockEditor.wasPressed())
            {
                ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.MAINHAND);

                if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == BBSMod.MODEL_BLOCK)
                {
                    ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);

                    if (item != null)
                    {
                        UIScreen.open(new UIModelBlockEditorMenu(item));
                    }
                }
            }

            while (keyFilms.wasPressed())
            {
                UIScreen.open(new UIFilmsMenu());
            }

            /* while (keyToggleRecording.wasPressed())
            {
                requestToggleRecording = true;
            } */
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
        {
            BBSRendering.renderHud(drawContext, tickDelta);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((e) ->
        {
            watchDog.stop();
        });

        ClientLifecycleEvents.CLIENT_STARTED.register((e) ->
        {
            Window window = MinecraftClient.getInstance().getWindow();

            originalFramebufferScale = window.getFramebufferWidth() / window.getWidth();
        });

        BBSRendering.setup();

        /* Network */
        ClientNetwork.setup();

        /* Entity renderers */
        EntityRendererRegistry.register(BBSMod.ACTOR_ENTITY, ActorEntityRenderer::new);

        BlockEntityRendererFactories.register(BBSMod.MODEL_BLOCK_ENTITY, ModelBlockEntityRenderer::new);

        BuiltinItemRendererRegistry.INSTANCE.register(BBSMod.MODEL_BLOCK_ITEM, modelBlockItemRenderer);
    }

    public static void reloadLanguage(String language)
    {
        if (language.isEmpty())
        {
            language = MinecraftClient.getInstance().options.language;
        }

        l10n.reload(language, BBSMod.getProvider());
    }
}