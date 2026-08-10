package com.afrobama.bbstools.kernel.logik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.utils.NbtSnapshotUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;


public final class BlockAufzeichnungLogik
{
    private BlockAufzeichnungLogik() {}

    /**
     * {@code LevelChunk.setBlockState} @HEAD — das gleich verschwindende Blockobjekt sichern.
     *
     * @param chunkObjekt der gewobene {@code this}; kommt als Argument, weil der Cast ihn braucht
     * @return die Momentaufnahme, oder {@code null} wenn dort kein Blockobjekt sass
     */
    public static CompoundTag ersetztesBlockObjekt(Object chunkObjekt, BlockPos pos)
    {
        if (!(chunkObjekt instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel welt))
        {
            return null;
        }

        BlockEntity blockObjekt = chunk.getBlockEntity(pos);

        return blockObjekt == null ? null : NbtSnapshotUtils.ofBlockEntity(blockObjekt, welt.registryAccess());
    }

    /**
     * {@code LevelChunk.setBlockState} @RETURN — die Aenderung in die Aufzeichnung legen.
     *
     * @param vorher der Rueckgabewert des Originals: der Zustand VOR der Aenderung
     * @param objekt was {@link #ersetztesBlockObjekt} bei HEAD gesichert hat, oft {@code null}
     */
    public static void blockGeaendert(Object chunkObjekt, BlockPos pos, BlockState vorher, CompoundTag objekt)
    {
        if (vorher == null || !(chunkObjekt instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel))
        {
            return;
        }

        BBSMod.getActions().changedBlock(pos, vorher, objekt);
    }
}
