package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.VertexFormatAlt;
import mchorse.bbs_mod.compat.alt.VertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(VertexFormat.class)
public abstract class VertexFormatAltMixin implements VertexFormatAlt
{
    @Unique
    private List<VertexFormats.Attribut> bbs$attribute;
    @Unique
    private int bbs$groesse;
    @Unique
    private int bbs$stepRate;

    @Override
    public void bbs$setAlt(List<VertexFormats.Attribut> attribute, int groesse, int stepRate)
    {
        this.bbs$attribute = attribute;
        this.bbs$groesse = groesse;
        this.bbs$stepRate = stepRate;
    }

    @Override
    public List<VertexFormats.Attribut> bbs$attribute()
    {
        return this.bbs$attribute;
    }

    @Override
    public int bbs$groesse()
    {
        return this.bbs$groesse;
    }

    @Override
    public int getStepRate()
    {
        return this.bbs$stepRate;
    }
}
