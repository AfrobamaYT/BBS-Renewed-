package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPipelineAlt;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineAltMixin implements RenderPipelineAlt
{
    @Unique
    private VertexFormat[] bbs$bindungen;
    @Unique
    private ColorTargetState bbs$farbe;
    @Unique
    private DepthStencilState bbs$tiefe;
    @Unique
    private List<BindGroupLayout> bbs$gruppen;

    @Override
    public void bbs$setAlt(VertexFormat[] bindungen, ColorTargetState farbe, DepthStencilState tiefe, List<BindGroupLayout> gruppen)
    {
        this.bbs$bindungen = bindungen;
        this.bbs$farbe = farbe;
        this.bbs$tiefe = tiefe;
        this.bbs$gruppen = gruppen;
    }

    @Override
    public VertexFormat[] bbs$bindungen()
    {
        return this.bbs$bindungen;
    }

    @Override
    public ColorTargetState bbs$farbe()
    {
        return this.bbs$farbe;
    }

    @Override
    public DepthStencilState bbs$tiefe()
    {
        return this.bbs$tiefe;
    }

    @Override
    public List<BindGroupLayout> bbs$gruppen()
    {
        return this.bbs$gruppen;
    }
}
