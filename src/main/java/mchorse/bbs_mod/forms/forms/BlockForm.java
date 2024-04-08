package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.properties.BlockStateProperty;
import net.minecraft.block.Blocks;

public class BlockForm extends Form
{
    public final BlockStateProperty blockState = new BlockStateProperty(this, "block_state", Blocks.AIR.getDefaultState());

    public BlockForm()
    {
        this.register(this.blockState);
    }
}