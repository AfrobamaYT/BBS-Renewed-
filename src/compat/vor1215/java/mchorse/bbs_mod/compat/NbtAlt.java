package mchorse.bbs_mod.compat;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/**
 * TagParser.parseCompoundAsArgument von 1.21.5+ vor 1.21.5: liest eine Verbindung ab der
 * Leseposition, ohne das Ende der Eingabe zu verlangen (wie dort).
 */
public final class NbtAlt
{
    private NbtAlt()
    {}

    public static CompoundTag parseCompoundAsArgument(StringReader reader) throws CommandSyntaxException
    {
        return new TagParser(reader).readStruct();
    }
}
