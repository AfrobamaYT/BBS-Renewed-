package mchorse.bbs_mod;

import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.renderer.ActorEntityRenderer;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.forms.categories.FormCategories;
import mchorse.bbs_mod.graphics.FramebufferManager;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.UITestMenu;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.watchdog.WatchDog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BBSModClient implements ClientModInitializer
{
    private static TextureManager textures;
    private static FramebufferManager framebuffers;
    private static SoundManager sounds;
    private static L10n l10n;

    private static ModelManager models;
    private static FormCategories formCategories;
    private static WatchDog watchDog;

    private static KeyBinding keyPlay;
    private static KeyBinding keyRecord;

    private static UIDashboard dashboard;

    private static List<Runnable> scheduledRunnables = new ArrayList<>();
    private static int _lastFramebufferSizeW;
    private static int _lastFramebufferSizeH;

    private static CameraController cameraController = new CameraController();

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

    public static CameraController getCameraController()
    {
        return cameraController;
    }

    public static UIDashboard getDashboard()
    {
        if (dashboard == null)
        {
            dashboard = new UIDashboard();
        }

        return dashboard;
    }

    public static void schedule(Runnable runnable)
    {
        scheduledRunnables.add(runnable);
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
        watchDog = new WatchDog(BBSMod.getAssetsFolder(), (runnable) -> schedule(runnable));
        watchDog.register(textures);
        watchDog.register(models);
        watchDog.register(sounds);
        watchDog.start();

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
        keyPlay = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".play",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category." + BBSMod.MOD_ID + ".test"
        ));

        keyRecord = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + ".record",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category." + BBSMod.MOD_ID + ".test"
        ));

        WorldRenderEvents.START.register((client) ->
        {
            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

            _lastFramebufferSizeW = framebuffer.textureWidth;
            _lastFramebufferSizeH = framebuffer.textureHeight;

            // framebuffer.resize(50, 50, false);
        });

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

            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();

            // framebuffer.resize(_lastFramebufferSizeW, _lastFramebufferSizeH, false);
        });

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
            {
                screen.update();
            }

            cameraController.update();

            while (keyPlay.wasPressed())
            {
                MinecraftClient.getInstance().setScreen(new UIScreen(Text.empty(), getDashboard()));
            }

            while (keyRecord.wasPressed())
            {
                MinecraftClient mc = MinecraftClient.getInstance();

                HitResult result = RayTracing.rayTraceEntity(mc.player, mc.world, mc.cameraEntity.getEyePos(), mc.cameraEntity.getRotationVec(0F), 64);

                if (result != null && result.getType() != HitResult.Type.MISS)
                {
                    Vec3d pos = result.getPos();

                    mc.world.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 0D, 0D, 0D);
                }
                else
                {
                    mc.player.sendMessage(Text.literal("I guess you have missed, huh?"));
                }

                MinecraftClient.getInstance().setScreen(new UIScreen(Text.literal("Model renderer"), new UITestMenu()));
            }

            for (Runnable runnable : scheduledRunnables)
            {
                runnable.run();
            }

            scheduledRunnables.clear();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((e) ->
        {
            watchDog.stop();
        });

        /* Network */
        ClientNetwork.setup();

        /* Entity renderers */
        EntityRendererRegistry.register(BBSMod.ACTOR_ENTITY, ActorEntityRenderer::new);

        BlockEntityRendererFactories.register(BBSMod.MODEL_BLOCK_ENTITY, ModelBlockEntityRenderer::new);
    }
}