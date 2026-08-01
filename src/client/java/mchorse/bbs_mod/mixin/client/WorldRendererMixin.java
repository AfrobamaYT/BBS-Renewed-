package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
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
    /* 26.2: das Feld heisst entityOutlineTarget -- es war immer schon das Umriss-Ziel, der
     * Name sagt es jetzt. Ein @Shadow auf den alten Namen bricht den Start. */
    @Shadow
    private RenderTarget entityOutlineTarget;

    /* Deferred form translucency spans the frame: forms enqueue their translucent pass while
     * entities render, and the queue flushes right before the translucent terrain layer so the
     * blending sits under water/glass the way vanilla entities do. The RETURN hook is a safety
     * net for frames where the translucent layer never draws (e.g. a replaced terrain pipeline). */
    @Inject(method = "render", at = @At("HEAD"))
    public void onRenderWorldStart(CallbackInfo info)
    {
        FormTranslucentQueue.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void onRenderWorldEnd(CallbackInfo info)
    {
        FormTranslucentQueue.flush();
    }

    /**
     * Die Kameramatrix fuer BBS, frueher aus {@code prepareCullFrustum}.
     *
     * <p>⚠️ Render-Zustaende werden WIEDERVERWENDET -- deshalb wird der Wert KOPIERT (set),
     * nicht die Matrix als Referenz gehalten. Vanilla ueberschreibt sie im naechsten Bild.</p>
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onSetupFrustum(com.mojang.blaze3d.resource.GraphicsResourceAllocator zuteiler,
        net.minecraft.client.DeltaTracker takt, boolean bloecke, CameraRenderState kamera,
        org.joml.Matrix4fc projektion, com.mojang.blaze3d.buffers.GpuBufferSlice nebel,
        org.joml.Vector4f nebelFarbe, boolean himmel, CallbackInfo info)
    {
        /* Messfuehler: 26.2 uebergibt an LevelRenderer.render ein Flag, OB der Himmel
         * gezeichnet wird. Der Himmel fehlt im Port komplett (schwarz statt blau, bei
         * Tageszeit 6000) -- also zuerst nachsehen, was hier ankommt, statt den
         * Himmelspass zu suchen. */
        /* ⛔ MIXIN-KLASSEN SIND NICHT HOTSWAPBAR -- jede Messung hier kostet einen Boot.
         * Darum ALLE verdaechtigen Werte auf einmal, nicht einen pro Lauf. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("welt.himmel-" + himmel,
            "nebelfarbe=" + (nebelFarbe == null
                ? "null"
                : nebelFarbe.x + "," + nebelFarbe.y + "," + nebelFarbe.z + "," + nebelFarbe.w)
            + " | nebelpuffer=" + (nebel != null)
            + " | bloecke=" + bloecke
            + " | projektion=" + (projektion == null ? "null" : String.valueOf(projektion.m00())));

        BBSRendering.camera.set(kamera.viewRotationMatrix);
    }

    @Inject(at = @At("RETURN"), method = "resize")
    private void onResized(CallbackInfo info)
    {
        if (this.entityOutlineTarget == null)
        {
            return;
        }

        BBSRendering.resizeExtraFramebuffers();
    }
}
