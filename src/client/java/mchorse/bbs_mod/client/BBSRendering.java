package mchorse.bbs_mod.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class BBSRendering
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static boolean canRender;

    public static boolean renderingWorld;
    public static int lastAction;

    public static final Matrix4f camera = new Matrix4f();

    /**
     * Active world projection from CameraRenderState, copied at LevelRenderer.render HEAD.
     * The render method's Matrix4fc parameter contains view rotation, not projection.
     * Lazily initialized so existing instances remain usable after a body hotswap.
     */
    private static Matrix4f zeichenProjektion;

    /** Active projection from the latest world pass, or null before the first stamp. */
    public static Matrix4f zeichenProjektion()
    {
        return zeichenProjektion;
    }

    /** Copy the native world projection, initializing its storage on the first pass. */
    public static void zeichenProjektionStempeln(org.joml.Matrix4fc projektion)
    {
        if (projektion == null)
        {
            return;
        }

        Matrix4f m = zeichenProjektion;

        if (m == null)
        {
            m = new Matrix4f();
            zeichenProjektion = m;
        }

        m.set(projektion);
    }

    private static boolean customSize;
    private static boolean iris;
    private static boolean sodium;
    private static boolean optifine;

    private static int width;
    private static int height;

    /* Orbit distance for the orthographic projection; negative = perspective.
     * Re-armed every frame by the film editor's orbit camera (which is set up
     * from Camera#update, between renderWorld's HEAD and its projection use),
     * so it can never go stale when another controller takes over. */
    private static float orthoDistance = -1F;

    private static boolean toggleFramebuffer;
    private static RenderTarget framebuffer;
    /* 26.2: clientFramebuffer ist entfallen. Es hielt das vorherige Renderziel, um es nach
     * dem Film-Rendern zurueckzusetzen -- ein Muster, das die neue Umlenkung nicht mehr
     * kennt (siehe reassignFramebuffer: Normalzustand ist null, nicht "das alte Ziel"). */
    private static Texture texture;
    private static mchorse.bbs_mod.graphics.Framebuffer /* NECESSARY */ exportFramebuffer;

    private static Runnable pendingExportResolutionAction;

    public static int getMotionBlur()
    {
        return getMotionBlur(BBSSettings.videoFrameRate.get(), getMotionBlurFactor());
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
        return getMotionBlurFactor(BBSSettings.videoMotionBlur.get());
    }

    public static int getMotionBlurFactor(int integer)
    {
        return integer == 0 ? 0 : (int) Math.pow(2, 6 + integer);
    }

    public static int getVideoWidth()
    {
        /* P7a: Settings-Pfad supersampled (Recorder skaliert zurueck); setCustomSize bleibt exakt. */
        return width == 0 ? BBSSettings.videoWidth.get() * BBSSettings.videoSupersampling.get() : width;
    }

    public static int getVideoHeight()
    {
        return height == 0 ? BBSSettings.videoHeight.get() * BBSSettings.videoSupersampling.get() : height;
    }

    public static int getVideoFrameRate()
    {
        int frameRate = BBSSettings.videoFrameRate.get();

        return frameRate * (1 << getMotionBlur(frameRate, getMotionBlurFactor()));
    }

    public static File getVideoFolder()
    {
        File movies = new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        File exportPath = new File(BBSSettings.videoExportPath.get());

        /* ⛔ Leere Vorgabe abfangen: File("").isDirectory() ist auf Java 25 TRUE (gemessen
         * 2026-08-17) — der Export lief dann "in directory \"\"" und ffmpeg starb an ENOENT. */
        if (!BBSSettings.videoExportPath.get().isEmpty() && exportPath.isDirectory())
        {
            movies = exportPath;
        }

        movies.mkdirs();

        return movies;
    }

    public static boolean canReplaceFramebuffer()
    {
        /* Welt UND HUD rendern in Export-Groesse (toggleFramebuffer haelt bis zum Blit) — sonst
         * zeichnet das HUD in Fenstergroesse und wird in der Datei gestreckt, wenn das Fenster
         * die Zielaufloesung nicht erreicht. Offener BBS-Editor ausgenommen (Panel-UI bleibt
         * in echter Fenstergroesse). */
        return customSize && (renderingWorld || (toggleFramebuffer && UIScreen.getCurrentMenu() == null));
    }

    public static boolean isCustomSize()
    {
        return customSize;
    }

    public static void setCustomSize(boolean customSize)
    {
        setCustomSize(customSize, 0, 0);
    }

    public static void setCustomSize(boolean customSize, int w, int h)
    {
        int newWidth = !customSize ? 0 : w;
        int newHeight = !customSize ? 0 : h;

        /* No-op when nothing actually changes. A redundant setCustomSize(false)
         * — e.g. a film panel disappearing while custom size is already off, which
         * happens when the dashboard is first lazily created by the teleport/record
         * keybinds — must NOT resize the vanilla framebuffers: that stalls the GPU
         * and freezes the screen for a frame even though the state didn't change. */
        if (BBSRendering.customSize == customSize && width == newWidth && height == newHeight)
        {
            return;
        }

        LOGGER.info("[BBS film] setCustomSize customSize={} w={} h={} (stored width/height will be {})",
            customSize, w, h, customSize ? w + "/" + h : "0/0");
        BBSRendering.customSize = customSize;

        width = newWidth;
        height = newHeight;

        if (!customSize)
        {
            resizeExtraFramebuffers();
        }
    }

    public static Texture getTexture()
    {
        if (texture == null)
        {
            texture = new Texture();
            /* ⚠️ 26.2: RGB8 GEHT NICHT MEHR. GlDevice.createTexture lehnt jedes dreikanalige
             * Format ab ("RGB8_UNORM format cannot be used to create textures") -- der neue
             * Zeichenweg legt Texturen ueber GpuDevice an, und der kennt nur ausgerichtete
             * Formate. Vier Kanaele statt drei; das Alpha bleibt ungenutzt, kostet aber ein
             * Byte je Bildpunkt. Kein Bildunterschied. */
            texture.setFormat(TextureFormat.RGBA_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        return texture;
    }

    public static void startTick()
    {
        capturedModelBlocks.clear();
    }

    public static void setup()
    {
        /* Iris ist am 2026-08-10 zurueckgeholt (1.11.2+26.2). SODIUM BLEIBT AUS: dessen
         * abhaengigkeit ist zwar im build, aber mchorse/bbs_mod/utils/sodium/** ist weiter
         * ausgeschlossen -- ein true hier faende SodiumUtils nicht. */
        iris = FabricLoader.getInstance().isModLoaded("iris");
        sodium = false;
        optifine = FabricLoader.getInstance().isModLoaded("optifabric");

        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getLevel().isClientSide())
            {
                capturedModelBlocks.add(entity);
            }
        });

        if (iris)
        {
            IrisUtils.setup();
        }
    }

    /* Framebuffers */

    public static RenderTarget getFramebuffer()
    {
        return framebuffer;
    }

    public static void setupFramebuffer()
    {
        Window window = Minecraft.getInstance().getWindow();

        /* ⛔ 26.2: KEIN MainTarget MEHR. MainTarget ist Minecrafts Klasse fuer DAS Hauptziel --
         * es haengt an der Fensterflaeche und ist dafuer gedacht, genau einmal zu existieren.
         * BBS braucht hier aber ein OFFSCREEN-Ziel, und dafuer gibt es TextureTarget.
         *
         * In 1.21.1 war der Unterschied folgenlos (beide waren Rahmenpuffer-Wrapper). In 26.2
         * legt der neue Zeichenweg Texturen ueber GpuDevice an und verwaltet die Darstellung
         * selbst -- ein zweites MainTarget ist damit kein harmloser Puffer mehr.
         *
         * RGBA8_UNORM, weil dreikanalige Formate abgelehnt werden (siehe getTexture). */
        framebuffer = new com.mojang.blaze3d.pipeline.TextureTarget(
            "bbs-film", window.getWidth(), window.getHeight(), true,
            com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
    }

    /* 26.2: RenderTarget.frameBufferId ist entfallen -- 26.2 gibt nur noch GpuTexture heraus,
     * keine rohen GL-Kennungen mehr, weil der Renderer auf Vulkan umziehen soll.
     *
     * Der Export-Blit unten BRAUCHT aber einen Lese-Rahmenpuffer: er verkleinert linear
     * (glBlitFramebuffer mit GL_LINEAR), und genau das kann weder copyTextureToTexture noch
     * glCopyTexSubImage2D. Ohne die Verkleinerung liefe das Bild in voller Fenstergroesse in
     * den Videorecorder -- falsche Aufloesung, und auf macOS ein Absturz.
     *
     * Also haengen wir die Farbtextur des Ziels selbst an einen EIGENEN Rahmenpuffer. Der
     * wird einmal angelegt und nur neu behaengt, wenn sich die Textur aendert.
     *
     * ⛔ DAS IST BEWUSST NOCH ROHES GL UND HAT EIN ABLAUFDATUM. Unter dem Vulkan-Backend gibt
     * es keine GL-Kennung mehr; dann muss der Export ueber den Befehlsgeber laufen und die
     * Verkleinerung woanders herkommen (eigener Durchgang mit Sampler). Siehe
     * docs/26.2-UMSTIEG-SPICKZETTEL.md. */
    private static int leseFbo = 0;

    /**
     * ⛔ DER ANHANG WIRD JEDES MAL NEU GESETZT — die GL-Nummer zu merken war ein Fehler, und
     * zwar ein stiller.
     *
     * <p>Hier stand ein Zwischenspeicher: "gleiche Texturnummer wie beim letzten Mal, also ist
     * der Anhang noch gut". Das gilt in 26.2 nicht mehr. Eine Groessenaenderung von
     * {@code RenderTarget} SCHLIESST die alte GPU-Textur und legt eine neue an; GL darf die
     * freigewordene Nummer sofort wiederverwenden. Dann stimmt die Nummer ueberein, obwohl es
     * ein anderes Objekt ist — der Zwischenspeicher meldet "unveraendert" und der Rahmenpuffer
     * haengt weiter am toten Anhang. Dieselbe Falle steht in {@code Texture}: wer die Nummer
     * zwischenspeichert, liest nach der naechsten Groessenaenderung ins Leere.
     *
     * <p>KOSTEN DES FEHLERS (Aufgabe 91): der Export-Blit las darueber und kopierte Schwarz,
     * obwohl BBS' Rahmenpuffer gefuellt war. Zwei Messwege auf DIESELBE Textur zeigten es --
     * direkt gelesen: Mittelwert 198, ueber diesen Rahmenpuffer gelesen: 0. Ohne die zweite
     * Messung haette man weiter im Weltdurchgang gesucht, wo nichts kaputt war.
     *
     * <p>Ein {@code glFramebufferTexture2D} je Bild kostet nichts Messbares — der
     * Zwischenspeicher sparte nichts und verbarg einen Fehler.
     */
    private static int leseFboFuer(RenderTarget ziel)
    {
        int textur = ((com.mojang.blaze3d.opengl.GlTexture) ziel.getColorTexture()).glId();

        if (leseFbo == 0)
        {
            leseFbo = GL30.glGenFramebuffers();
        }

        int vorher = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, leseFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, textur, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);

        return leseFbo;
    }

    public static void resizeExtraFramebuffers()
    {
        Set<RenderTarget> buffers = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();

        /* 26.2: die sechs Ziel-Getter haben nur das get-Praefix verloren (entityTarget heisst
         * jetzt entityOutlineTarget, weil es immer schon das Umriss-Ziel war). Kein Umbau --
         * aber ein Muster, das in 26.2 oft zuschlaegt: eine Methode, die "weg" aussieht, hat
         * haeufig nur das get verloren. Vor dem Aufgeben ohne Praefix nachsehen. */
        buffers.add(mc.levelRenderer.entityOutlineTarget());
        buffers.add(mc.levelRenderer.translucentTarget());
        buffers.add(mc.levelRenderer.itemEntityTarget());
        buffers.add(mc.levelRenderer.particlesTarget());
        buffers.add(mc.levelRenderer.weatherTarget());
        buffers.add(mc.levelRenderer.cloudsTarget());

        for (RenderTarget buffer : buffers)
        {
            resizeFramebuffer(buffer);
        }
    }

    public static void resizeFramebuffer(RenderTarget framebuffer)
    {
        if (framebuffer == null)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        if (framebuffer.width == w && framebuffer.height == h)
        {
            return;
        }

        framebuffer.resize(w, h);
    }

    public static void toggleFramebuffer(boolean toggleFramebuffer)
    {
        if (toggleFramebuffer == BBSRendering.toggleFramebuffer)
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        BBSRendering.toggleFramebuffer = toggleFramebuffer;

        if (toggleFramebuffer)
        {
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();

            resizeExtraFramebuffers();

            if (framebuffer.width != w || framebuffer.height != h)
            {
                framebuffer.resize(w, h);
            }

            /* 26.2: das Merken des vorherigen Ziels ist ersatzlos entfallen. Umgelenkt wird
             * jetzt ueber RenderSystem.outputColorTextureOverride, und dessen Normalzustand
             * ist null -- man stellt nicht das alte Ziel zurueck, man nimmt die Umlenkung
             * weg (siehe reassignFramebuffer). Das alte Ziel zu merken und spaeter wieder zu
             * setzen wuerde die Umlenkung stehen lassen. */
            reassignFramebuffer(framebuffer);

            /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
             * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
             * zeichnen will, gibt das Ziel dort an. */
        }
        else
        {
            int drawW = window.getWidth();
            int drawH = window.getHeight();
            reassignFramebuffer(null);

            /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
             * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
             * zeichnen will, gibt das Ziel dort an. */

            if (width != 0)
            {
                /* When the film panel is open, the UI draws the preview texture in its block; do not
                 * blit our framebuffer to the full window or the preview would stretch to full screen. */
                UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
                boolean filmPanelShowing = currentMenu instanceof UIDashboard dashboard
                    && dashboard.getPanels().panel instanceof UIFilmPanel;
                if (!filmPanelShowing)
                {
                    /* 26.2: blitToScreen ist entfallen. Der Nachfolger sitzt an der
                     * Fensterflaeche selbst: sie nimmt einen Befehlsgeber und die
                     * Farb-Textursicht des Puffers.
                     *
                     * ⚠️ Die Zielgroesse (drawW/drawH) faellt dabei weg -- der Blit fuellt die
                     * Flaeche. Fuer den Vollbild-Fall ist das dasselbe Ergebnis; wenn hier je
                     * eine ANDERE Groesse als das Fenster ankommt, waere es eine Streckung,
                     * die vorher nicht da war. */
                    Minecraft.getInstance().windowSurface().blitFromTexture(
                        RenderSystem.getDevice().createCommandEncoder(),
                        framebuffer.getColorTextureView());
                }
            }
        }
    }

    /**
     * Setzt die beiden Textursicht-Umleitungen.
     *
     * <p>⛔ NICHT die Stelle, an der BBS den Film umlenkt (dieser Irrglaube verursachte
     * Aufgabe 91): {@code LevelRenderer.render} setzt {@code outputColorTextureOverride}
     * selbst und raeumt es ab — ein vorher gesetzter Wert ist weg. Die echte Umlenkung ist
     * {@code GameRendererMixin#bbs$umlenkenAufFilmpuffer} via {@link #canReplaceFramebuffer()}.
     * Bleibt stehen, weil {@code null} der Normalzustand ist; ob die Setzerei noch etwas
     * bewirkt, ist OFFEN (Aufgabe 92 — messen, nicht raten).
     */
    private static void reassignFramebuffer(RenderTarget framebuffer)
    {
        RenderSystem.outputColorTextureOverride =
            framebuffer == null ? null : framebuffer.getColorTextureView();

        /* ⛔ DIE TIEFE MUSS MIT. Sie ist eine ZWEITE, eigenstaendige Umleitung, und wer nur
         * die Farbe umlenkt, baut ein Ziel aus zwei verschiedenen Flaechen zusammen.
         *
         * Nachgelesen in PreparedRenderType.drawFromBuffer: der Zeichenweg nimmt die Farbe
         * aus outputColorTextureOverride (falls gesetzt, sonst vom Ziel des RenderTypes) und
         * die Tiefe UNABHAENGIG DAVON aus outputDepthTextureOverride (falls gesetzt, sonst
         * vom Ziel). Beide Zweige entscheiden getrennt -- eine halbe Umleitung ergibt also
         * BBS' Farbflaeche mit fremder Tiefe.
         *
         * Der Zeichenweg prueft die Masse und bricht ab:
         *   "Depth texture: size does not match expected attachment size.
         *    Is 854x480 expected 2560x1440"
         *
         * ⚠️ Die Meldung erscheint im WELTdurchgang (LevelRenderer.render), nicht dort, wo
         * umgelenkt wurde. Sie hat mich einen Messlauf lang in den Oberflaechen-Weg geschickt,
         * wo gar nichts kaputt war. */
        RenderSystem.outputDepthTextureOverride =
            framebuffer == null || !framebuffer.useDepth ? null : framebuffer.getDepthTextureView();
    }

    /* Rendering */

    public static void onWorldRenderBegin()
    {

        if (orthoDistance > 0F)
        {
            /* Give back the culling disabled for the previous ortho frame
             * (see setOrthoDistance); re-armed by the orbit if still on. */
            Minecraft.getInstance().smartCull = true;

        }

        orthoDistance = -1F;

        Minecraft mc = Minecraft.getInstance();
        BBSModClient.getFilms().startRenderFrame(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            menu.startRenderFrame(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }

        renderingWorld = true;

        if (!customSize)
        {
            return;
        }

        toggleFramebuffer(true);
    }

    /**
     * Baut einen Zeichner auf dem LEBENDEN GUI-Zustand.
     *
     * <p>26.2: der Konstruktor nimmt keinen Sofort-Puffer mehr (den gibt es nicht), sondern
     * den {@code GuiRenderState} und die Groesse. Den Zustand holt {@link
     * mchorse.bbs_mod.mixin.client.GuiRenderStateAccessor} aus {@code Gui} -- einen eigenen
     * zu bauen waere uebersetzbar und unsichtbar, weil ihn niemand zeichnet.</p>
     */
    private static GuiGraphicsExtractor zeichner(Minecraft mc)
    {
        /* ⚠️ NICHT selbst bauen: ein eigener Zeichner haette einen eigenen Matrix-Stack,
         * und ein Push hier passte dann zu keinem Pop anderswo. Siehe GuiZeichner. */
        return GuiZeichner.aktuell(mc);
    }

    public static void onWorldRenderEnd()
    {
        Minecraft mc = Minecraft.getInstance();

        if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController controller)
        {
            GuiGraphicsExtractor drawContext = zeichner(mc);
            Batcher2D batcher = new Batcher2D(drawContext);

            UISubtitleRenderer.renderSubtitles(batcher.pose(), batcher, SubtitleClip.getSubtitles(controller.getContext()));
        }

        if (!customSize)
        {
            renderingWorld = false;

            return;
        }

        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();

        if (currentMenu instanceof UIDashboard dashboard)
        {
            /* ⛔ NUR MIT OBERFLAECHEN-KONTEXT (2026-08-04, live abgestuerzt).
             *
             * Diese Stelle laeuft am ENDE des Weltdurchgangs, `Batcher2D.pose()` braucht aber
             * den Extraktor der Oberflaeche -- und den gibt es nur waehrend des
             * Sammel-Durchlaufs. Steht hier keiner, ist es kein Sonderfall, sondern ein
             * Absturz mitten im Bild:
             *
             *   NullPointerException: Cannot invoke "GuiGraphicsExtractor.pose()"
             *     because "this.context" is null
             *       at Batcher2D.pose(Batcher2D.java:129)
             *       at BBSRendering.onWorldRenderEnd(BBSRendering.java:637)
             *   crash-2026-08-04_13.01.54-client.txt, ausgeloest beim Beenden eines Films
             *
             * Ohne Extraktor gaebe es ohnehin niemanden, der die Untertitel entgegennimmt --
             * ueberspringen ist hier also nicht "weniger", sondern das Richtige. */
            if (dashboard.getPanels().panel instanceof UIFilmPanel panel
                && currentMenu.context.batcher.getContext() != null)
            {
                UISubtitleRenderer.renderSubtitles(currentMenu.context.batcher.pose(), currentMenu.context.batcher, SubtitleClip.getSubtitles(panel.getRunner().getContext()));
            }
        }

        renderingWorld = false;
    }

    /**
     * Fuellen UND Abschliessen — der bisherige Weg, unveraendert fuer alle Aufrufer, die
     * beides in einem Rutsch wollen (Welt-Modus).
     */
    public static void onRenderBeforeScreen()
    {
        fuellen();
        abschliessen();
    }

    /**
     * Nur die Vorschau-Textur fuellen, ohne den Framebuffer abzuhaengen.
     *
     * <p>⛔ WOFUER (2026-08-02, #19): im Editor haengt das Fuellen am TAIL von
     * {@code Gui.extractRenderState} — gemessen per Stapelabzug. In 26.2 werden dort erst
     * ALLE UI-Elemente eingesammelt und danach gezeichnet: der Blit des Vorschaufeldes ist
     * also schon eingereiht, wenn die Textur gefuellt wird. Das Feld zeigt deshalb immer
     * das Bild des VORIGEN Durchgangs (gemessen: beim Blit stand die Fuellung auf 132,
     * waehrend sie bei 133 war), und der aktuelle Replay-Umriss darueber sitzt bei
     * Kamerabewegung daneben.
     *
     * <p>Das Fuellen darf deshalb vorziehen, das ABHAENGEN nicht: InGameHudMixin sagt
     * "dort muss der framebuffer bis zum TAIL gebunden bleiben, sonst landet das HUD im
     * haupt-framebuffer statt im viewport". Genau diese Trennung macht diese Methode.
     */
    public static void fuellen()
    {
        /* ⛔ OHNE DIESEN TEST STIRBT DER VIDEO-EXPORT MIT EINEM NULLZEIGER.
         *
         * Belegt durch crash-2026-07-31_15.03: BBSRendering.leseFboFuer dereferenziert sein
         * Argument in der ersten Zeile, und der Aufruf unten reicht das statische Feld
         * `framebuffer` durch. Ist der Puffer noch nicht angelegt -- oder nach einem
         * Groessenwechsel gerade abgeraeumt -- reisst es genau den Vorgang ab, bei dem am
         * meisten Arbeit verloren geht.
         *
         * ⚠️ Der Test gehoert HIERHER und nicht in leseFboFuer: dort waere er
         * Symptombehandlung, die einen halb aufgebauten Blit trotzdem weiterlaufen liesse.
         *
         * ⛔ ZWEITE NULL, 2026-08-04 nachgezogen. Der Test oben deckte nur ein null-ZIEL ab.
         * Der Absturzbericht nennt aber eine ANDERE Null: die Meldung lautet
         * `NPE: RenderTarget.getColorTexture()`, also war das Ziel da und seine Farbtextur
         * nicht. leseFboFuer dereferenziert sie in der ersten Zeile
         * (`((GlTexture) ziel.getColorTexture()).glId()`), und genau dort riss der
         * Video-Export ab (docs/ABSTURZ-HISTORIE.md, G8, 31.07. 15:03).
         *
         * Ein halb angelegtes Ziel ist derselbe Fall wie gar keins: ohne Farbtextur gibt es
         * nichts zu lesen, also wird der Durchgang uebersprungen statt geraten. */
        if (framebuffer == null || framebuffer.getColorTexture() == null)
        {
            return;
        }

        Texture texture = getTexture();
        int targetWidth = getVideoWidth();
        int targetHeight = getVideoHeight();

        if (exportFramebuffer == null)
        {
            exportFramebuffer = new mchorse.bbs_mod.graphics.Framebuffer();
            exportFramebuffer.attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            exportFramebuffer.unbind();
        }

        if (texture.width != targetWidth || texture.height != targetHeight)
        {
            /* ⛔ NIE texture.setSize() direkt: in 26.2 wechselt setSize die GL-Nummer
             * (Texture.allocate legt ein neues GPU-Objekt an), und das FBO behielte die
             * alte im GL-Anhang — der Blit fuellte fortan eine freigegebene Textur,
             * waehrend das Vorschaufeld die neue, ewig leere sampelt (RenderDoc-Beweis
             * 2026-08-13: Welt+Overlays landeten in der toten 1280x720, das GUI las die
             * schwarze 640x360; V1-Wurzelursache). resize() zieht den Anhang nach. */
            exportFramebuffer.resize(targetWidth, targetHeight);
        }

        /* Jedes Bild, nicht nur bei Groessenwechsel: setSize kann auch WOANDERS gelaufen
         * sein (gleiche Groesse, neue GL-Nummer) — dieselbe Entscheidung wie leseFboFuer. */
        exportFramebuffer.anhangNachziehen();

        /* The export framebuffer is rendered at the display's native resolution, which on
         * HiDPI screens (such as Retina) is larger than the requested video size. Downscale
         * it into the export texture with a linear blit so the recording stays at the
         * resolution the user asked for (and gains free supersampling). A plain
         * glCopyTexSubImage2D can't rescale, so it would copy the full-size frame and
         * overflow the video recorder's frame buffer (macOS crashes in storeVecColor_BGR_UB). */
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, leseFboFuer(framebuffer));
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, exportFramebuffer.id);

        /* ⛔ ALPHA MUSS 1 BLEIBEN, SONST FEHLT DER HIMMEL IM VORSCHAUFELD.
         *
         * Direkt aus BBS' Puffer gelesen (tools: Pufferprobe), nach dem Weltdurchgang:
         *
         *     Himmel  173,203,255,0     Farbe genau richtig (#ADCBFF), Alpha NULL
         *     Boden   148,148,148,255
         *     Modell  110,17,80,255
         *
         * Der Himmel wird also gezeichnet und ist nur vollstaendig durchsichtig -- Vanillas
         * Himmelsdurchgang schreibt kein deckendes Alpha, weil der Bildschirm nie gemischt
         * wird. Vanillas eigenes MainTarget zeigt dieselben Alpha-Nullen, das ist kein
         * Portierfehler. In 1.21.1 stand hier Alpha 1, weil Vanillas Loeschen damals BBS'
         * Puffer traf (BBS tauschte das Feld Minecraft.mainRenderTarget); in 26.2 gibt es
         * dieses Feld nicht mehr.
         *
         * Und die durchsichtigen Stellen verschwinden nicht durch Mischung, sondern durch
         * DISCARD im GUI-Fragmentshader: mit einer Pipeline ganz OHNE Mischfunktion
         * (GUI_OPAQUE_TEXTURED_BACKGROUND, blendFunction=Optional.empty) blieben sie ebenso
         * unsichtbar, waehrend alle Punkte mit Alpha 255 sauber durchkamen. Am Blit fuehrt
         * deshalb kein Weg vorbei.
         *
         * Loesung: NACH dem Blit den Alphakanal allein auf 1 setzen.
         *
         * ⚠️ DIE REIHENFOLGE IST KEIN GESCHMACK, SONDERN SPEZIFIKATION. Erst versucht und
         * gemessen wirkungslos: vorher mit Alpha 1 loeschen und den Blit mit maskiertem
         * Alphakanal fahren. glBlitFramebuffer beachtet Schreibmasken NAEMLICH NICHT -- laut
         * GL-Spezifikation wirken auf einen Blit nur Pixel-Ownership, Scissor und
         * sRGB-Wandlung, der komplette Fragmentteil wird uebersprungen. Die Maske blieb also
         * folgenlos und die Nullen kamen mit. glClear dagegen BEACHTET die Maske, deshalb
         * geht es hinterher und nur ueber den Alphakanal.
         *
         * Ebenfalls verworfen: BBS' Puffer vor dem Weltdurchgang mit Alpha 1 vorzuloeschen --
         * gemessen wirkungslos, der Weltdurchgang ueberschreibt Farbe UND Alpha (mit Rot als
         * Loeschfarbe gegengeprueft). */
        /* ⛔ DER SCISSOR MUSS SCHON FUER DEN BLIT AUS SEIN, NICHT ERST FUER DAS CLEAR.
         * Laut GL-Spez wirken auf einen Blit Pixel-Ownership, SCISSOR und sRGB — Schreib-
         * masken nicht (Fragmentteil uebersprungen). Gemessen 2026-08-04: ein stehen-
         * gelassenes GUI-Rechteck (512x256 auf 1280x720) liess den Blit nur einen Ausschnitt
         * beschreiben — sichtbar als eingefrorener Streifen ab 0,8 der Breite (Bugliste
         * #1/#3), bei hohen FPS unsichtbar schnell, bei 10 fps deutlich. */
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        if (scissor)
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        GL30.glBlitFramebuffer(
            0, 0, framebuffer.width, framebuffer.height,
            0, 0, targetWidth, targetHeight,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );

        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            /* ⚠️ GL-Nummern-Gleichheit beweist hier KEINE Objektidentitaet: GL recycelt
             * freigegebene Nummern sofort (V1-Jagd 2026-08-13 — vonTex/nachTex sahen
             * korrekt aus, waehrend das GUI ein totes Objekt gleicher Nummer las). */
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("fuellen.blit",
                "vonTex=" + ((com.mojang.blaze3d.opengl.GlTexture) framebuffer.getColorTexture()).glId()
                    + " von=" + framebuffer.width + "x" + framebuffer.height
                    + " nachTex=" + getTexture().id + " nach=" + targetWidth + "x" + targetHeight);
        }

        /* Dasselbe gilt fuers Clear: ein stehengelassenes Rechteck wuerde den Alphakanal nur
         * teilweise setzen -- ein halb sichtbarer Himmel waere schwerer zu deuten als gar
         * keiner. Der Scissor ist oben schon aus und bleibt es bis zum Ende. */
        GL11.glColorMask(false, false, false, true);
        GL11.glClearColor(0F, 0F, 0F, 1F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColorMask(true, true, true, true);

        if (scissor)
        {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }

        /* ⚗️ Auf Anforderung genau das messen, was die Vorschau danach abtastet. */
        mchorse.bbs_mod.graphics.Pufferprobe.vielleichtLesenFbo(exportFramebuffer.id,
            targetWidth, targetHeight, "export-textur");

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);

    }

    /**
     * Der Schluss des Bildes: Aufnahme-Overlay, Framebuffer abhaengen, ein anstehender
     * Aufloesungswechsel.
     *
     * <p>⛔ GETRENNT VOM FUELLEN (2026-08-02). Beides stand in {@code onRenderBeforeScreen()},
     * und die Methode haengt im Editor am TAIL von {@code Gui.extractRenderState}
     * (gemessen per Stapelabzug: {@code Gui.handler$...$bbs$onRenderEnd <- extractRenderState
     * <- GameRenderer.extract}). In 26.2 werden dort erst ALLE UI-Elemente eingesammelt und
     * danach gezeichnet — der Blit des Vorschaufeldes war also laengst eingereiht, wenn die
     * Textur gefuellt wurde. Ergebnis: das Vorschaufeld zeigt immer das Bild des VORIGEN
     * Durchgangs, und jede Ueberlagerung darueber (Replay-Umriss) sitzt bei Kamerabewegung
     * daneben. Gemessen: beim Blit stand die Fuellung auf 132, waehrend sie bei 133 war.
     *
     * <p>Das Abhaengen MUSS am TAIL bleiben — siehe InGameHudMixin: "dort muss der
     * framebuffer bis zum TAIL gebunden bleiben, sonst landet das HUD im haupt-framebuffer
     * statt im viewport". Nur das FUELLEN darf vorziehen, und genau das trennt diese
     * Methode ab.
     */
    public static void abschliessen()
    {
        renderRecordingOverlay();

        toggleFramebuffer(false);

        if (pendingExportResolutionAction != null)
        {
            Runnable action = pendingExportResolutionAction;
            pendingExportResolutionAction = null;
            Minecraft.getInstance().execute(action);
        }
    }

    public static void scheduleAfterNextExportFrame(Runnable action)
    {
        pendingExportResolutionAction = action;
    }

    /**
     * ⛔ STILLGELEGT IN 26.2 — tut nichts, und das ist eine bewusste Entscheidung, kein Versehen.
     *
     * <p>Der Haken zeichnete BBS-Formen mitten im Chunk-Ebenen-Durchgang, damit sie unter
     * Iris-Shadern im richtigen Durchgang landen. Zwei Dinge stehen dem in 26.2 entgegen, und
     * beide sind strukturell:</p>
     *
     * <ol>
     * <li><b>Der Aufrufer kann nicht mehr einhaengen.</b> {@code WorldRendererMixin} zielte auf
     *     {@code LevelRenderer.renderSectionLayer} -- die Methode existiert nicht mehr, das
     *     Zeichnen der Ebenen ist nach {@code ChunkSectionsToRender#renderGroup} gewandert,
     *     also in eine ANDERE KLASSE.</li>
     * <li><b>Und selbst mit Haken fehlte das Werkzeug.</b> Man baute hier einen
     *     {@code WorldRenderContextImpl} (Fabric-intern, ersatzlos gestrichen) und fuellte ihn
     *     unter anderem mit {@code renderBuffers().bufferSource()}. Einen Sofort-Puffer gibt es
     *     nicht mehr; Geometrie wird bei einem {@code SubmitNodeCollector} eingereicht, und den
     *     bekommt man nur INNERHALB eines Durchgangs gereicht -- nicht an dieser Stelle.</li>
     * </ol>
     *
     * <p>Einen Kontext hier zusammenzuluegen wuerde uebersetzen und nichts zeichnen. Deshalb
     * steht die Methode leer da statt gefuellt: der Verlust ist sichtbar, nicht kaschiert.</p>
     *
     * <p><b>Zurueckholen</b> heisst: Mixin auf {@code ChunkSectionsToRender#renderGroup}
     * umhaengen, den dortigen Sammler durchreichen und daraus eine eigene Umsetzung von
     * {@code LevelRenderContext} bauen (fuenf Methoden: submitNodeCollector, poseStack,
     * gameRenderer, levelRenderer, levelState). Gehoert in die Mixin-Runde.</p>
     */
    public static void onRenderChunkLayer(Matrix4f positionMatrix)
    {
    }

    public static void renderHud(GuiGraphicsExtractor drawContext, float tickDelta)
    {
        Batcher2D batcher2D = new Batcher2D(drawContext);

        BBSModClient.getFilms().renderHud(batcher2D, tickDelta);
    }

    /**
     * Draw the recording countdown / frame-counter overlay. This is operator UI: it is drawn from
     * {@link #onRenderBeforeScreen()} after the export blit but before the buffer is copied to the
     * screen, so it shows up on screen but is never captured into the file.
     */
    private static void renderRecordingOverlay()
    {
        if (!BBSSettings.recordingOverlays.get() || UIScreen.getCurrentMenu() != null)
        {
            return;
        }

        String label;

        if (BBSModClient.isVideoExportDelayPending())
        {
            int countdown = Math.max(0, (int) Math.ceil(BBSModClient.getVideoExportDelayRemainingMs() / 50D));

            label = String.valueOf(countdown / 20F);
        }
        else if (BBSModClient.getVideoRecorder().isRecording())
        {
            int count = BBSModClient.getVideoRecorder().getCounter();

            label = UIKeys.FILM_VIDEO_RECORDING.format(
                count,
                BBSModClient.getKeyRecordVideo().getTranslatedKeyMessage().getString()
            ).get();
        }
        else
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor drawContext = zeichner(mc);

        renderRecordingTimerOverlay(new Batcher2D(drawContext), label);

        /* 26.2: flush() gibt es nicht mehr -- es leerte den Sofort-Puffer. nextStratum() hebt
         * die Stapellage, damit Folgendes ueber dieser Ueberlagerung liegt. Gezeichnet wird
         * spaeter aus dem gesammelten Zustand. */
        drawContext.nextStratum();
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label)
    {
        renderRecordingTimerOverlay(batcher2D, label, 5, 5);
    }

    public static void renderRecordingTimerOverlay(Batcher2D batcher2D, String label, int x, int y)
    {
        int iconX = x + 16;

        batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, iconX, y, 1F, 0F);
        batcher2D.textCard(label, iconX + 3, y + 4, Colors.WHITE, Colors.A50);
    }

    public static void renderCoolStuff(LevelRenderContext worldRenderContext)
    {
        /* 26.2: die Notreparatur fuer einen fehlenden Matrizen-Stapel ist ersatzlos entfallen.
         * Fabrics LevelRenderContext traegt jetzt IMMER einen poseStack() -- der Zweig hier
         * konnte gar nicht mehr feuern, und WorldRenderContextImpl (Fabric-intern) gibt es
         * nicht mehr, um hineinzuschreiben.
         *
         * ⚠️ Der Stapel ist die EINHEITSMATRIX, nicht die Ansichtsmatrix. Wer daraus einen
         * Strahl baut, zielt an allem vorbei. Die Ansicht steht in
         * levelState().cameraRenderState.viewRotationMatrix. */

        /* Feed the world camera orientation into the holder that replaced
         * RenderSystem's inverse view rotation matrix (removed in 1.21.1), so
         * billboards and particles keep facing the camera in world space. */
        InverseView.set(new Matrix3f().rotation(worldRenderContext.levelState().cameraRenderState.orientation));
        /* ⛔ Figuren VOR den Editor-Overlays — Herleitung am Verdeckt-Pass in ModelIKDebug. */
        BBSModClient.getFilms().render(worldRenderContext);

        if (Minecraft.getInstance().gui.screen() instanceof UIScreen screen)
        {
            screen.renderInWorld(worldRenderContext);
        }
    }

    public static boolean isOptifinePresent()
    {
        return optifine;
    }

    public static boolean isRenderingWorld()
    {
        return renderingWorld;
    }

    /**
     * Arm the orthographic projection for the current frame. Pass the orbit
     * camera's distance to the pivot; negative disables. The value is reset
     * at the beginning of every world render, so the caller must re-arm it
     * each frame for as long as ortho should stay on.
     */
    public static void setOrthoDistance(float distance)
    {
        orthoDistance = distance;

        if (distance > 0F)
        {
            /* The chunk occlusion culling walks sections outward from the
             * camera POINT, which is only sound for a perspective projection —
             * under ortho's parallel sightlines it over-culls sections near
             * the screen edges. Disable it for the frame (Sodium honours the
             * same flag); the frustum and render distance still cull. Sodium's
             * own point-camera heuristics get the same treatment. */
            Minecraft.getInstance().smartCull = false;

        }
    }

    public static boolean isOrthoActive()
    {
        return orthoDistance > 0F;
    }

    /**
     * Build the orthographic projection replacing the given perspective one
     * (returns the input untouched when ortho is not armed). FOV and aspect are
     * derived from the perspective matrix itself, so the ortho frame height
     * matches the perspective frame height at the orbit pivot's distance: the
     * subject keeps its size when toggling projections, and the scroll zoom
     * keeps working through the orbit distance.
     *
     * @param minHalfHeight a lower bound on the frame's half height, and the
     *        slack behind the camera plane the near plane is given; the frustum
     *        culling matrix is built with a loose bound on both, so culling
     *        stays conservative when zoomed all the way in.
     */
    public static Matrix4f getOrthoProjection(GameRenderer renderer, Matrix4f perspective, float minHalfHeight)
    {
        if (orthoDistance <= 0F)
        {
            return perspective;
        }

        float tanHalfFov = 1F / perspective.m11();
        float aspect = perspective.m11() / perspective.m00();
        float halfHeight = Math.max(minHalfHeight, orthoDistance * tanHalfFov);
        float halfWidth = halfHeight * aspect;

        /* The near plane sits exactly at the camera, the way a perspective one
         * effectively does: under ortho's parallel sightlines everything BEHIND
         * the camera projects into the frame as well, so a hillside the camera
         * stands in paints itself over the subject, and no amount of orbiting
         * gets past it. Clipping at the camera plane drops precisely what the
         * eye has already passed and nothing the eye still faces — pushing the
         * plane any further in would slice the ground in front of the camera
         * and leave a hole where it was. Zooming in walks the camera towards
         * the pivot, so the zoom doubles as the control over how much of an
         * obstacle in front gets cut.
         *
         * The far plane is the one vanilla builds its perspective with, which
         * already bounds everything the game draws; together with the near
         * plane it keeps the box tight enough for the frustum to cull with,
         * which matters here because chunk occlusion culling is off (see
         * setOrthoDistance). */
        float near = -minHalfHeight;
        /* 26.2: GameRenderer.getDepthFar() ist entfallen. Vanilla baut seine ferne Ebene heute
         * aus der Sichtweite: Abschnitte mal 16, mindestens 32 -- derselbe Wert, den die
         * perspektivische Projektion des Spiels benutzt. Er bleibt damit die Schranke, die
         * alles Gezeichnete umschliesst, und haelt den Kasten eng genug fuers Culling. */
        float far = Math.max(Minecraft.getInstance().options.getEffectiveRenderDistance() * 16F, 32F);

        return new Matrix4f().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
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

    /**
     * Iris considers a vanilla core program applied during world rendering a stray
     * draw into its G-buffers and masks its color/depth writes. Reporting that the
     * main framebuffer isn't bound (like vanilla render targets do via bindWrite)
     * turns both core shader overrides and that masking off.
     */
    public static void setIrisMainBound(boolean bound)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.setMainBound(bound);
    }

    public static void trackTexture(Texture texture)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.trackTexture(texture);
    }

    public static float[] calculateTangents(float[] t, float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return t;
        }

        return IrisUtils.calculateTangents(t, v, n, u);
    }

    public static float[] calculateTangents(float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return v;
        }

        return IrisUtils.calculateTangents(v, n, u);
    }


    /**
     * Reicht BBS' Shader-Kurven als eigene Uniforms an Iris durch.
     *
     * <p>⚠️ Der Aufruf kommt aus {@code CustomUniformsBuilderMixin}, also von IRIS her —
     * nicht von uns. Faellt diese Methode weg, kompiliert das Mixin nicht mehr; genau daran
     * ist die Stilllegung am 2026-08-10 aufgefallen.</p>
     */
    public static void addUniforms(List<CachedUniform> list, Map<String, ShaderCurves.ShaderVariable> variableMap)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.addUniforms(list, variableMap);
    }

    public static List<String> getShadersSliderOptions()
    {
        if (!iris)
        {
            return Collections.emptyList();
        }

        return IrisUtils.getSliderProperties();
    }

    public static Map<String, String> getShadersLanguageMap(String language)
    {
        if (!iris)
        {
            return Collections.emptyMap();
        }

        return IrisUtils.getShadersLanguageMap(language);
    }

    /* Curves */

    public static Long getTimeOfDay()
    {
        if (!Minecraft.getInstance().isSameThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get(ShaderCurves.SUN_ROTATION) : null;

            if (v != null)
            {
                return (long) (v * 1000L);
            }
        }

        return null;
    }

    public static Double getBrightness()
    {
        if (!Minecraft.getInstance().isSameThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get(ShaderCurves.BRIGHTNESS) : null;

            if (v != null)
            {
                return v;
            }
        }

        return null;
    }

    public static Double getWeather()
    {
        if (!Minecraft.getInstance().isSameThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());
            Double v = values != null ? values.get(ShaderCurves.WEATHER) : null;

            if (v != null)
            {
                return v;
            }
        }

        return null;
    }

    public static Integer getChromaSkyColorArgb()
    {
        if (!Minecraft.getInstance().isSameThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Integer> values = CurveClip.getColorValues(controller.getContext());

            if (values != null)
            {
                return values.get(CurveClip.CHROMA_SKY_COLOR);
            }
        }

        return null;
    }

    public static Function<VertexConsumer, VertexConsumer> getColorConsumer(Color color)
    {
        return (b) -> new RecolorVertexConsumer(b, color);
    }
}
