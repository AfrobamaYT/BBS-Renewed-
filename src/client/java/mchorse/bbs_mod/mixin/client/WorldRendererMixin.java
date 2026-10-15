package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 nachgezogen. Von den sieben alten Einhaengepunkten hat KEINER seinen Namen behalten --
 * {@code LevelRenderer} hat oeffentlich nur noch {@code render}, {@code prepareChunkRenders},
 * {@code resize} und {@code endFrame}. Was aus den einzelnen Haken wurde:
 *
 * <ul>
 * <li>{@code renderLevel} → {@code render}. Reine Umbenennung.</li>
 * <li>{@code prepareCullFrustum} → weg. Die Kameramatrix steht jetzt im
 *     {@code CameraRenderState}, den {@code render} gereicht bekommt, also holen wir sie
 *     dort ab. ⚠️ {@code viewRotationMatrix}, NICHT {@code projectionMatrix} -- das ist die
 *     Entsprechung der alten {@code positionMatrix} (an anderer Stelle in diesem Port
 *     gemessen).</li>
 * <li>{@code initOutline} → weg, und ERSATZLOS richtig: der Haken rief dasselbe
 *     {@code resizeExtraFramebuffers} wie der {@code resize}-Haken darunter. Er war schon
 *     vorher doppelt gemoppelt, nur fiel es nicht auf.</li>
 * <li>{@code renderSectionLayer} → in eine ANDERE KLASSE gewandert. Chunk-Schichten zeichnet
 *     heute {@code ChunkSectionsToRender.renderGroup}; der Haken lebt deshalb in
 *     {@link ChunkSectionsToRenderMixin} weiter.</li>
 * <li>{@code renderSky} → weg, und mit ihm der Chroma-Himmel. Siehe unten.</li>
 * </ul>
 *
 * <h2>⛔ Der Chroma-Himmel ist raus -- und der Weg zurueck ist BESSER als vorher</h2>
 *
 * <p>Der alte Haken faerbte den Himmel, indem er den Farbpuffer per {@code glClearColor} auf
 * die Chroma-Farbe loeschte und Vanillas Himmel-Zeichnen abbrach. Beides gibt es nicht mehr:
 * das Himmel-Zeichnen haengt im Bildgraphen ({@code addSkyPass}, privat), und einen globalen
 * Loeschbefehl an dieser Stelle gibt es auch nicht.</p>
 *
 * <p>Die alte Fassung hatte dabei einen dokumentierten Mangel: ferne Landschaft blendete
 * weiter in die VANILLA-Nebelfarbe, weil {@code setShaderFogColor} fehlte -- am Horizont
 * blieb ein Farbsaum stehen, den man nicht wegkeyen konnte.</p>
 *
 * <p><b>Genau das loest 26.2.</b> Himmel- UND Nebelfarbe sind UMGEBUNGSATTRIBUTE geworden
 * ({@code EnvironmentAttributes.SKY_COLOR} und {@code FOG_COLOR}, ortsabhaengig abfragbar
 * ueber {@code level.environmentAttributes()}). Wer den Chroma-Himmel neu baut, setzt beide --
 * und bekommt damit zum ersten Mal einen Horizont OHNE Saum. Das ist kein Nachbauen des alten
 * Hakens, sondern die Gelegenheit, das Feature richtig zu machen. Eigener Schritt, bewusst
 * nicht hier hineingezwaengt.</p>
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin
{
    /**
     * ⚗️ MESSFUEHLER (2026-08-01): wird der Himmelspass ueberhaupt in den Bildgraphen
     * eingehaengt?
     *
     * <p>Der Himmel fehlt im Port vollstaendig, und die gesamte Eingabeseite ist bereits
     * ausgeschlossen: Flag 124x true, Nebelfarbe = genau die fehlende Farbe (#ADCBFF),
     * SKY_COLOR = #78A7FF, Pipelines registriert, und der Zustand WIRD eingesammelt
     * (SkyRenderer.extractRenderState laeuft 127x). Damit bleiben drei Faelle, und dieser
     * Zaehler trennt den ersten von den anderen beiden:
     *
     * <ul>
     * <li>Zaehler 0 → der Pass wird gar nicht eingehaengt.</li>
     * <li>Zaehler laeuft → er wird eingehaengt und zeichnet ins Leere oder wird uebermalt.</li>
     * </ul>
     */
    @Inject(method = "addSkyPass", at = @At("HEAD"))
    private void bbs$himmelspassGemessen(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "himmelspass");
    }

    /* 26.2: das Feld heisst entityOutlineTarget -- es war immer schon das Umriss-Ziel, der
     * Name sagt es jetzt. Ein @Shadow auf den alten Namen bricht den Start. */
    @Shadow
    private RenderTarget entityOutlineTarget;

    /* Deferred form translucency spans the frame: forms enqueue their translucent pass while
     * entities render, and the queue flushes right before the translucent terrain layer so the
     * blending sits under water/glass the way vanilla entities do. The RETURN hook is a safety
     * net for frames where the translucent layer never draws (e.g. a replaced terrain pipeline). */
    //? if >=26.1 {
    @Inject(method = "render", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    *///?}
    public void onRenderWorldStart(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "renderAnfang");
    }

    //? if >=26.1 {
    @Inject(method = "render", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("RETURN"))
    *///?}
    public void onRenderWorldEnd(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "renderEnde");
    }

    /**
     * Die Kameramatrix fuer BBS, frueher aus {@code prepareCullFrustum}.
     *
     * <p>⚠️ Render-Zustaende werden WIEDERVERWENDET -- deshalb wird der Wert KOPIERT (set),
     * nicht die Matrix als Referenz gehalten. Vanilla ueberschreibt sie im naechsten Bild.</p>
     */
    //? if >=26.1 {
    @Inject(method = "render", at = @At("HEAD"))
    private void onSetupFrustum(com.mojang.blaze3d.resource.GraphicsResourceAllocator zuteiler,
        //? if >=26.3 {
        /*boolean bloecke, CameraRenderState kamera,
        com.mojang.blaze3d.buffers.GpuBufferSlice nebel,
        org.joml.Vector4f nebelFarbe, boolean himmel, boolean drahtgitter, CallbackInfo info)
        *///?} else {
        net.minecraft.client.DeltaTracker takt, boolean bloecke, CameraRenderState kamera,
        org.joml.Matrix4fc projektion, com.mojang.blaze3d.buffers.GpuBufferSlice nebel,
        org.joml.Vector4f nebelFarbe, boolean himmel, CallbackInfo info)
        //?}
    {
        MixinBruecke.rufe("WeltRendererLogik", "projektionStempeln",
            bloecke, kamera, nebel, nebelFarbe, himmel);
    }
    //?} else {
    /*/^* Vor 26.1 kommen Blickrotation und Projektion als eigene Matrizen in renderLevel
     * (GameRenderer.renderLevel: Blick, Projektion, Culling-Projektion), nicht im Kamerazustand. *^/
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onSetupFrustum(com.mojang.blaze3d.resource.GraphicsResourceAllocator zuteiler,
        net.minecraft.client.DeltaTracker takt, boolean bloecke, net.minecraft.client.Camera kamera,
        org.joml.Matrix4f blick, org.joml.Matrix4f projektion, org.joml.Matrix4f culling,
        com.mojang.blaze3d.buffers.GpuBufferSlice nebel, org.joml.Vector4f nebelFarbe, boolean himmel, CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "projektionStempeln",
            bloecke, projektion, blick, nebel, nebelFarbe, himmel);
    }
    *///?}

    @Inject(at = @At("RETURN"), method = "resize")
    private void onResized(CallbackInfo info)
    {
        /* entityOutlineTarget ist @Shadow — der Kernel bekommt den Wert, nicht das Feld. */
        MixinBruecke.rufe("WeltRendererLogik", "groesseGeaendert", this.entityOutlineTarget);
    }
}
