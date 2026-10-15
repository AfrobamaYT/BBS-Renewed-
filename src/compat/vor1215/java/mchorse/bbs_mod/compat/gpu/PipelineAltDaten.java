package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPipelineAlt;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;

import java.util.List;

/** Die 26.2-Beschreibung einer Pipeline vor 1.21.5 (gesetzt von RenderPipelineBuilderAltMixin). */
public abstract class PipelineAltDaten implements RenderPipelineAlt
{
    private VertexFormat[] bindungen;
    private ColorTargetState farbe;
    private DepthStencilState tiefe;
    private List<BindGroupLayout> gruppen;

    @Override
    public void bbs$setAlt(VertexFormat[] bindungen, ColorTargetState farbe, DepthStencilState tiefe, List<BindGroupLayout> gruppen)
    {
        this.bindungen = bindungen;
        this.farbe = farbe;
        this.tiefe = tiefe;
        this.gruppen = gruppen;
    }

    @Override public VertexFormat[] bbs$bindungen() { return this.bindungen; }
    @Override public ColorTargetState bbs$farbe() { return this.farbe; }
    @Override public DepthStencilState bbs$tiefe() { return this.tiefe; }
    @Override public List<BindGroupLayout> bbs$gruppen() { return this.gruppen; }
}
