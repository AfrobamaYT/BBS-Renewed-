package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
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
    private Properties properties = new Properties();
    private IEntity entity = new StubEntity();

    public ModelBlockEntity(BlockPos pos, BlockState state)
    {
        super(BBSMod.MODEL_BLOCK_ENTITY, pos, state);
    }

    public Properties getProperties()
    {
        return this.properties;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void tick(World world, BlockPos pos, BlockState state)
    {
        ModelBlockEntityUpdateCallback.EVENT.invoker().update(this);

        this.properties.update(this.entity);
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

        DataStorageUtils.writeToNbtCompound(nbt, "Properties", this.properties.toData());
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        BaseType baseType = DataStorageUtils.readFromNbtCompound(nbt, "Properties");

        if (baseType instanceof MapType)
        {
            this.properties.fromData(baseType.asMap());
        }
    }

    public void updateForm(MapType data, World world)
    {
        this.properties.fromData(data);

        BlockPos pos = this.getPos();
        BlockState blockState = world.getBlockState(pos);

        world.updateListeners(pos, blockState, blockState, Block.NOTIFY_LISTENERS);
    }

    public static class Properties implements IMapSerializable
    {
        private Form form;
        private final Transform transform = new Transform();
        private final Transform transformThirdPerson = new Transform();
        private final Transform transformInventory = new Transform();
        private final Transform transformFirstPerson = new Transform();
        private boolean shadow;

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

        public Transform getTransformThirdPerson()
        {
            return this.transformThirdPerson;
        }

        public Transform getTransformInventory()
        {
            return this.transformInventory;
        }

        public Transform getTransformFirstPerson()
        {
            return this.transformFirstPerson;
        }

        public boolean getShadow()
        {
            return this.shadow;
        }

        public void setShadow(boolean shadow)
        {
            this.shadow = shadow;
        }

        @Override
        public void fromData(MapType data)
        {
            this.form = FormUtils.fromData(data.getMap("form"));
            this.transform.fromData(data.getMap("transform"));
            this.transformThirdPerson.fromData(data.getMap("transformThirdPerson"));
            this.transformInventory.fromData(data.getMap("transformInventory"));
            this.transformFirstPerson.fromData(data.getMap("transformFirstPerson"));
            this.shadow = data.getBool("shadow");
        }

        @Override
        public void toData(MapType data)
        {
            data.put("form", FormUtils.toData(this.form));
            data.put("transform", this.transform.toData());
            data.put("transformThirdPerson", this.transformThirdPerson.toData());
            data.put("transformInventory", this.transformInventory.toData());
            data.put("transformFirstPerson", this.transformFirstPerson.toData());
            data.putBool("shadow", this.shadow);
        }

        public void update(IEntity entity)
        {
            if (this.form != null)
            {
                this.form.update(entity);
            }
        }
    }
}