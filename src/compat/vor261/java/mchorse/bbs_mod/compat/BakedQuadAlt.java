package mchorse.bbs_mod.compat;

import mchorse.bbs_mod.compat.alt.MaterialInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;

/** 26.2's BakedQuad.materialInfo(), for the versions before 26.1. Injected into BakedQuad. */
public interface BakedQuadAlt
{
    default MaterialInfo materialInfo()
    {
        BakedQuad quad = (BakedQuad) this;

        return new MaterialInfo(quad.sprite(), quad.tintIndex(), quad.shade(), quad.lightEmission());
    }
}
