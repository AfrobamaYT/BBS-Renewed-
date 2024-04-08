package mchorse.bbs_mod.utils.keyframes.generic.factories;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.math.IInterpolation;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

import java.util.Optional;

public class BlockStateKeyframeFactory implements IGenericKeyframeFactory<BlockState>
{
    @Override
    public BlockState fromData(BaseType data)
    {
        DataResult<Pair<BlockState, NbtElement>> decode = BlockState.CODEC.decode(NbtOps.INSTANCE, DataStorageUtils.toNbt(data));
        Optional<Pair<BlockState, NbtElement>> result = decode.result();

        return result.map(Pair::getFirst).orElse(null);
    }

    @Override
    public BaseType toData(BlockState value)
    {
        Optional<NbtElement> result = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, value).result();

        return result.map(DataStorageUtils::fromNbt).orElse(null);
    }

    @Override
    public BlockState copy(BlockState value)
    {
        return value;
    }

    @Override
    public BlockState interpolate(BlockState a, BlockState b, IInterpolation interpolation, float x)
    {
        return b;
    }
}