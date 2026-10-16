package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.component.TypedEntityData;

public class ModelBlock extends Block implements EntityBlock, SimpleWaterloggedBlock
{
    /* Light and sound in the state, where the engine reads them; the block entity's body is their source (BBS FS 996eb0adc) */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT_LEVEL = net.minecraft.world.level.block.state.properties.IntegerProperty.create("light_level", 0, 15);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<ModelBlockSound> SOUND = net.minecraft.world.level.block.state.properties.EnumProperty.create("sound", ModelBlockSound.class);

    /**
     * Client side: whether the player is editing (dashboard open, or a model block in hand). While
     * editing, the outline grows to at least the full cube, so a block with a tiny or offset hitbox
     * stays easy to target. Always false on a dedicated server, which never targets blocks visually.
     */
    public static java.util.function.BooleanSupplier editingCheck = () -> false;

    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    public ModelBlock(Properties settings)
    {
        super(settings);

        this.registerDefaultState(defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, false)
            .setValue(LIGHT_LEVEL, 0)
            .setValue(SOUND, ModelBlockSound.STONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.WATERLOGGED, LIGHT_LEVEL, SOUND);
    }

    /** Pushes the body's light and sound from the block entity into the state; server side, the state syncs itself */
    public static void mirrorBlockState(Level world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        if (!(world.getBlockEntity(pos) instanceof ModelBlockEntity model) || !(state.getBlock() instanceof ModelBlock))
        {
            return;
        }

        mchorse.bbs_mod.blocks.entities.ModelBody body = model.getProperties().getBody();
        BlockState updated = state.setValue(LIGHT_LEVEL, body.getLightLevel()).setValue(SOUND, body.getSound());

        if (updated != state)
        {
            world.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    @Nullable
    private static ModelBlockEntity model(BlockGetter world, BlockPos pos)
    {
        return world.getBlockEntity(pos) instanceof ModelBlockEntity model ? model : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        ModelBlockEntity model = model(world, pos);
        VoxelShape shape = model == null ? Shapes.block() : model.getShape();

        /* Editing keeps the block targetable as at least a full cube; a cube hitbox is the block()
         * singleton, so the reference check skips the union. */
        return shape != Shapes.block() && editingCheck.getAsBoolean() ? Shapes.or(shape, Shapes.block()) : shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        ModelBlockEntity model = model(world, pos);

        return model != null && model.getProperties().getBody().isSolid() ? model.getShape() : Shapes.empty();
    }

    /** What the third person camera bumps into */
    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        ModelBlockEntity model = model(world, pos);

        return model != null && model.getProperties().getBody().isCameraCollision() ? model.getShape() : Shapes.empty();
    }

    @Override
    protected net.minecraft.world.level.block.SoundType getSoundType(BlockState state)
    {
        return state.getValue(SOUND).group;
    }

    /**
     * Vanilla's break speed, with the hardness from the block's body instead of the registration,
     * so every model block takes its own time to mine. Both sides run it from the synced entity.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos)
    {
        ModelBlockEntity model = model(world, pos);
        float hardness = model == null ? 0F : model.getProperties().getBody().getHardness();

        if (hardness <= 0F)
        {
            /* A whole break per tick: the instant break this block always had */
            return 1F;
        }

        return player.getDestroySpeed(state) / hardness / (player.hasCorrectToolForDrops(state) ? 30 : 100);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        /* A placed item may carry body data, already poured into the block entity: mirror it */
        if (!world.isClientSide())
        {
            mirrorBlockState(world, pos);
        }
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

            stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(modelBlock.getType(), modelBlock.saveCustomOnly(world.registryAccess())));

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
    public void playerDestroy(ServerLevel world, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool)
    {
        if (!player.getAbilities().instabuild)
        {
            if (be instanceof ModelBlockEntity model)
            {
                ItemStack stack = new ItemStack(this);

                stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(model.getType(), model.saveCustomOnly(world.registryAccess())));

                Containers.dropContents(world, pos, NonNullList.withSize(1, stack));
            }
        }

        super.playerDestroy(world, player, pos, state, be, tool);
    }
}
