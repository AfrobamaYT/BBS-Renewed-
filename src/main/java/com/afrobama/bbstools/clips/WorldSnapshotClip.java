package com.afrobama.bbstools.clips;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures or restores a block region through Minecraft's native structure format. */
public class WorldSnapshotClip extends ActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/snapshot-clip");

    public final ValueInt minX = new ValueInt("min_x", 0, -30000000, 30000000);
    public final ValueInt minY = new ValueInt("min_y", 0, -64, 320);
    public final ValueInt minZ = new ValueInt("min_z", 0, -30000000, 30000000);
    public final ValueInt maxX = new ValueInt("max_x", 0, -30000000, 30000000);
    public final ValueInt maxY = new ValueInt("max_y", 0, -64, 320);
    public final ValueInt maxZ = new ValueInt("max_z", 0, -30000000, 30000000);
    public final ValueString snapshotFile = new ValueString("snapshot_file", "");
    public final ValueBoolean capture = new ValueBoolean("capture", false);
    public final ValueBoolean restoreBlockEntities = new ValueBoolean("restore_block_entities", false);
    public final ValueBoolean diffOnly = new ValueBoolean("diff_only", true);

    public WorldSnapshotClip()
    {
        this.add(this.minX);
        this.add(this.minY);
        this.add(this.minZ);
        this.add(this.maxX);
        this.add(this.maxY);
        this.add(this.maxZ);
        this.add(this.snapshotFile);
        this.add(this.capture);
        this.add(this.restoreBlockEntities);
        this.add(this.diffOnly);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        ServerLevel world = (ServerLevel) player.level();
        try
        {
            WorldSnapshots.execute(world, this);
        }
        catch (Exception exception)
        {
            LOG.error("World snapshot failed in film {} at tick {}", film.getId(), tick, exception);
            Component message = Component.literal("World snapshot failed: " + exception.getMessage());
            for (net.minecraft.server.level.ServerPlayer viewer : world.players())
            {
                viewer.sendSystemMessage(message);
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new WorldSnapshotClip();
    }
}
