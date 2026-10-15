// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 3.6 v2: event-driven delta).
package com.blenderbridge.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.blenderbridge.world.BlockDeltaEvents;
import com.blenderbridge.world.BlockDeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Event-driven block-change signal for {@link BlockDeltaTracker} (task 3.6 v2) — replaces the
 * detection LATENCY of the budgeted poll (~1 sweep ≈ 1s) with same-tick reporting; the wire
 * format, batching and the addon consumer ({@code world_delta.py}) stay byte-identical.
 *
 * <p>⛔ BOOT-WINDOW CITIZEN (W8-B3, 2026-07-17): committed DORMANT — mixins weave at target
 * class-load, so this does NOTHING until the next game boot (deploy-fenster-doc, klasse 3;
 * same shipping pattern as {@link ModelPartChildrenAccessor}). Until then W7-B4's poll carries
 * the feature. The full activation plan (weave gate, runtime gate, poll-budget tuning) lives in
 * {@code com.blenderbridge.world.BlockDeltaEvents}' class doc — the bridge class both hooks
 * call into.</p>
 *
 * <h2>WHY TWO HOOKS (MEASURED, CFR 0.152 against this project's mapped jar, yarn 1.21.1+build.3)</h2>
 * <p>There is no single method every client-side block change funnels through:</p>
 * <ul>
 *   <li>{@code handleBlockUpdate} receives ALL server-driven changes ({@code onBlockUpdate}
 *       single + {@code onChunkDeltaUpdate} per-position multi) — but applies them via {@code
 *       super.setBlockState(...)}, an INVOKESPECIAL that bypasses the {@code ClientWorld}
 *       override, so hook 2 alone would miss every server change.</li>
 *   <li>{@code setBlockState(BlockPos,BlockState,II)Z} receives local prediction (place/break
 *       via {@code ClientPlayerInteractionManager}/{@code BlockItem}) and {@code
 *       processPendingUpdate} corrections — none of which pass {@code handleBlockUpdate}.</li>
 *   <li>Full-chunk (re)loads ({@code onChunkData}) pass NEITHER (sections deserialise
 *       wholesale) — that gap belongs to the poll sweep, which therefore gets tuned DOWN at
 *       activation, never off (see BlockDeltaEvents activation plan).</li>
 * </ul>
 *
 * <p>Both packet paths are forced onto the main thread ({@code NetworkThreadUtils
 * .forceMainThread}), prediction is main-thread → the bridge may use plain statics.
 * Double-report is impossible by construction: a predicted place notifies via hook 2 and
 * adopts the snapshot at flush; the server confirm then either short-circuits in {@code
 * handleBlockUpdate} (pending-update hit) or re-notifies a position whose snapshot already
 * matches — flush diffs it as unchanged. Notify is guard-and-enqueue only (a few static
 * reads), so the woven-but-disarmed cost per block update is near zero.</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientWorldBlockDeltaMixin
{
    /** Server-driven changes (single + chunk-delta packets). RETURN (not TAIL) covers every
     * exit; a pending-update short-circuit still notifies, which flush resolves as a no-op. */
    @Inject(method = "setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("RETURN"))
    private void blenderbridge$onHandleBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci)
    {
        MixinBruecke.rufe("BlockAenderungMeldenLogik", "geaendert", (ClientLevel) (Object) this, pos);
    }

    /** Local prediction + pending-update corrections. Only a TRUE return means the world
     * actually changed (vanilla returns false for same-state writes). */
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void blenderbridge$onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ())
        {
            MixinBruecke.rufe("BlockAenderungMeldenLogik", "geaendert", (ClientLevel) (Object) this, pos);
        }
    }
}
