package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.opengl.GlBuffer;

/** 26.2's GlBuffer.handle() accessor; before 26.1 the GL name is a field (opened by compat.accesswidener). Injected into GlBuffer. */
public interface GlBufferAlt
{
    default int handle()
    {
        return ((GlBuffer) this).handle;
    }
}
