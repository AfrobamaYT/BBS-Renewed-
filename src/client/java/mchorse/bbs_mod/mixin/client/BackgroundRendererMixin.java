package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class BackgroundRendererMixin
{
    /**
     * Under the orthographic projection the whole frame sits at roughly the
     * same depth, but blocks near the screen edges are laterally further from
     * the camera point than the view distance — the fog paints them sky
     * colored, which reads as geometry vanishing at the edges (and Sodium
     * additionally culls whole sections beyond the fog end it reads back from
     * RenderSystem). Push the fog out of reach for the ortho frame.
     */
    /**
     * ⚠️ 26.2: {@code setupFog} ist keine statische void-Methode mehr, sondern eine
     * Instanzmethode, die das fertige {@link FogData} ZURUECKGIBT; erst danach schiebt
     * {@code FogRenderer#updateBuffer} es in den Uniformpuffer. Damit gibt es die beiden
     * globalen Schalter {@code RenderSystem.setShaderFogStart/End} nicht mehr — man
     * schreibt stattdessen in das zurueckgegebene Datenobjekt, solange es noch nicht
     * hochgeladen ist. Deshalb haengt der Einhaengepunkt jetzt am RETURN und nimmt den
     * Rueckgabewert.
     *
     * <p>⚠️ VERHALTENSAENDERUNG: die alten zwei Werte hatten kein exaktes Gegenstueck.
     * FogData trennt Umgebungsnebel (Wasser, Lava, Blindheit) und Sichtweiten-Nebel, dazu
     * kommen Himmels- und Wolkenende. Weit weggeschoben werden hier ALLE VIER Paare —
     * sonst bliebe im Ortho-Bild z.B. der Wassernebel stehen, den der alte Aufruf mangels
     * getrennter Werte gleich mit erschlagen hat.</p>
     */
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void onApplyFog(Camera camera, int renderDistanceChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> info)
    {
        MixinBruecke.rufe("OrthoNebelLogik", "nebelWegschieben", info.getReturnValue());
    }
}
