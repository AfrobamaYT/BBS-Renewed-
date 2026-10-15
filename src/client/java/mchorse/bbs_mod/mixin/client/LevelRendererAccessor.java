package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gibt den {@link LevelRenderState} des Weltzeichners heraus.
 *
 * <h2>Warum BBS ihn braucht</h2>
 *
 * <p>In 26.2 liegt die Projektionsmatrix als Uniform-Puffer auf der Grafikkarte;
 * {@code RenderSystem.getProjectionMatrixBuffer()} gibt einen {@code GpuBufferSlice}
 * heraus, aus dem keine Zahlen mehr zurueckkommen. Die einzige lesbare Fassung steht im
 * eingefrorenen Kamerazustand: {@code levelRenderState.cameraRenderState.bbsProjectionMatrix()}.
 *
 * <p>An mehreren Stellen braucht BBS diese Zahlen wirklich und nicht nur zum
 * Wiederherstellen: der Tracker skaliert m00/m11, um dem Sprint-Sichtfeld der Hand zu
 * folgen, und der Achsen-Gizmo rechnet aus m11 das Sichtfeld zurueck, um seine Groesse
 * entfernungsabhaengig zu waehlen.
 *
 * <p>Wer einen {@code WorldRenderContext} hat, nimmt {@code levelState()} und braucht diesen
 * Accessor NICHT. Er ist fuer die Stellen da, die aus einem Mixin heraus laufen und keinen
 * Kontext gereicht bekommen.
 *
 * <p>Das Feld ist privat und hat keinen Getter (javap -p:
 * {@code private final net.minecraft.client.renderer.state.LevelRenderState levelRenderState;}).
 *
 * <p>⚠️ Der Zustand ist nur WAEHREND eines Weltdurchgangs gefuellt. Ausserhalb steht dort
 * der Wert des letzten Bildes — fuer die Hand und den Gizmo richtig, weil beide im
 * Durchgang laufen; fuer alles andere eine Falle.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor
{
    @Accessor("levelRenderState")
    LevelRenderState bbs$getLevelRenderState();
}
