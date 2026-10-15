package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.mixin.OverlayTextureAltMixin;

/** vor1215: 1.21.11s OverlayTexture.getTextureView() ueber die DynamicTexture der Ueberlagerung. */
public interface OverlayTextureAlt
{
    default GpuTextureView getTextureView()
    {
        return ((OverlayTextureAltMixin) this).bbs$textur().getTextureView();
    }
}
