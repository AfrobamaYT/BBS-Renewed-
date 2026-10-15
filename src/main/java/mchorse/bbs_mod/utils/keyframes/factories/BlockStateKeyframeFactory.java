package mchorse.bbs_mod.utils.keyframes.factories;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.apache.logging.log4j.LogManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

public class BlockStateKeyframeFactory implements IKeyframeFactory<BlockState>
{
    /** Datenversion von 1.21.1 — aus dieser Fassung stammen die Filme, die nach 26.x kommen. */
    private static final int DATEN_1_21_1 = 3955;

    /**
     * ⛔ NIE {@code null}: das naechste Speichern encodiert den Wert und stuerzt ab
     * (gemessen 2026-10-13, Autosave von Film "c9", NPE in BlockState.CODEC).
     *
     * <p>1.21 schrieb {@code {Name, Properties}}, 26.x liest nur noch {@code {id, properties}}
     * oder den nackten Namen. Alte Daten laufen darum durch Mojangs DataFixer
     * (BlockStateFieldNamesFix) — derselbe Weg, mit dem Minecraft alte Welten hebt.</p>
     */
    @Override
    public BlockState fromData(BaseType data)
    {
        Tag tag = DataStorageUtils.toNbt(data);
        DataResult<Pair<BlockState, Tag>> decode = BlockState.CODEC.decode(NbtOps.INSTANCE, tag);
        Optional<BlockState> result = decode.result().map(Pair::getFirst);

        if (result.isEmpty())
        {
            Dynamic<Tag> neu = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag),
                DATEN_1_21_1, SharedConstants.getCurrentVersion().getDataVersion().getVersion());

            result = BlockState.CODEC.parse(neu).result();
        }

        if (result.isEmpty())
        {
            LogManager.getLogger("bbs/blockstate").warn("Unreadable block state {}, replaced with air: {}", data, decode.error().map(DataResult.Error::message).orElse("?"));
        }

        return result.orElseGet(this::createEmpty);
    }

    @Override
    public BaseType toData(BlockState value)
    {
        Optional<Tag> result = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, value).result();

        return result.map(DataStorageUtils::fromNbt).orElse(null);
    }

    @Override
    public BlockState createEmpty()
    {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState copy(BlockState value)
    {
        return value;
    }

    @Override
    public BlockState interpolate(BlockState preA, BlockState a, BlockState b, BlockState postB, IInterp interpolation, float x)
    {
        return a;
    }
}