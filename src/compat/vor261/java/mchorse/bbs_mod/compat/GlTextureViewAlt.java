package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;

/** 26.2's GlTextureView.glId(): the GL name of the viewed texture. Injected into GlTextureView. */
public interface GlTextureViewAlt
{
    default int glId()
    {
        return ((GlTexture) ((GlTextureView) this).texture()).glId();
    }
}
