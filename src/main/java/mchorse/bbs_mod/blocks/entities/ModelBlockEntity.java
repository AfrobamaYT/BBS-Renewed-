package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.api.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;

public class ModelBlockEntity extends BlockEntity
{
    private ModelProperties properties = new ModelProperties();
    private IEntity entity = new StubEntity();

    private float lastYaw = Float.NaN;
    private float currentYaw = Float.NaN;

    public ModelBlockEntity(BlockPos pos, BlockState state)
    {
        super(BBSMod.MODEL_BLOCK_ENTITY, pos, state);
    }

    public String getName()
    {
        BlockPos pos = this.getBlockPos();
        Form form = this.getProperties().getForm();
        String s = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";

        if (form != null)
        {
            s += " " + form.getDisplayName();
        }

        return s;
    }

    public ModelProperties getProperties()
    {
        return this.properties;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void setLookYaw(float yaw)
    {
        this.lastYaw = yaw;
        this.currentYaw = yaw;
    }

    public float updateLookYawContinuous(float yaw)
    {
        if (Float.isNaN(this.currentYaw))
        {
            this.setLookYaw(yaw);

            return this.currentYaw;
        }

        float diff = yaw - this.lastYaw;

        while (diff > Math.PI) diff -= (float) (Math.PI * 2);
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);

        this.currentYaw += diff;
        this.lastYaw = yaw;

        return this.currentYaw;
    }

    public void resetLookYaw()
    {
        this.lastYaw = this.currentYaw = Float.NaN;
    }

    public void snapLookYawToBase(float lastYaw, float currentYaw)
    {
        this.lastYaw = lastYaw;
        this.currentYaw = currentYaw;
    }

    public void tick(Level world, BlockPos pos, BlockState state)
    {
        ModelBlockEntityUpdateCallback.EVENT.invoker().update(this);

        this.properties.getEquipment().apply(this.entity);
        this.entity.update();
        this.entity.setWorld(world);
        this.properties.update(this.entity);
    }

    /** The block's physical shape, from the body settings against the current form and transform */
    public net.minecraft.world.phys.shapes.VoxelShape getShape()
    {
        return this.properties.getBody().buildShape(this.properties.getForm(), this.properties.getTransform());
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /* 26.2: saveWithId schreibt jetzt in einen ValueOutput, statt einen CompoundTag
     * zurueckzugeben. TagValueOutput ist die vom spiel mitgelieferte bruecke -- hinein
     * schreiben, buildResult() liefert den tag. Gleiche daten wie vorher, nur der weg
     * ist ein anderer. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup)
    {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryLookup);

        this.saveWithId(output);

        return output.buildResult();
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);

        MapType data = this.properties.toData();

        DataStorageUtils.writeToValueOutput(output, "Properties", data);
    }

    @Override
    public void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);

        BaseType baseType = DataStorageUtils.readFromValueInput(input, "Properties");

        if (baseType instanceof MapType mapType)
        {
            this.properties.fromData(mapType);
        }
    }

    public void updateForm(MapType data, Level world)
    {
        this.properties.fromData(data);

        BlockPos pos = this.getBlockPos();

        /* Light and sound live in the block STATE, where the engine reads them; the body is their source */
        if (!world.isClientSide())
        {
            mchorse.bbs_mod.blocks.ModelBlock.mirrorBlockState(world, pos);
        }

        BlockState blockState = world.getBlockState(pos);

        world.sendBlockUpdated(pos, blockState, blockState, Block.UPDATE_CLIENTS);
        world.blockEntityChanged(pos);
    }
}