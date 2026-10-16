package com.afrobama.bbstools.kernel.logik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.utils.NbtSnapshotUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Logik hinter {@code WorldChunkMixin} — schreibt Blockaenderungen fuer die Aufzeichnung mit.
 *
 * <p>Nicht zu verwechseln mit {@code BlockAenderungMeldenLogik} auf der Client-Seite: die
 * meldet an die Blender-Bruecke, diese hier fuellt die Aufzeichnung.</p>
 *
 * <p>⚠️ DIE REIHENFOLGE IST DER GANZE WITZ. Das Blockobjekt wird bei HEAD abgeschrieben, der
 * Blockzustand erst bei RETURN:</p>
 * <ul>
 *   <li>Das Objekt muss VOR dem Ersetzen weg — {@code onStateReplaced} kippt spaeter im selben
 *       Aufruf den Kisteninhalt in die Welt und leert das Objekt; wer danach abschreibt,
 *       sichert eine leere Kiste.</li>
 *   <li>Der Zustand kommt aus dem RUECKGABEWERT, nicht aus {@code getBlockState} — beim
 *       Aufzeichnen haelt der Chunk laengst den neuen Zustand.</li>
 * </ul>
 *
 * <p>Die Weitergabe zwischen beiden Punkten macht das Mixin ueber MixinExtras
 * ({@code @Share LocalRef}). Das bleibt DORT: dieser Kernel kennt MixinExtras nicht, deshalb
 * gibt {@link #ersetztesBlockObjekt} den Wert zurueck, statt eine Referenz zu befuellen.</p>
 */
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
