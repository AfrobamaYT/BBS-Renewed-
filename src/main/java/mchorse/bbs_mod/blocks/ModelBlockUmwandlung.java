package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelBody;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.utils.PermissionUtils;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Alt + right click with a model block in hand turns the block under the crosshair into a model
 * block showing that same block, so a set becomes movable one poke at a time. This is the server's
 * half: the packet and the swap. Creative only and within reach, a builder's tool, not a way to lift
 * blocks out of a survival world. From BBS FS b4ba56e23 (Wemppy4/bbs-fs, MIT).
 */
public final class ModelBlockUmwandlung
{
    public static final Identifier PAKET = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "model_block_convert");

    private ModelBlockUmwandlung()
    {}

    public static void setup()
    {
        PayloadTypeRegistry.serverboundPlay().register(ServerNetwork.idFor(PAKET), ServerNetwork.BufPayload.codecFor(ServerNetwork.idFor(PAKET)));
        ServerPlayNetworking.registerGlobalReceiver(ServerNetwork.idFor(PAKET), (payload, context) ->
        {
            BlockPos pos = payload.asPacketByteBuf().readBlockPos();
            ServerPlayer player = context.player();

            if (!PermissionUtils.arePanelsAllowed(context.server(), player))
            {
                return;
            }

            context.server().execute(() ->
            {
                Level world = player.level();

                if (player.isCreative() && player.distanceToSqr(Vec3.atCenterOf(pos)) < 64D && world.mayInteract(player, pos))
                {
                    convert(world, pos);
                }
            });
        });
    }

    /** Whether {@link #convert} has anything to turn into a model block here */
    public static boolean canConvert(BlockState state)
    {
        return !state.isAir() && !(state.getBlock() instanceof ModelBlock);
    }

    /**
     * Turns the block at {@code pos} into a model block showing that same block: a block form with
     * its state, and a body taken from the block (light, sound, hardness, whether it is walked on,
     * its outline as the hitbox), so nothing changes until the user edits it. Neighbours are not
     * updated, so the other half of a door or a torch on its side stay as the set had them.
     */
    public static boolean convert(Level world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        if (!canConvert(state))
        {
            return false;
        }

        /* Everything read off the old block before it is gone */
        boolean waterlogged = state.getFluidState().is(Fluids.WATER);
        int light = state.getLightEmission();
        SoundType group = state.getSoundType();
        ModelBlockSound sound = ModelBlockSound.of(group);
        float hardness = state.getDestroySpeed(world, pos);
        VoxelShape outline = state.getShape(world, pos);
        boolean solid = !state.getCollisionShape(world, pos).isEmpty();
        boolean camera = !state.getVisualShape(world, pos, CollisionContext.empty()).isEmpty();

        BlockState modelState = BBSMod.MODEL_BLOCK.defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, waterlogged)
            .setValue(ModelBlock.LIGHT_LEVEL, light)
            .setValue(ModelBlock.SOUND, sound);

        if (!world.setBlock(pos, modelState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE) || !(world.getBlockEntity(pos) instanceof ModelBlockEntity model))
        {
            return false;
        }

        /* The water is the model block's now; left in the form it would be drawn twice */
        BlockForm form = new BlockForm();

        form.blockState.set(state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state);

        ModelProperties properties = model.getProperties();
        ModelBody body = properties.getBody();

        properties.setForm(form);
        body.setLightLevel(light);
        body.setSound(sound);
        body.setHardness(Math.max(hardness, 0F));
        body.setSolid(solid);
        body.setCameraCollision(camera);

        if (!outline.isEmpty())
        {
            AABB box = outline.bounds();

            if (box.minX != 0 || box.minY != 0 || box.minZ != 0 || box.maxX != 1 || box.maxY != 1 || box.maxZ != 1)
            {
                body.setHitboxMode(ModelBody.HitboxMode.MANUAL);
                body.getHitboxMin().set((float) box.minX, (float) box.minY, (float) box.minZ);
                body.getHitboxMax().set((float) box.maxX, (float) box.maxY, (float) box.maxZ);
            }
        }

        model.setChanged();
        world.sendBlockUpdated(pos, modelState, modelState, Block.UPDATE_CLIENTS);
        world.playSound(null, pos, group.getPlaceSound(), SoundSource.BLOCKS, (group.getVolume() + 1F) / 2F, group.getPitch() * 0.8F);

        return true;
    }
}
