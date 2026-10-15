package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;

/**
 * 26.2-Namen an 1.21.4s RenderType: primitiveTopology() ist mode(); pipeline() gibt es vor
 * 1.21.6 nicht und wird fuer die Block-Gegenstandsebenen (ItemBlockRenderTypes.getRenderType)
 * auf die Pipelines der Schicht abgebildet. Andere Ebenen scheitern laut. Injiziert.
 */
public interface RenderTypeAlt
{
    default VertexFormat.Mode primitiveTopology()
    {
        return ((RenderType) this).mode();
    }

    default RenderPipeline pipeline()
    {
        RenderType ebene = (RenderType) this;

        if (ebene == Sheets.translucentItemSheet())
        {
            return RenderPipelines.ENTITY_TRANSLUCENT;
        }

        if (ebene == Sheets.cutoutBlockSheet() || ebene == Sheets.solidBlockSheet())
        {
            return RenderPipelines.ENTITY_CUTOUT;
        }

        throw new IllegalArgumentException("Keine Pipeline vor 1.21.5 fuer " + ebene);
    }
}
