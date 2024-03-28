package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ModelBlockEntity extends BlockEntity
{
    private Form form;
    private final Transform transform = new Transform();
    private boolean shadow;
    private IEntity entity = new StubEntity();

    public ModelBlockEntity(BlockPos pos, BlockState state)
    {
        super(BBSMod.MODEL_BLOCK_ENTITY, pos, state);
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public Form getForm()
    {
        return this.form;
    }

    public void setForm(Form form)
    {
        this.form = form;
    }

    public Transform getTransform()
    {
        return this.transform;
    }

    public boolean getShadow()
    {
        return this.shadow;
    }

    public void setShadow(boolean shadow)
    {
        this.shadow = shadow;
    }

    public void tick(World world, BlockPos pos, BlockState state)
    {
        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket()
    {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt()
    {
        return createNbt();
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        if (this.form != null)
        {
            DataStorageUtils.writeToNbtCompound(nbt, "Form", FormUtils.toData(this.form));
        }

        DataStorageUtils.writeToNbtCompound(nbt, "Transform", this.transform.toData());
        nbt.putBoolean("Shadow", this.shadow);
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        if (nbt.contains("Form"))
        {
            BaseType baseType = DataStorageUtils.readFromNbtCompound(nbt, "Form");

            if (baseType instanceof MapType)
            {
                this.form = BBSMod.getForms().fromData(baseType.asMap());
            }
        }

        BaseType baseType = DataStorageUtils.readFromNbtCompound(nbt, "Transform");

        if (baseType instanceof MapType)
        {
            this.transform.fromData(baseType.asMap());
        }

        this.shadow = nbt.getBoolean("Shadow");
    }

    public void updateForm(Form form, MapType transform, boolean shadow, World world)
    {
        this.form = FormUtils.copy(form);
        this.transform.fromData(transform);
        this.shadow = shadow;

        BlockPos pos = this.getPos();
        BlockState blockState = world.getBlockState(pos);

        world.updateListeners(pos, blockState, blockState, Block.NOTIFY_LISTENERS);
    }
}