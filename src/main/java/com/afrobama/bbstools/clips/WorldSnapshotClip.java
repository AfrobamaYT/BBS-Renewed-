package com.afrobama.bbstools.clips;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.CommandSourceUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restores a saved vanilla structure snapshot when this server action activates. */
public class WorldSnapshotClip extends ActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/snapshot-clip");

    public final ValueInt minX = new ValueInt("min_x", 0, -30000000, 30000000);
    public final ValueInt minY = new ValueInt("min_y", 0, -64, 320);
    public final ValueInt minZ = new ValueInt("min_z", 0, -30000000, 30000000);
    public final ValueInt maxX = new ValueInt("max_x", 0, -30000000, 30000000);
    public final ValueInt maxY = new ValueInt("max_y", 0, -64, 320);
    public final ValueInt maxZ = new ValueInt("max_z", 0, -30000000, 30000000);
    /** Saved structure ID; captured through vanilla structure blocks. */
    public final ValueString snapshotFile = new ValueString("snapshot_file", "");
    public final ValueBoolean restoreBlockEntities = new ValueBoolean("restore_block_entities", false);
    public final ValueBoolean diffOnly = new ValueBoolean("diff_only", true);
    private String lastFailure = "";

    public WorldSnapshotClip()
    {
        this.add(this.minX); this.add(this.minY); this.add(this.minZ);
        this.add(this.maxX); this.add(this.maxY); this.add(this.maxZ);
        this.add(this.snapshotFile); this.add(this.restoreBlockEntities); this.add(this.diffOnly);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        LivingEntity target = actor == null ? player : actor;
        if (target == null || !(target.level() instanceof ServerLevel level))
            throw new IllegalArgumentException("World snapshot action requires a server actor");
        CommandSourceStack source = target instanceof ServerPlayer serverPlayer
            ? serverPlayer.createCommandSourceStack() : CommandSourceUtils.of(target, level);
        try
        {
            WorldSnapshots.restore(source, this.snapshotFile.get(),
                new BlockPos(this.minX.get(), this.minY.get(), this.minZ.get()),
                new BlockPos(this.maxX.get(), this.maxY.get(), this.maxZ.get()),
                this.restoreBlockEntities.get(), this.diffOnly.get());
            this.lastFailure = "";
        }
        catch (RuntimeException failure)
        {
            String error = "World snapshot failed: " + failure.getMessage();
            if (!error.equals(this.lastFailure))
            {
                this.lastFailure = error;
                Component message = Component.literal(error);
                source.sendFailure(message);
                for (ServerPlayer viewer : level.players())
                    if (viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) viewer.sendSystemMessage(message);
                LOG.warn("World snapshot {} failed in film {} at tick {}", this.snapshotFile.get(), film == null ? "" : film.getId(), tick, failure);
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new WorldSnapshotClip();
    }
}
