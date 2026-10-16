package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    /**
     * ⭐ HIER lenkt BBS das Rendern in seinen eigenen Rahmenpuffer um — der Nachfolger des
     * ueberschriebenen {@code Minecraft.mainRenderTarget}-Feldes aus 1.21.1.
     *
     * <h2>Warum nicht ueber die Textursicht-Umleitung (das war der Fehler)</h2>
     *
     * <p>Der Port hatte dafuer {@code RenderSystem.outputColorTextureOverride} genommen, weil
     * das Feld {@code Minecraft.mainRenderTarget} verschwunden war und jenes wie der Nachfolger
     * aussah. Es ist keiner, und das laesst sich im Bytecode zeigen:
     *
     * <ul>
     *   <li>{@code LevelRenderer.render} holt sein Ziel ueber {@code GameRenderer.mainRenderTarget()}
     *       und meldet es mit {@code FrameGraphBuilder.importExternal("main", ...)} beim Bildgraphen
     *       an. DA entscheidet sich, wohin die Welt geht.</li>
     *   <li>Dieselbe Methode SETZT die beiden Override-Felder anschliessend selbst und raeumt sie
     *       danach wieder ab. Es ist ein kurzlebiger Schalter INNERHALB eines Durchgangs, kein
     *       globaler Umlenker — wer ihn vorher setzt, dessen Wert ist weg, sobald Vanilla ihn
     *       ueberschreibt.</li>
     * </ul>
     *
     * <p>Gemessen, nicht geschlossen: mit gesetzten Overrides blieb BBS' Puffer ueber den ganzen
     * Weltdurchgang schwarz (Helligkeit 0 an beiden Enden), waehrend Mojangs Hauptziel gefuellt
     * war. Genau deshalb blieb das Vorschaufeld des Film-Editors schwarz (Aufgabe 91).
     *
     * <p>⚠️ DIE BEDINGUNG MUSS DIESELBE SEIN WIE IN {@code WindowMixin}. {@code GameRenderer.render}
     * vergleicht die Masse des Ziels mit {@code windowRenderState} und RESIZED das Ziel bei
     * Abweichung. Solange beide an {@code canReplaceFramebuffer()} haengen, meldet das Fenster
     * genau dann die Videogroesse, wenn hier auch BBS' Puffer herausgegeben wird — sonst
     * schrumpfte Vanilla den Puffer auf Fenstergroesse und die Aufnahme haette die falsche
     * Aufloesung.
     */
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void bbs$umlenkenAufFilmpuffer(CallbackInfoReturnable<RenderTarget> info)
    {
        RenderTarget puffer = (RenderTarget) MixinBruecke.rufe("SpielRendererLogik", "filmpuffer");

        if (puffer != null)
        {
            info.setReturnValue(puffer);
        }
    }

    /**
     * This injection cancels bobbing when camera controller takes over
     */
    /* ⚠️ 26.2: (CameraRenderState, PoseStack) statt (PoseStack, float). Der Teiltick wird
     * nicht mehr gereicht -- das Wackeln haengt jetzt am Kamera-Zustand, nicht mehr an einem
     * durchgereichten Zeitwert. Wir holen ihn dort, wo Vanilla ihn auch herholt. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(CameraRenderState kamera, PoseStack matrices, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielRendererLogik", "kopfwackeln", matrices))
        {
            ci.cancel();
        }
    }

    /* ⚠️ DAS SICHTFELD WIRD NICHT MEHR HIER UEBERSCHRIEBEN (26.2).
     *
     * `GameRenderer.getFov(Camera, float, boolean)` gibt es nicht mehr. Das sichtfeld
     * rechnet jetzt die KAMERA selbst, einmal je bild in `Camera.update(DeltaTracker)`:
     *
     *     alignWithEntity(δ)                 <- pose (frueher Camera.setup)
     *     this.fov    = calculateFov(δ)      <- welt   (frueher getFov(.., true))
     *     this.hudFov = calculateHudFov(δ)   <- hand   (frueher getFov(.., false))
     *     prepareCullFrustum(..) / setupPerspective(.., fov, ..)
     *
     * Der BBS-ueberschreiber (gun-zoom, clip-FOV, sprint-abzug fuer die hand) sitzt darum
     * jetzt in {@link CameraMixin} — dieselbe zielklasse wie die kamerapose. Aus dem
     * booleschen `changingFov` sind zwei getrennte methoden geworden; der schalter
     * "Hand follows sprint FOV" wirkt entsprechend auf `calculateHudFov`.
     *
     * Gemessen am 26.2-jar (javap -c auf net.minecraft.client.Camera), nicht angenommen. */

    /**
     * This injection replaces the camera roll when camera controller takes over
     */
    /* ⚠️ 26.2: wie bobView -- (CameraRenderState, PoseStack). Der Teiltick wird hier gar
     * nicht gebraucht, der Parameter faellt einfach weg. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(CameraRenderState kamera, PoseStack matrices, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielRendererLogik", "trefferneigung", matrices))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    public void onRenderHand(CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielRendererLogik", "handAnfang"))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    public void onRenderHandEnd(CallbackInfo info)
    {
        MixinBruecke.rufe("SpielRendererLogik", "handEnde");
    }

    @Inject(at = @At("HEAD"), method = "renderLevel")
    private void onWorldRenderBegin(CallbackInfo callbackInfo)
    {
        MixinBruecke.rufe("SpielRendererLogik", "weltAnfang");
    }

    /**
     * These two injections substitute an orthographic projection when the film
     * editor's orbit camera asks for one (see BBSRendering#getOrthoProjection).
     * The frustum culling matrix gets a loose lower bound on the frame size so
     * culling stays conservative when zoomed all the way in; the same bound
     * pushes its near plane back, so the frustum never culls a section the
     * render would still have drawn.
     */
    /* ⛔ HERAUSGENOMMEN 2026-07-31 -- die orthografische Editor-Kamera ist voruebergehend aus.
     *
     * Hier standen zwei @ModifyArg, die eine ORTHOGRAFISCHE Projektion unterschoben, wenn die
     * Umlaufkamera des Film-Editors eine wollte: einmal am prepareCullFrustum-Aufruf (damit das
     * Keulen zur gezeichneten Ansicht passt), einmal am renderLevel-Aufruf.
     *
     * Beide Ziele sind weg. Die Aufrufe sind von LevelRenderer nach Camera gewandert, und --
     * das ist der eigentliche Punkt -- setupPerspective nimmt gar keine Matrix mehr entgegen,
     * sondern (zNear, zFar, fov, breite, hoehe). Es gibt kein Matrix-Argument mehr, das man
     * unterschieben koennte. Der Trick selbst ist also nicht mehr uebersetzbar, nicht nur der
     * Deskriptor.
     *
     * ⭐ DER RUECKWEG IST BESSER ALS DAS ORIGINAL. 26.2 hat eine erstklassige orthografische
     * Projektion: net.minecraft.client.renderer.Projection.setupOrtho(...) neben
     * setupPerspective(...), und Camera haelt so ein Projection-Objekt als Feld. Statt eine
     * fertige Matrix zu ersetzen, sagt man dem Projektionsobjekt, dass es orthografisch sein
     * soll -- damit stimmen Keulen, nahe/ferne Ebene und Projektion automatisch ueberein,
     * waehrend der alte Weg sie an ZWEI Stellen getrennt unterschieben musste und dabei (siehe
     * die alten Kommentare) eine lose untere Schranke brauchte, damit das Keulen nicht zu viel
     * wegschnitt.
     *
     * ZU TUN: in CameraMixin ein @Shadow auf das Projection-Feld, Injektor am RETURN von
     * setupPerspective, und bei aktiver Umlaufkamera setupOrtho aufrufen. Die Parameterfolge
     * von setupOrtho ist NICHT geraten nachzuschlagen, sondern an einem Vanilla-Aufrufer
     * abzulesen. Bis dahin: der Film-Editor zeichnet perspektivisch statt orthografisch. */


    @Inject(at = @At("RETURN"), method = "renderLevel")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        MixinBruecke.rufe("SpielRendererLogik", "weltEnde");
    }

    /**
     * {@code render3dHud} @HEAD — nach der Welt UND nach dem Composite eines Shaderpacks
     * (Vitrail haengt sein {@code PackChain.draw} direkt hinter {@code LevelRenderer.render}),
     * aber vor Handprojektion und Tiefenloeschung. Was bei END_MAIN gezeichnet wird, ueberschreibt
     * das Composite (gemessen 2026-10-11).
     */
    @Inject(method = "render3dHud", at = @At("HEAD"), require = 1)
    private void onBeforeHud3d(CameraRenderState kamera, PlayerRenderState spieler, OptionsRenderState optionen,
        boolean hud, CallbackInfo info)
    {
        MixinBruecke.rufe("SpielRendererLogik", "nachDerWelt", kamera);
    }

    /**
     * 26.3: Hand, Bildschirmeffekte und Debug-Fadenkreuz lesen das FELD {@code mainRenderTarget},
     * nicht die oben umgelenkte Methode. Im Editor landete die Tracker-Hand darum in Mojangs
     * Fensterziel statt in BBS' Puffer und blieb unsichtbar (gemessen 2026-10-12: Puffer 1460x658,
     * Feld 1908x1028). require = 5: vier Lesestellen in render3dHud, eine in renderItemInHand.
     */
    @ModifyExpressionValue(method = {"render3dHud", "renderItemInHand"}, require = 5, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
        opcode = Opcodes.GETFIELD))
    private RenderTarget bbs$handZiel(RenderTarget original) throws Throwable
    {
        return (RenderTarget) MixinBruecke.rufePflicht("SpielRendererLogik", "himmelsziel", original);
    }

    /**
     * ⛔ EINHAENGUNG ZURUECK AUF DIE FORM DER VORLAGE (2026-08-02).
     *
     * <p>Hier stand eine FIELD-Injektion auf {@code Options.hideGui} — spezifischer als
     * noetig, und genau deshalb zerbrochen: das Feld gibt es in 26.2 nicht mehr, weder als
     * Feld noch als Methode. Mit {@code require = 0} scheiterte der Haken STILL: 0 Stellen
     * im Bytecode, keine Logzeile, das Feature einfach tot. Gefunden hat es
     * {@code tools/mixin-haken-feuert.py} — beim ersten Lauf, nachdem das Werkzeug selbst
     * repariert war (es stuerzte vorher bei JEDEM Aufruf ab und lief nie).</p>
     *
     * <p>26.1 macht es schlichter und robuster: {@code @At("HEAD")} auf {@code render},
     * Bedingung im Java-Code. Diese Form ueberlebt einen Feldwechsel, weil sie an keinem
     * Feld haengt. Wir hatten sie im Port ohne Not verschaerft.</p>
     *
     * <p>Die Bedingung selbst kennt den 26.2-Weg bereits: {@code HudSichtbarkeit.istVersteckt()}
     * liest {@code Minecraft.getInstance().gui.hud.isHidden()}.</p>
     *
     * <p><b>require = 1 ist Absicht.</b> Faellt das Ziel beim naechsten Mapping-Wechsel weg,
     * soll der Start LAUT scheitern statt still ein Feature zu verlieren.</p>
     */
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void onBeforeHudRendering(CallbackInfo info)
    {
        MixinBruecke.rufe("SpielRendererLogik", "vorDemBildschirm");
    }

    @Inject(method = "render", require = 1, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.BEFORE))
    private void bbs$beforeGuiDraw(CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("SpielRendererLogik", "vorGuiZeichnen");
    }

    @Inject(method = "render", require = 1, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
    private void bbs$afterGuiDraw(CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("SpielRendererLogik", "nachGuiZeichnen");
    }
}
