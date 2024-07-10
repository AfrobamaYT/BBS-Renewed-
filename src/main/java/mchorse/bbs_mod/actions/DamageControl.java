package mchorse.bbs_mod.actions;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class DamageControl
{
    private List<BlockCapture> blocks = new ArrayList<>();
    private List<Entity> entities = new ArrayList<>();

    private ServerWorld world;

    public DamageControl(ServerWorld world)
    {
        this.world = world;
    }

    public void addBlock(BlockPos pos, BlockState state)
    {
        for (int i = 0; i < this.blocks.size(); i++)
        {
            BlockCapture blockCapture = this.blocks.get(i);

            if (blockCapture.pos.equals(pos))
            {
                return;
            }
        }

        this.blocks.add(new BlockCapture(pos, state));
    }

    public void addEntity(Entity entity)
    {
        this.entities.add(entity);
    }

    public void restore()
    {
        for (BlockCapture block : this.blocks)
        {
            this.world.setBlockState(block.pos, block.lastState, 2);
        }

        for (Entity entity : this.entities)
        {
            if (!entity.isRemoved())
            {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        this.blocks.clear();
        this.entities.clear();
    }

    private static class BlockCapture
    {
        public BlockPos pos;
        public BlockState lastState;

        public BlockCapture(BlockPos pos, BlockState lastState)
        {
            this.pos = pos;
            this.lastState = lastState;
        }
    }
}