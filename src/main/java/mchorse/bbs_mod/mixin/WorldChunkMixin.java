package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class WorldChunkMixin
{
    /* Every World.setBlockState overload funnels into this single, non-overloaded chunk method, so it is the
     * one place that catches all block changes - including World.breakBlock, which calls the four-argument
     * setBlockState directly. Targeting a method that is not overloaded also sidesteps Sinytra Connector's
     * overload mis-resolution under NeoForge.
     *
     * The replaced block entity is serialized to NBT at HEAD, before the block is replaced: onStateReplaced
     * (run later in this method) drops container contents into the world and clears the block entity, so
     * serializing any later would capture empty data. The replaced block state, on the other hand, is read
     * from the return value rather than from getBlockState - by the time the change is recorded the chunk
     * already holds the new state, and on Connector the injection even lands after the section write. */
    private static final String SET_BLOCK_STATE = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;";

    @Inject(method = SET_BLOCK_STATE, at = @At("HEAD"))
    private void captureReplacedBlockEntity(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> info, @Share("replaced") LocalRef<CompoundTag> replaced)
    {
        /* Die Weitergabe zwischen HEAD und RETURN bleibt hier: der Kernel kennt MixinExtras nicht. */
        replaced.set((CompoundTag) KernelBruecke.rufe("BlockAufzeichnungLogik", "ersetztesBlockObjekt", this, pos));
    }

    @Inject(method = SET_BLOCK_STATE, at = @At("RETURN"))
    private void recordChangedBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> info, @Share("replaced") LocalRef<CompoundTag> replaced)
    {
        KernelBruecke.rufe("BlockAufzeichnungLogik", "blockGeaendert", this, pos, info.getReturnValue(), replaced.get());
    }
}
