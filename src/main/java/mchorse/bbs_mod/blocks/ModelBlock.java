package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
//? if >=26.3 {
/*import net.minecraft.server.level.ServerLevel;
*///?}
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.component.CustomData;

public class ModelBlock extends Block implements EntityBlock, SimpleWaterloggedBlock
{
    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    public ModelBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return this.defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).is(Fluids.WATER));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof ModelBlockEntity modelBlock)
        {
            ItemStack stack = new ItemStack(this);

            //? if >=1.21.9 {
            /*stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(modelBlock.saveWithId(world.registryAccess())));
            *///?} else {
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(modelBlock.saveWithId(world.registryAccess())));
            //?}

            return stack;
        }

        return super.getCloneItemStack(world, pos, state, includeData);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state)
    {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        if (world.isClientSide())
        {
            return validateTicker(type, BBSMod.MODEL_BLOCK_ENTITY, (theWorld, blockPos, blockState, blockEntity) -> blockEntity.tick(theWorld, blockPos, blockState));
        }

        return null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ModelBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            ServerNetwork.sendClickedModelBlock(serverPlayer, pos);
        }

        return InteractionResult.SUCCESS;
    }

    /* Waterloggable implementation */

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    //? if >=26.3 {
    /*public void playerDestroy(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool)
    *///?} else {
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool)
    //?}
    {
        //? if >=26.3 {
        /*if (!player.getAbilities().instabuild)
        *///?} else {
        if (!world.isClientSide() && !player.getAbilities().instabuild)
        //?}
        {
            if (be instanceof ModelBlockEntity model)
            {
                ItemStack stack = new ItemStack(this);

                //? if >=1.21.9 {
                /*stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(model.saveWithId(world.registryAccess())));
                *///?} else {
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(model.saveWithId(world.registryAccess())));
                //?}

                Containers.dropContents(world, pos, NonNullList.withSize(1, stack));
            }
        }

        super.playerDestroy(world, player, pos, state, be, tool);
    }
}
