package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class ModelBlock extends Block implements BlockEntityProvider
{
    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    public ModelBlock(Settings settings)
    {
        super(settings);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof ModelBlockEntity modelBlock)
        {
            ItemStack stack = new ItemStack(this);
            NbtCompound compound = new NbtCompound();

            compound.put("BlockEntityTag", modelBlock.createNbtWithId());
            stack.setNbt(compound);

            return stack;
        }

        return super.getPickStack(world, pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        if (world.isClient())
        {
            return validateTicker(type, BBSMod.MODEL_BLOCK_ENTITY, (theWorld, blockPos, blockState, blockEntity) -> blockEntity.tick(theWorld, blockPos, blockState));
        }

        return null;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new ModelBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (hand == Hand.MAIN_HAND)
        {
            if (player instanceof ServerPlayerEntity serverPlayer)
            {
                ServerNetwork.sendClickedModelBlock(serverPlayer, pos);
            }

            return ActionResult.SUCCESS;
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }
}