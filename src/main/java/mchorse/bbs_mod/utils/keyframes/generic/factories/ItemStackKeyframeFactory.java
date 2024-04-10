package mchorse.bbs_mod.utils.keyframes.generic.factories;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.math.IInterpolation;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

import java.util.Optional;

public class ItemStackKeyframeFactory implements IGenericKeyframeFactory<ItemStack>
{
    @Override
    public ItemStack fromData(BaseType data)
    {
        DataResult<Pair<ItemStack, NbtElement>> decode = ItemStack.CODEC.decode(NbtOps.INSTANCE, DataStorageUtils.toNbt(data));
        Optional<Pair<ItemStack, NbtElement>> result = decode.result();

        return result.map(Pair::getFirst).orElse(null);
    }

    @Override
    public BaseType toData(ItemStack value)
    {
        Optional<NbtElement> result = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, value).result();

        return result.map(DataStorageUtils::fromNbt).orElse(null);
    }

    @Override
    public ItemStack copy(ItemStack value)
    {
        return value.copy();
    }

    @Override
    public ItemStack interpolate(ItemStack a, ItemStack b, IInterpolation interpolation, float x)
    {
        return b;
    }
}