package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.properties.BlockStateProperty;
import mchorse.bbs_mod.forms.properties.ColorProperty;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

public class BlockForm extends Form
{
    public final BlockStateProperty blockState = new BlockStateProperty(this, "block_state", Blocks.AIR.getDefaultState());
    public final ColorProperty color = new ColorProperty(this, "color", Color.white());

    public BlockForm()
    {
        this.register(this.blockState);
        this.register(this.color);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return Registries.BLOCK.getId(this.blockState.get().getBlock()).toString();
    }
}