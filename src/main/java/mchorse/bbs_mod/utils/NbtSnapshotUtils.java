package mchorse.bbs_mod.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.6 {
/*import net.minecraft.util.ProblemReporter;
*///?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=1.21.6 {
/*import net.minecraft.nbt.CompoundTag;
*///?}

/**
 * Macht von einer Entity oder BlockEntity einen CompoundTag -- so wie es bis 1.21.1
 * ein einzelner Aufruf war.
 *
 * 26.2 hat das Speichern auf ValueOutput umgestellt: saveWithId und saveWithoutId geben
 * nichts mehr zurueck, sondern SCHREIBEN in einen ValueOutput. Wer wie BBS an mehreren
 * Stellen einen fertigen Tag braucht (Item-komponente, Undo-schnappschuss, Morph-daten),
 * muesste sonst ueberall dieselben drei Zeilen wiederholen. TagValueOutput ist die vom
 * Spiel mitgelieferte Bruecke; hier steht sie an einer Stelle.
 *
 * ProblemReporter.DISCARDING: derselbe Verzicht auf Meldungen, den die alten Methoden
 * hatten -- sie konnten gar nicht berichten. Wer Probleme sehen will, gibt einen eigenen
 * Reporter an die Aufrufstelle, nicht hierher.
 */
public class NbtSnapshotUtils
{
    /** Wie das alte BlockEntity.saveWithId(): Nutzdaten plus "id". */
    public static CompoundTag ofBlockEntity(BlockEntity entity, HolderLookup.Provider lookup)
    {
        //? if >=1.21.6 {
        /*TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);

        entity.saveWithId();

        return output.buildResult();
        *///?} else {
        return entity.saveWithId();
        //?}
    }

    /** Wie das alte Entity.saveWithoutId(new CompoundTag()): Nutzdaten ohne Typ. */
    public static CompoundTag ofEntity(Entity entity, HolderLookup.Provider lookup)
    {
        //? if >=1.21.6 {
        /*TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);

        entity.saveWithoutId(output);

        return output.buildResult();
        *///?} else {
        return entity.saveWithoutId(new CompoundTag());
        //?}
    }
}
