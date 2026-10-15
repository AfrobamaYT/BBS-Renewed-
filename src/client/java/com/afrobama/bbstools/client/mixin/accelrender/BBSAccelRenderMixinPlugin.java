// Coded by Afrobama/Claude — Mixin plugin gating the AccelRender freeze
// fixes. Only applies if AcceleratedRendering mod is installed at runtime
// — without this, Mixin would abort the entire config when AccelRender
// classes are missing from classpath, breaking BBSTools for users without
// AccelRender.
//
// Why: 2026-05-17 deep-research identified two AccelRender bugs that
// cause PC hard-freeze in our 20k same-mesh crowd workload:
//   1. Sync.waitSync() uses Long.MAX_VALUE timeout → render thread blocks
//      indefinitely on ring-slot exhaustion → NVIDIA driver TDR fails →
//      full PC freeze. SyncWaitTimeoutMixin caps timeout at 500ms.
//   2. AcceleratedRingBuffers initializes sharingBuffer at 64 bytes →
//      power-of-2 cascade resize on first camera-pan triggers GPU sync
//      storm. AcceleratedRingBuffersInitialSizeMixin pre-sizes to 4 MB.
package com.afrobama.bbstools.client.mixin.accelrender;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class BBSAccelRenderMixinPlugin implements IMixinConfigPlugin
{
    private boolean accelRenderPresent;

    @Override
    public void onLoad(String mixinPackage)
    {
        /* CRITICAL: do NOT call Class.forName on the AccelRender target
         *  classes here — that forces them to load BEFORE Mixin can
         *  weave them, and Mixin then fails with "target was loaded
         *  too early". Pattern verified against BBSPhysicsModMixinPlugin
         *  which uses the same isModLoaded-only check successfully. */
        try
        {
            this.accelRenderPresent = net.fabricmc.loader.api.FabricLoader
                .getInstance()
                .isModLoaded("acceleratedrendering");
        }
        catch (Throwable t)
        {
            this.accelRenderPresent = false;
        }
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        return this.accelRenderPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

}
