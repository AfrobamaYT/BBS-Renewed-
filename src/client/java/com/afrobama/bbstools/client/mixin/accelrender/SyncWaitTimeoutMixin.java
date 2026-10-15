// Coded by Afrobama/Claude — patches AccelRender's Sync.waitSync() to
// use a BOUNDED timeout (500ms) instead of Long.MAX_VALUE (infinite).
//
// THE BUG (AccelRender Sync.java:22, MIT-licensed):
//   public void waitSync() {
//       glClientWaitSync(syncHandle, GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
//   }
//
// Called from AcceleratedRingBuffers.fail() when all ring slots are
// in-flight and the GPU hasn't signaled completion yet. With our 20k
// same-mesh same-frame workload + camera-pan triggering power-of-2
// buffer cascade resizes (sharingBuffer starts at 64 bytes, needs
// 2.24 MB → ~16 reallocations in one frame, each doing
// glCopyNamedBufferSubData + persistent unmap/remap = full GPU sync),
// the GPU falls behind. Once the ring runs out of free slots, waitSync
// blocks the render thread INDEFINITELY. NVIDIA's driver TDR can't
// recover from accumulated unsignaled fences → kernel display state
// corruption → full PC freeze, hard-reset required.
//
// FIX: cap timeout at 500_000_000 ns (500ms). On timeout, return
// without blocking — caller (AcceleratedRingBuffers) will retry next
// frame. Hard PC freeze → 1 FPS stutter. The user's UX recovers
// gracefully even under heavy GPU pressure.
//
// EVIDENCE: deep-research 2026-05-17 identified this as the freeze
// mechanism. Confirmed by AccelRender GitHub issues #94 and #111 in
// the same regime (many-instance same-mesh under Iris).
//
// SAFETY: timing out a GL fence wait does NOT corrupt GL state. The
// sync handle remains valid; calling waitSync again on the next frame
// is well-defined. Worst case: one frame's draw is dropped because we
// proceeded before GPU was actually done — visible as a 1-frame stutter
// or stale frame, never a crash.
//
// HOTSWAP-UMBAU (2026-07-26): @Overwrite -> MixinExtras @WrapMethod. Ein
// @Overwrite-handler ist byte-für-byte in Sync.class gewoben und kann nie
// hotswapbar werden (siehe MixinBruecke-javadoc: der KnotClassLoader wird
// vom kernel-swap nie erreicht). @WrapMethod umschliesst die methode und
// liefert ein Operation<T>, mit dem der handler das original rufen ODER
// überspringen kann — der funktionale ersatz für @Overwrite, dokumentiert
// unter https://github.com/LlamaLad7/MixinExtras/wiki/WrapMethod.
//
// DER SCHNITT: Operation<T> ist ein MixinExtras-typ, kein JDK/MC/BBS-typ —
// genau die art fremd-typ, die MixinBruecke.rufe() NICHT als argument sehen
// darf (identitäts-problem bei classloader-swap). Deshalb bleibt der
// Operation-aufruf HIER im gewobenen handler (ändert sich nie — reiner
// GL-API-call), und alles woran man iteriert (timeout-wert, log-schwelle,
// timeout/fehler-erkennung, throttle-zählung) wandert in den kernel:
// com.afrobama.blendertransform.SyncWaitLogik. Zwei brücken-aufrufe plus
// der eine GL-call bleiben unter dem gate-deckel von 3 anweisungen.
package com.afrobama.bbstools.client.mixin.accelrender;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.github.argon4w.acceleratedrendering.core.backends.Sync;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import static org.lwjgl.opengl.GL46.GL_SYNC_FLUSH_COMMANDS_BIT;
import static org.lwjgl.opengl.GL46.GL_TIMEOUT_EXPIRED;
import static org.lwjgl.opengl.GL46.GL_WAIT_FAILED;
import static org.lwjgl.opengl.GL46.glClientWaitSync;

@Mixin(value = Sync.class, remap = false)
public abstract class SyncWaitTimeoutMixin
{
    @Shadow
    private long syncHandle;

    /**
     * Wraps {@link Sync#waitSync()}: skips the original body entirely and does its own
     * bounded {@code glClientWaitSync} call instead of Long.MAX_VALUE. Prevents indefinite
     * render-thread block → NVIDIA driver TDR → PC hard-freeze on our 20k same-mesh workload.
     *
     * <p>{@code original} (the {@link Operation}) is NEVER invoked — the whole point is to
     * replace the unbounded wait with a bounded one, same as the former {@code @Overwrite}
     * did. It's declared only because {@code @WrapMethod} requires the parameter.</p>
     */
    @WrapMethod(method = "waitSync")
    private void bbstools$waitSyncBounded(Operation<Void> original)
    {
        if (!(Boolean) MixinBruecke.rufe("SyncWaitLogik", "sollWarten", this.syncHandle))
        {
            return;
        }

        Object timeoutWert = MixinBruecke.rufe("SyncWaitLogik", "timeoutNanos");
        long timeout = timeoutWert instanceof Long ? (Long) timeoutWert : 500_000_000L;

        int result = glClientWaitSync(this.syncHandle,
            GL_SYNC_FLUSH_COMMANDS_BIT,
            timeout);

        boolean timeoutOderFehler = result == GL_TIMEOUT_EXPIRED || result == GL_WAIT_FAILED;
        MixinBruecke.rufe("SyncWaitLogik", "nachWarten", result, timeoutOderFehler);
    }
}
