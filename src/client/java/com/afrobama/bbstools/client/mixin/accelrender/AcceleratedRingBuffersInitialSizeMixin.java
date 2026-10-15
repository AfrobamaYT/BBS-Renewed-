// Coded by Afrobama/Claude — Layer 2 fix for the AccelRender freeze
// chain. Enlarges the sharingBuffer initial size from 64 bytes to 4 MB
// so we avoid the power-of-2 cascade resize that triggers GPU sync
// storms on the first camera-pan with many same-mesh instances.
//
// THE PROBLEM (AccelRender AcceleratedRingBuffers.java:109):
//   this.sharingBuffer = new MappedBuffer(64L);
//
// Each sharing entry = 112 bytes (16 floats for Matrix4f transform +
// 12 floats for Matrix3f normal). At our scale of ~5000-20000 visible
// villagers per frame after pose-cache invalidation (camera-pan):
//   - 5000 instances × 112 B = 560 KB needed
//   - 20000 instances × 112 B = 2.24 MB needed
//
// Power-of-2 growth from 64 B = 13-16 reallocations PER FRAME. Each
// reallocation does:
//   - glNamedBufferStorage (new buffer allocation)
//   - glCopyNamedBufferSubData (copy old → new)
//   - glUnmap + glMap persistent-coherent (FULL GPU SYNC)
//   - glDeleteBuffers (old handle)
//
// Combined with Sync.waitSync(Long.MAX_VALUE) when ring slots run out
// (now patched by SyncWaitTimeoutMixin to 500ms), this previously
// caused PC hard-freeze on camera-pan. With Layer 1 (Sync timeout) the
// freeze is replaced with 1 FPS stutter; with Layer 2 (this mixin) we
// pre-grow to 4 MB so the cascade never happens — sustained 30-60 FPS
// possible even with thousands of same-mesh instances.
//
// SAFETY: 4 MB initial × 8 ring slots = 32 MB total. Tiny vs the
// 57 MB element buffer AccelRender already allocates per slot. The
// extra cost is one-time per slot; once allocated, no resize needed.
//
// EVIDENCE: deep-research 2026-05-17 (Agent 2 pipeline analysis)
// identified buffer cascade as the trigger mechanism. File references:
//   - AcceleratedRingBuffers.java:109 (the 64L literal)
//   - AcceleratedRingBuffers.java:211-213 (112 B/entry sizing)
//   - MutableBuffer.java:21-28 (the cascade resize code)
//   - MappedBuffer.java:58-65 (unmap/remap on grow)
package com.afrobama.bbstools.client.mixin.accelrender;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.AcceleratedRingBuffers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = AcceleratedRingBuffers.Buffers.class, remap = false)
public abstract class AcceleratedRingBuffersInitialSizeMixin
{
    /**
     * Hooks the {@code new MappedBuffer(64L)} call inside
     * {@link AcceleratedRingBuffers.Buffers#Buffers} and replaces the
     * 64-byte initial size with the kernel-computed value.
     *
     * Matches only the direct MappedBuffer constructor in the Buffers
     * ctor (line 109). Other pools (MeshUploaderPool, StagingBufferPool)
     * construct their MappedBuffers via different code paths and are
     * NOT affected by this mixin.
     */
    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/argon4w/acceleratedrendering/core/backends/buffers/MappedBuffer;<init>(J)V"
        ),
        index = 0
    )
    private long bbstools$enlargeSharingBuffer(long originalSize)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.AcceleratedRingBuffersInitialSizeLogik — siehe
         * MixinBruecke. Fallback = originalSize: kommt die brücke nicht durch, verhält sich
         * das spiel wie ohne diesen mixin statt mit einem kaputten wert weiterzulaufen. */
        Object r = MixinBruecke.rufe("AcceleratedRingBuffersInitialSizeLogik",
            "enlargeSharingBuffer", originalSize);
        return r instanceof Long ? (Long) r : originalSize;
    }

}
