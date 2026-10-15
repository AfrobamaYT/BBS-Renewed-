package mchorse.bbs_mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.audio.MinecraftSoundCapture;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.CurveClientClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.client.renderer.entity.GunProjectileEntityRenderer;
import mchorse.bbs_mod.client.renderer.item.GunItemRenderer;
import mchorse.bbs_mod.client.renderer.item.ModelBlockItemRenderer;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.events.register.RegisterClientSettingsEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.WorldVideoExportSession;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.FramebufferManager;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.items.GunZoom;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.particles.ParticleManager;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLError;
import mchorse.bbs_mod.resources.packs.URLRepository;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.resources.packs.URLTextureErrorCallback;
import mchorse.bbs_mod.selectors.EntitySelectors;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.QuickReplayWelt;
import mchorse.bbs_mod.ui.film.replays.overlays.UIQuickReplayOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.KeybindSettings;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.MinecraftSourcePack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.BlockEntityRendererRegistryImpl;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Collections;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.SofortZeichner;
import com.mojang.blaze3d.platform.Window;
import com.afrobama.bbstools.client.keybinds.BBSKeyCategories;

public class BBSModClient implements ClientModInitializer
{
    private static TextureManager textures;
    private static FramebufferManager framebuffers;
    private static SoundManager sounds;
    private static L10n l10n;

    private static ModelManager models;
    private static FormCategories formCategories;
    private static ScreenshotRecorder screenshotRecorder;
    private static VideoRecorder videoRecorder;
    private static final MinecraftSoundCapture minecraftSoundCapture = new MinecraftSoundCapture();
    private static EntitySelectors selectors;

    private static ParticleManager particles;

    private static KeyMapping keyDashboard;
    private static KeyMapping keyItemEditor;
    private static KeyMapping keyPlayFilm;
    private static KeyMapping keyPauseFilm;
    private static KeyMapping keyRecordReplay;
    private static KeyMapping keyRecordVideo;
    private static KeyMapping keyPlayFilmAndRecord;
    private static KeyMapping keyOpenReplays;
    private static KeyMapping keyOpenQuickReplays;
    private static KeyMapping keyOpenMorphing;
    private static KeyMapping keyDemorph;
    private static KeyMapping keyTeleport;
    private static KeyMapping keyZoom;

    private static UIDashboard dashboard;

    private static CameraController cameraController = new CameraController();
    private static ModelBlockItemRenderer modelBlockItemRenderer = new ModelBlockItemRenderer();
    private static GunItemRenderer gunItemRenderer = new GunItemRenderer();
    private static Films films;
    private static GunZoom gunZoom;

    private static final WorldVideoExportSession worldExportSession = new WorldVideoExportSession();

    private static float originalFramebufferScale;
    private static boolean customGUIScale;
    /** Das dashboard, ohne es zu erzeugen (null wenn noch keins offen ist).
     *
     *  MERGE 2026-07-27: aus dem CML-fork — unsere autonomie-schicht (mcstudio, blenderbridge)
     *  fragt darueber den UI-zustand ab, ohne ein dashboard zu oeffnen. */
    public static UIDashboard peekDashboard()
    {
        return dashboard;
    }


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

    /** Read the completed world/HUD image after rendering, outside the level's open pass. */
    public static void captureFinishedFrame()
    {
        if (Minecraft.getInstance().level != null && videoRecorder.isRecording() && BBSRendering.canRender)
        {
            BBSRendering.fuellen();
            minecraftSoundCapture.captureFrame();
            videoRecorder.recordFrame();
        }
    }

    public static MinecraftSoundCapture getMinecraftSoundCapture()
    {
        return minecraftSoundCapture;
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

    public static GunZoom getGunZoom()
    {
        return gunZoom;
    }

    public static KeyMapping getKeyZoom()
    {
        return keyZoom;
    }

    public static KeyMapping getKeyRecordVideo()
    {
        return keyRecordVideo;
    }

    public static boolean isVideoExportDelayPending()
    {
        return worldExportSession.isWarmingUp();
    }

    public static long getVideoExportDelayRemainingMs()
    {
        return worldExportSession.getWarmupRemainingMs();
    }

    /** Returns the dashboard without creating it. Used to avoid creating UI when handling keys (e.g. F6) before user has opened BBS. */
    public static UIDashboard getDashboardIfCreated()
    {
        return dashboard;
    }

    /** Der Waehler braucht die Taste, um beim zweiten Druck zu bestaetigen statt zu oeffnen. */
    public static KeyMapping getKeyOpenQuickReplays()
    {
        return keyOpenQuickReplays;
    }

    public static UIDashboard getDashboard()
    {
        if (dashboard == null)
        {
            dashboard = new UIDashboard();
        }

        return dashboard;
    }

    /**
     * Whether BBS's UI is on screen right now, i.e. whether the ui_scale setting
     * should drive the window's scale factor (see WindowMixin).
     */
    public static void setCustomGUIScale(boolean enabled)
    {
        customGUIScale = enabled;
    }

    /**
     * GUI scale that should be forced upon the window, or 0 to leave
     * Minecraft's own scale in charge (BBS UI closed, or ui_scale is 0).
     */
    public static float getCustomGUIScale()
    {
        return customGUIScale ? BBSSettings.userIntefaceScale.get() : 0F;
    }

    /**
     * Framebuffer pixels per BBS UI unit right now, fractional values included (see UISkala);
     * the "0 = Minecraft's scale" mode gives Minecraft's own factor.
     */
    public static float getGUIScale()
    {
        return mchorse.bbs_mod.ui.framework.UISkala.effektiv();
    }

    public static float getOriginalFramebufferScale()
    {
        return Math.max(originalFramebufferScale, 1);
    }

    public static ModelProperties getItemStackProperties(ItemStack stack)
    {
        ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);

        if (item != null)
        {
            return item.entity.getProperties();
        }

        GunItemRenderer.Item gunItem = gunItemRenderer.get(stack);

        if (gunItem != null)
        {
            return gunItem.properties;
        }

        return null;
    }

    public static void onEndKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        if (action != InputConstants.PRESS)
        {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || Minecraft.getInstance().gui.screen() != null)
        {
            return;
        }

        Morph morph = Morph.getMorph(player);

        /* Animation state trigger */
        if (morph != null && morph.getForm() != null && morph.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_MORPH);
            form.playState(state);
        }))
            return;

        /* Animation state trigger for items*/
        ModelProperties main = getItemStackProperties(player.getItemInHand(InteractionHand.MAIN_HAND));
        ModelProperties offhand = getItemStackProperties(player.getItemInHand(InteractionHand.OFF_HAND));

        if (main != null && main.getForm() != null && main.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_MAIN_HAND_ITEM);
            form.playState(state);
        }))
            return;

        if (offhand != null && offhand.getForm() != null && offhand.getForm().findState(key, (form, state) ->
        {
            ClientNetwork.sendFormTrigger(state.id.get(), ServerNetwork.STATE_TRIGGER_OFF_HAND_ITEM);
            form.playState(state);
        }))
            return;

        /* Change form based on the hotkey */
        for (Form form : BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).getForms())
        {
            if (form.hotkey.get() == key)
            {
                ClientNetwork.sendPlayerForm(form);

                return;
            }
        }

        for (UserFormCategory category : BBSModClient.getFormCategories().getUserForms().categories)
        {
            for (Form form : category.getForms())
            {
                if (form.hotkey.get() == key)
                {
                    ClientNetwork.sendPlayerForm(form);

                    return;
                }
            }
        }
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

        BBSMod.events.post(new RegisterL10nEvent(l10n));

        File parentFile = BBSMod.getSettingsFolder().getParentFile();

        particles = new ParticleManager(() -> new File(BBSMod.getAssetsFolder(), "particles"));

        models = new ModelManager(provider);
        formCategories = new FormCategories();
        screenshotRecorder = new ScreenshotRecorder(new File(parentFile, "screenshots"));
        videoRecorder = new VideoRecorder();
        selectors = new EntitySelectors();
        selectors.read();
        films = new Films();

        BBSResources.init();

        URLRepository repository = new URLRepository(new File(parentFile, "url_cache"));

        provider.register(new URLSourcePack("http", repository));
        provider.register(new URLSourcePack("https", repository));

        KeybindSettings.registerClasses();

        BBSMod.setupConfig(Icons.KEY_CAP, "keybinds", new File(BBSMod.getSettingsFolder(), "keybinds.json"), KeybindSettings::register);

        BBSMod.events.post(new RegisterClientSettingsEvent());

        BBSSettings.language.postCallback((v, f) -> reloadLanguage(getLanguageKey()));
        BBSSettings.userIntefaceScale.postCallback((v, f) ->
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.gui.screen() instanceof UIScreen)
            {
                mc.resizeDisplay();
            }
        });
        BBSSettings.editorSeconds.postCallback((v, f) ->
        {
            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel panel)
            {
                panel.fillData();
            }
        });

        BBSSettings.theme.modes(
            UIKeys.ENGINE_THEME_LIGHT,
            UIKeys.ENGINE_THEME_DARK
        );

        BBSSettings.keystrokeMode.modes(
            UIKeys.ENGINE_KEYSTROKES_POSITION_AUTO,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_LEFT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_BOTTOM_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_RIGHT,
            UIKeys.ENGINE_KEYSTROKES_POSITION_TOP_LEFT
        );

        BBSSettings.rotate3dSphereMode.modes(
            UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_TRACKBALL,
            UIKeys.ENGINE_ROTATE_3D_SPHERE_MODE_ARCBALL
        );

        BBSSettings.translateHotkeyOrder
            .labels(
                UIKeys.TRANSFORMS_TARGET_SCREEN,
                IKey.constant("X"),
                IKey.constant("Y"),
                IKey.constant("Z")
            )
            .colors(0, Colors.A100 | Colors.RED, Colors.A100 | Colors.GREEN, Colors.A100 | Colors.BLUE);

        BBSSettings.scaleHotkeyOrder
            .labels(
                UIKeys.TRANSFORMS_TARGET_ALL,
                IKey.constant("X"),
                IKey.constant("Y"),
                IKey.constant("Z")
            )
            .colors(0, Colors.A100 | Colors.RED, Colors.A100 | Colors.GREEN, Colors.A100 | Colors.BLUE);

        BBSSettings.rotateHotkeyOrder
            .labels(
                UIKeys.TRANSFORMS_TARGET_VIEW,
                UIKeys.TRANSFORMS_TARGET_SPHERE,
                IKey.constant("X"),
                IKey.constant("Y"),
                IKey.constant("Z")
            )
            .colors(0, 0, Colors.A100 | Colors.RED, Colors.A100 | Colors.GREEN, Colors.A100 | Colors.BLUE);

        UIKeys.C_KEYBIND_CATGORIES.load(KeyCombo.getCategoryKeys());
        UIKeys.C_KEYBIND_CATGORIES_TOOLTIP.load(KeyCombo.getCategoryKeys());

        /* Replace audio clip with client version that plays audio */
        BBSMod.getFactoryCameraClips()
            .register(Link.bbs("audio"), AudioClientClip.class, new ClipFactoryData(Icons.SOUND, 0xffc825))
            .register(Link.bbs("tracker"), TrackerClientClip.class, new ClipFactoryData(Icons.USER, 0x4cedfc))
            .register(Link.bbs("curve"), CurveClientClip.class, new ClipFactoryData(Icons.ARC, 0xff1493));

        /* Keybinds */
        keyDashboard = this.createKey("dashboard", InputConstants.KEY_0);
        keyItemEditor = this.createKey("item_editor", InputConstants.KEY_HOME);
        keyPlayFilm = this.createKey("play_film", InputConstants.KEY_RCONTROL);
        keyPauseFilm = this.createKey("pause_film", InputConstants.KEY_BACKSLASH);
        keyRecordReplay = this.createKey("record_replay", InputConstants.KEY_RALT);
        keyRecordVideo = this.createKey("record_video", InputConstants.KEY_F4);
        keyPlayFilmAndRecord = this.createKey("play_film_and_record", InputConstants.KEY_F6);
        keyOpenReplays = this.createKey("open_replays", InputConstants.KEY_RSHIFT);
        /* Bestehendes Replay in der WELT waehlen, ohne ins Dashboard gezogen zu werden.
         * Der Sprach-Schluessel key.bbs.open_quick_replays lag die ganze Zeit im Baum — die
         * Taste war beim Fork-Wechsel verlorengegangen, die Uebersetzung nicht. Siehe
         * UIQuickReplayOverlayPanel.
         *
         * ⛔ GRAVE (^) UND NICHT upstreams RIGHT_BRACKET: dieselbe Taste wie im Editor
         * (BBSToolsKeys.QUICK_REPLAY_PICKER). Der Nutzer am 2026-07-30: "ich will das der
         * button fuer die welt und im dashboard gleich sind" — zwei Tasten fuer dieselbe Box
         * sind zwei Dinge zum Merken. */
        keyOpenQuickReplays = this.createKey("open_quick_replays", InputConstants.KEY_GRAVE);
        keyOpenMorphing = this.createKey("open_morphing", InputConstants.KEY_B);
        keyDemorph = this.createKey("demorph", InputConstants.KEY_PERIOD);
        keyTeleport = this.createKey("teleport", InputConstants.KEY_Y);
        keyZoom = this.createKeyMouse("zoom", 2);

        /* Der Sammler des laufenden Weltdurchgangs wird angemeldet, solange BBS zeichnet,
         * und danach wieder abgemeldet. Die Form-Renderer holen ihn dort ab (FormSammler);
         * ihn durch ein Dutzend Signaturen zu reichen waere sauberer und ist der naechste
         * Schritt, nicht dieser.
         *
         * ⚠️ Das Abmelden steht im finally: bleibt ein alter Sammler angemeldet, reichen
         * die Formen des naechsten Bildes in einen Sammler ein, den niemand mehr
         * abarbeitet -- sie verschwinden lautlos. */
        /* ⛔ SAMMEL-DURCHGANG: Schatten und Namensschild MÜSSEN hier eingereicht werden,
         * nicht unten in AFTER_SOLID_FEATURES (2026-08-02 am Bytecode gelesen und behoben).
         *
         * Reihenfolge in 26.2:
         *   1. submitFeatures(...)                      <- COLLECT_SUBMITS feuert HIER
         *   2. prepareFrame -> drainPhases -> sortInto(), und sortInto endet mit clear()
         *   3. Hauptpass executeSolid()                 <- AFTER_SOLID_FEATURES feuert HIER
         *
         * Schatten (submitShadow) und Namensschild (submitText) werden nicht gezeichnet,
         * sondern EINGEREICHT. Aus Schritt 3 eingereicht landeten sie in einer frischen
         * Sammlung und wurden erst im NÄCHSTEN Bild gezeichnet — mit einer Matrix, die
         * relativ zur Kamera des VORIGEN Bildes gebaut war. Versatz = camPos(N) − camPos(N+1):
         * im Stillstand exakt null, beim Schwenk gleitend, beim Anhalten zurückschnappend.
         * Das Modell war nie betroffen, es wird sofort gezeichnet (ModelVAORenderer) — daher
         * stand die Figur still, während ihr Schatten hinterherzog.
         *
         * ⚠️ NUR der Film-Durchgang, und NUR sein aufschiebbarer Teil (FilmControllerContext
         * #sammelDurchgang). Den ganzen renderCoolStuff hierher zu ziehen wäre falsch: der
         * Hauptpass ruft danach clearColorAndDepthTextures, die sofort gezeichneten Modelle
         * wären weg. */
        WorldRenderEvents.AFTER_ENTITIES.register((context) ->
        {
            mchorse.bbs_mod.forms.FormSammler.anmelden(new mchorse.bbs_mod.compat.gpu.SofortSammler(context.consumers()));
            mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang = true;

            try
            {
                /* ⛔ BEIDE WEGE. Im EDITOR läuft der Film NICHT über Films.getControllers()
                 * — die Liste ist dort leer (gemessen 2026-08-02) — sondern über
                 * UIScreen.renderInWorld. Wer nur films.render() sammelt, nimmt dem Editor
                 * den Schatten ganz weg, statt ihn zu reparieren: aus dem Zeichen-Durchgang
                 * entfernt, im Sammel-Durchgang nie erreicht. Genau so gemessen, bevor diese
                 * Zeile dazukam. renderCoolStuff ruft beide, in dieser Reihenfolge. */
                if (Minecraft.getInstance().gui.screen() instanceof UIScreen screen)
                {
                    screen.renderInWorld(context);
                }

                BBSModClient.getFilms().render(context);
            }
            finally
            {
                /* Beides im finally: ein hängengebliebenes Flag würde im Zeichen-Durchgang
                 * das Modell unterdrücken — die Figuren wären unsichtbar. */
                mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang = false;
                mchorse.bbs_mod.forms.FormSammler.abmelden();
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register((context) ->
        {
            mchorse.bbs_mod.forms.FormSammler.anmelden(new mchorse.bbs_mod.compat.gpu.SofortSammler(context.consumers()));

            try
            {
            BBSRendering.renderCoolStuff(context);

            if (BBSSettings.chromaSkyEnabled.get())
            {
                float d = BBSSettings.chromaSkyBillboard.get();

                if (d > 0)
                {
                    PoseStack stack = context.matrixStack();
                    Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
                    Color color = Colors.COLOR.set(fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get());

                    stack.pushPose();

                    PoseStack.Pose peek = stack.last();

                    peek.pose().identity();
                    peek.normal().identity();
                    stack.translate(0F, 0F, -d);

                    /* 26.2: Tiefe, Mischung und Cull sind kein globaler Zustand mehr, sondern
                     * stecken in der Pipeline dieses einen Zuges. Das enableDepthTest von
                     * vorher ist damit erledigt — es ist jetzt tiefePruefen = true; das
                     * disableDepthTest dahinter entfaellt ersatzlos, weil jeder folgende
                     * Zeichenaufruf seinen Tiefenzustand selbst mitbringt. */
                    try (ByteBufferBuilder speicher = new ByteBufferBuilder(1024))
                    {
                        BufferBuilder builder = SofortZeichner.beginnen(speicher, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

                        float fov = Minecraft.getInstance().options.fov().get();
                        float dd = d * (float) Math.pow(fov / 40F, 2F);

                        Draw.fillQuad(builder, stack,
                            -dd, -dd, 0,
                            dd, -dd, 0,
                            dd, dd, 0,
                            -dd, dd, 0,
                            color.r, color.g, color.b, 1F
                        );

                        /* Kein Culling: die Flaeche haengt als Billboard direkt vor der
                         * Kamera, Verwerfen spart hier nichts — koennte sie bei umgekehrter
                         * Wicklung aber ganz verschlucken. Der alte Block schaltete Cull nie
                         * ein. Kein Mischen: der Himmel ist undurchsichtig (Alpha 1).
                         *
                         * Die Ecken tragen die Pose bereits (Draw.fillQuad multipliziert
                         * stack.last().pose() hinein), darum geht als Modellansicht NUR der
                         * globale Wert mit — genau der, den BufferUploader.drawWithShader
                         * frueher selbst aus RenderSystem nahm. Die Pose hier noch einmal zu
                         * uebergeben wuerde die Flaeche doppelt verschieben. */
                        SofortZeichner.zeichnen(
                            FormPipelines.holen(VertexFormat.Mode.TRIANGLES, false, true, false),
                            builder.build(),
                            new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()),
                            null,
                            null);
                    }

                    stack.popPose();
                }
            }
            }
            finally
            {
                mchorse.bbs_mod.forms.FormSammler.abmelden();
            }
        });

        /* ⚠️ DER AUFBAU DER FORMKATEGORIEN HAENGT AM WELT-BEITRITT, nicht am Client-Start.
         * Er legt Vorlage-Formen an, darunter einen ItemStack -- und dessen Komponenten sind
         * in 26.2 datengetrieben, kommen also erst mit dem Datenpaket der Welt. Zweimal zu
         * frueh versucht (Initialisierer, CLIENT_STARTED), beide Male "Components not bound
         * yet". Begruendung ausfuehrlich in BBSResources.formulareAufbauen. */
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BBSResources.formulareAufbauen());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            dashboard = null;
            worldExportSession.stop();

            /* A panel export dies with its dashboard without finishing - the sound
             * capture must not keep accumulating into the next session */
            minecraftSoundCapture.end();

            films = new Films();

            ClientNetwork.resetHandshake();
            films.reset();
            cameraController.reset();
        });

        ClientTickEvents.START_CLIENT_TICK.register((client) ->
        {
            BBSRendering.startTick();
        });

        /* 26.2 (fabric-lifecycle-events 4.1.3): START_WORLD_TICK/END_WORLD_TICK heissen jetzt
         * START_LEVEL_TICK/END_LEVEL_TICK -- Fabric ist der Umbenennung von "world" zu "level"
         * gefolgt. Der Rueckruf bekam schon vorher die Welt, nicht den Client; der Parameter
         * heisst hier ab jetzt so, wie er ist. */
        ClientTickEvents.END_WORLD_TICK.register((welt) ->
        {
            Minecraft mc = Minecraft.getInstance();

            if (!mc.isPaused())
            {
                films.updateEndWorld();
            }

            BBSResources.tick();
        });

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.gui.screen() instanceof UIScreen screen)
            {
                screen.update();
            }

            cameraController.update();

            if (!mc.isPaused())
            {
                films.update();
                modelBlockItemRenderer.update();
                gunItemRenderer.update();
                textures.update();
            }

            worldExportSession.update();

            while (keyDashboard.consumeClick()) UIScreen.open(getDashboard());
            while (keyItemEditor.consumeClick()) this.keyOpenModelBlockEditor(mc);
            while (keyPlayFilm.consumeClick()) this.keyPlayFilm();
            while (keyPauseFilm.consumeClick()) this.keyPauseFilm();
            while (keyRecordReplay.consumeClick()) this.keyRecordReplay();
            while (keyRecordVideo.consumeClick()) this.keyRecordVideo(mc);
            while (keyPlayFilmAndRecord.consumeClick()) this.keyPlayFilmAndRecord();
            while (keyOpenReplays.consumeClick()) this.keyOpenReplays();
            /* Nicht neu oeffnen, solange der Waehler steht: derselbe Tastendruck BESTAETIGT
             * dort die Auswahl (UIQuickReplayOverlayPanel.subKeyPressed). Ohne den Test
             * bekaeme man beides — bestaetigen und sofort wieder aufmachen.
             *
             * ⛔ DER NULL-TEST IST NICHT UEBERFLUESSIG. Bei einem HOTSWAP laufen statische
             * Initialisierer NICHT, ein NEU hinzugekommenes Feld steht also auf null — und
             * dieser Zugriff liegt in der Tick-Schleife, laeuft somit in JEDEM Frame. Am
             * 2026-07-30 hat genau diese Zeile ohne den Test eine Instanz mit 5,5 h Laufzeit
             * umgebracht, Sekunden nach dem Tausch. Die anderen Tasten oben brauchen ihn
             * nicht: sie existierten beim Start und behalten ihren Wert ueber den Tausch. */
            while (keyOpenQuickReplays != null && keyOpenQuickReplays.consumeClick())
            {
                if (!UIQuickReplayOverlayPanel.isOpened()) QuickReplayWelt.oeffnen();
            }
            while (keyOpenMorphing.consumeClick())
            {
                UIDashboard dashboard = getDashboard();

                UIScreen.open(dashboard);
                dashboard.setPanel(dashboard.getPanel(UIMorphingPanel.class));
            }
            while (keyDemorph.consumeClick()) ClientNetwork.sendPlayerForm(null);
            while (keyTeleport.consumeClick()) this.keyTeleport();

            if (mc.player != null)
            {
                boolean zoom = keyZoom.isDown();
                ItemStack stack = mc.player.getMainHandItem();

                if (gunZoom == null && zoom && stack.getItem() == BBSMod.GUN_ITEM)
                {
                    GunProperties properties = GunProperties.get(stack);

                    ClientNetwork.sendZoom(true);
                    gunZoom = new GunZoom(properties.fovTarget, properties.fovInterp, properties.fovDuration);
                }
            }
        });

        /* 26.2: HudRenderCallback ist in fabric-rendering-v1 25.3.2 ersatzlos gestrichen (in
         * 16.2.10 noch im JAR, in 25.3.2 nicht mehr). Nachfolger ist HudElementRegistry.
         *
         * addLast haengt uns an eine EIGENE Wurzelebene NACH allen Vanilla-Elementen
         * (HudElementRegistryImpl haelt FIRST und LAST getrennt von der Vanilla-Liste) --
         * das ist das exakte Gegenstueck zum alten Rueckruf, der am TAIL des HUD-Zeichnens
         * feuerte. Wer eine andere Tiefe will, haengt sich mit attachElementBefore/After an
         * ein benanntes VanillaHudElements-Element; die Reihenfolge ist jetzt explizit.
         *
         * ⚠️ extractRenderState heisst so, weil es nur SAMMELT. Zeichnen laeuft ueber den
         * GuiGraphics (der die volle Zeichen-API traegt) -- wer daneben einen
         * eigenen RenderPass aufmacht, landet UNTER dem gesamten GUI. */
        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
        {
            BBSRendering.renderHud(drawContext, tickDelta.getGameTimeDeltaPartialTick(false));

            if (gunZoom != null)
            {
                gunZoom.update(keyZoom.isDown(), Minecraft.getInstance().getTimer().getRealtimeDeltaTicks());

                if (gunZoom.canBeRemoved())
                {
                    ClientNetwork.sendZoom(false);
                    gunZoom = null;
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((e) -> BBSResources.stopWatchdog());
        ClientLifecycleEvents.CLIENT_STARTED.register((e) ->
        {
            BBSRendering.setupFramebuffer();
            provider.register(new MinecraftSourcePack());

            Window window = Minecraft.getInstance().getWindow();

            /* ⛔ DER NENNER IST DIE FENSTERBREITE, NICHT DIE GUI-BREITE. Dieser Wert meint das
             * Verhaeltnis Rahmenpuffer zu Fenster -- also die Anzeigeskalierung eines HiDPI-
             * Bildschirms. Auf einem gewoehnlichen Bildschirm ist er 1.
             *
             * 1.21.1 rechnete `getFramebufferWidth() / getWidth()`. Die Namen sind in 26.2
             * verschoben, und dabei ist der Nenner verrutscht (gemessen an einer laufenden
             * Instanz, beide Seiten):
             *
             *     yarn getFramebufferWidth 1280  ->  mojmap getWidth          1280
             *     yarn getWidth            1280  ->  mojmap getScreenWidth    1280
             *     yarn getScaledWidth       640  ->  mojmap getGuiScaledWidth  640
             *
             * Mit getGuiScaledWidth kam 1280/640 = 2 heraus statt 1 -- der Wert war die
             * GUI-Skalierung. WindowMixin.getWidth() gibt daraufhin `Videobreite * 2` heraus,
             * und BBSRendering.setupFramebuffer legte den Filmpuffer mit 2560x1440 an, waehrend
             * die Welt 1280x720 hineinzeichnet und das Vorschaufeld die Textur mit 1280x720
             * abtastet. Sichtbar war das als Vorschaufeld, das nur zur Haelfte gefuellt ist:
             * oben Himmel, unten der durchscheinende Panel-Hintergrund (#171A1F =
             * BBSSettings.DARK_BASE_SURFACE). 1.21.1 fuellt dieselbe Flaeche vollstaendig.
             *
             * Ganzzahlige Division wie in 1.21.1 -- gleiche Rechnung, gleiches Ergebnis. */
            originalFramebufferScale = window.getWidth() / window.getScreenWidth();
        });

        URLTextureErrorCallback.EVENT.register((url, error) ->
        {
            UIBaseMenu menu = UIScreen.getCurrentMenu();

            if (menu != null)
            {
                url = url.substring(0, MathUtils.clamp(url.length(), 0, 40));

                if (error == URLError.FFMPEG)
                {
                    menu.context.notifyError(UIKeys.TEXTURE_URL_ERROR_FFMPEG.format(url));
                }
                else if (error == URLError.HTTP_ERROR)
                {
                    menu.context.notifyError(UIKeys.TEXTURE_URL_ERROR_HTTP.format(url));
                }
            }
        });

        BBSRendering.setup();

        /* Network */
        ClientNetwork.setup();

        /* Entity renderers */
        EntityRendererRegistry.register(BBSMod.ACTOR_ENTITY, ActorEntityRenderer::new);
        EntityRendererRegistry.register(BBSMod.GUN_PROJECTILE_ENTITY, GunProjectileEntityRenderer::new);

        BlockEntityRendererRegistryImpl.register(BBSMod.MODEL_BLOCK_ENTITY, ModelBlockEntityRenderer::new);

        /* Bild-im-Bild-Zeichner fuer die Modellvorschauen der Oberflaeche.
         *
         * ⚠️ OHNE DIESE REGISTRIERUNG PASSIERT NICHTS UND ES GIBT KEINEN FEHLER: ein
         * eingereihter Zustand, fuer den kein Zeichner registriert ist, wird still
         * verworfen. Wer den Zeichner baut und das hier vergisst, sucht den Fehler in
         * seinem Zeichencode.
         *
         * Das Gitter war der Vertikalschnitt zum Beweis des Weges (Aufgabe 96), das Modell
         * ist der eigentliche Fall. Beide brauchen je einen eigenen Eintrag -- die
         * Zuordnung laeuft ueber getRenderStateClass(). */
        mchorse.bbs_mod.compat.gpu.BildImBildRegister.register(
            (kontext) -> new mchorse.bbs_mod.ui.framework.elements.utils.vorschau.VorschauGitterRenderer());
        mchorse.bbs_mod.compat.gpu.BildImBildRegister.register(
            (kontext) -> new mchorse.bbs_mod.ui.framework.elements.utils.vorschau.VorschauModellRenderer());
        /* Die Miniaturen der Formpalette -- derselbe Weg, ein dritter Eintrag. Ohne ihn wird
         * der eingereihte Zustand still verworfen (kein Zeichner fuer die Zustandsklasse).
         * ALLE Vorschauen eines Bildes teilen sich eine bildschirmgrosse Textur; warum, steht
         * in MiniaturSammelZustand. */
        mchorse.bbs_mod.compat.gpu.BildImBildRegister.register(
            (kontext) -> new mchorse.bbs_mod.ui.framework.elements.utils.vorschau.MiniaturSammelRenderer());
        /* Das Gizmo -- vierter Eintrag, selber Grund: ohne ihn wird der eingereihte Zustand
         * still verworfen. Warum es den Weg braucht, steht in GizmoZustand. */
        mchorse.bbs_mod.compat.gpu.BildImBildRegister.register(
            (kontext) -> new mchorse.bbs_mod.ui.framework.elements.utils.vorschau.GizmoRenderer());

        /* 26.2: BuiltinItemRendererRegistry ist in fabric-rendering-v1 25.3.2 ERSATZLOS
         * gestrichen; einen Nachfolger hat Fabric nicht gebaut, weil Mojang selbst einen hat.
         * Ein eigener Zeichner haengt jetzt nicht mehr AM GEGENSTAND, sondern am MODELL: die
         * beiden Aufrufe tragen die Kennungen bbs:model_block und bbs:gun in Vanillas
         * (private) Sonderzeichner-Tabelle ein, und die Modell-Beschreibungen
         * assets/bbs/items/model.json und gun.json zeigen darauf.
         * Details: ModelBlockSpecialRenderer, SpecialModelRenderersAccessor. */
        ModelBlockItemRenderer.registriereSonderzeichner();
        GunItemRenderer.registriereSonderzeichner();

        /* Create folders */
        BBSMod.getAudioFolder().mkdirs();
        BBSMod.getAssetsPath("textures").mkdirs();

        for (String path : List.of("alex", "alex_simple", "steve", "steve_simple"))
        {
            BBSMod.getAssetsPath("models/emoticons/" + path + "/").mkdirs();
        }

        for (String path : List.of("alex", "alex_bends", "eyes", "eyes_1px", "steve", "steve_bends"))
        {
            BBSMod.getAssetsPath("models/player/" + path + "/").mkdirs();
        }
    }

    private void keyRecordVideo(Minecraft mc)
    {
        if (worldExportSession.isExporting())
        {
            worldExportSession.cancel();

            return;
        }

        worldExportSession.start(null, null);
    }

    private KeyMapping createKey(String id, int key)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key." + BBSMod.MOD_ID + "." + id,
            InputConstants.Type.KEYSYM,
            key,
            BBSKeyCategories.BBS_MAIN
        ));
    }

    private KeyMapping createKeyMouse(String id, int button)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key." + BBSMod.MOD_ID + "." + id,
            InputConstants.Type.MOUSE,
            button,
            BBSKeyCategories.BBS_MAIN
        ));
    }

    private void keyOpenModelBlockEditor(Minecraft mc)
    {
        ItemStack stack = mc.player.getItemBySlot(EquipmentSlot.MAINHAND);
        ModelBlockItemRenderer.Item item = modelBlockItemRenderer.get(stack);
        GunItemRenderer.Item gunItem = gunItemRenderer.get(stack);

        if (item != null)
        {
            UIScreen.open(new UIModelBlockEditorMenu(item.entity.getProperties()));
        }
        else if (gunItem != null)
        {
            UIScreen.open(new UIModelBlockEditorMenu(gunItem.properties));
        }
    }

    private void keyPlayFilm()
    {
        if (getDashboardIfCreated() == null)
        {
            return;
        }

        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);
        if (panel.getData() != null)
        {
            Films.playFilm(panel.getData().getId(), false);
        }
    }

    /**
     * Start video recording and film playback together (F6). Recording stops
     * automatically when the film finishes.
     */
    private void keyPlayFilmAndRecord()
    {
        if (getDashboardIfCreated() == null || com.afrobama.bbstools.client.film.RecordFromKeyframesHandler.consumeWorldKey(keyPlayFilmAndRecord))
        {
            return;
        }

        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);

        if (panel.getData() == null)
        {
            return;
        }

        String filmId = panel.getData().getId();

        if (worldExportSession.isExporting())
        {
            /* Toggle off only this film's combo; ignore the key while an unrelated recording runs. */
            if (filmId.equals(worldExportSession.getFilmId()))
            {
                worldExportSession.cancel();
            }

            return;
        }

        worldExportSession.start(filmId, panel.getData());
    }

    private void keyPauseFilm()
    {
        if (getDashboardIfCreated() == null)
        {
            return;
        }

        UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);
        if (panel.getData() != null)
        {
            Films.pauseFilm(panel.getData().getId());
        }
    }

    private void keyRecordReplay()
    {
        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getData() != null)
        {
            Recorder recorder = getFilms().getRecorder();

            if (recorder != null)
            {
                recorder = BBSModClient.getFilms().stopRecording();

                if (recorder == null || recorder.hasNotStarted() || panel.getData() == null)
                {
                    return;
                }

                panel.applyRecordedKeyframes(recorder, panel.getData());
            }
            else
            {
                Replay replay = panel.replayEditor.getReplay();
                int index = panel.getData().replays.getList().indexOf(replay);

                if (index >= 0)
                {
                    getFilms().startRecording(panel.getData(), index, 0);
                }
            }
        }
    }

    private void keyOpenReplays()
    {
        UIDashboard dashboard = getDashboard();

        UIScreen.open(dashboard);

        /* ⛔ Nie mehr den alten Replay-Zweig aufschalten (Nutzer 2026-08-24: "die neue
         * timeline soll das alte komplett ersetzen") — die Replays wohnen im Outliner
         * des Film-Editors. */
        dashboard.setPanel(dashboard.getPanel(UIFilmPanel.class));
    }

    private void keyTeleport()
    {
        UIDashboard dashboard = getDashboard();
        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel != null)
        {
            panel.replayEditor.teleport();
        }
    }

    public static String getLanguageKey()
    {
        return getLanguageKey(BBSSettings.language.get());
    }

    public static String getLanguageKey(String key)
    {
        if (key.isEmpty())
        {
            key = Minecraft.getInstance().options.languageCode;
        }

        return key;
    }

    public static void reloadLanguage(String language)
    {
        l10n.reload(language, BBSMod.getProvider());
    }
}
