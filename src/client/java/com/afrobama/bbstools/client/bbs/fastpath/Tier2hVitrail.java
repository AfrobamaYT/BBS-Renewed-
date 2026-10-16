package com.afrobama.bbstools.client.bbs.fastpath;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

/** Public access to the real native crowd pipeline for the optional Vitrail geometry door. */
public final class Tier2hVitrail
{
    private Tier2hVitrail() {}
    public static RenderPipeline pipeline() { return Tier2hNativePipeline.PIPELINE; }
}
