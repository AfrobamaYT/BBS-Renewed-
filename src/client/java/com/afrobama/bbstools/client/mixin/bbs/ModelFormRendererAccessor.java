// Coded by Afrobama/Claude — Tier 1 fast-path accessor.
//
// Exposes ModelFormRenderer.animator + bones fields and the
// ensureAnimator / captureMatrices / getPose methods to the BBSTools
// fast-path mixin so we can capture post-animator bone state without
// touching BBS source.
//
// AW pattern is preferred over @Invoker for private methods, but our
// bbstools.accesswidener already documents the entries — this interface
// gives a compiled-class-stable API for the mixin to consume.
package com.afrobama.bbstools.client.mixin.bbs;

import java.util.function.Supplier;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ModelFormRenderer.class, remap = false)
public interface ModelFormRendererAccessor
{
    @Accessor("animator")
    IAnimator bbstools$getAnimator();

    @Accessor("bones")
    MatrixCache bbstools$getBones();

    @Invoker("ensureAnimator")
    void bbstools$ensureAnimator(float transition);

    @Invoker("captureMatrices")
    void bbstools$captureMatrices(ModelInstance model);

    @Invoker("getPose")
    Pose bbstools$getPose();

    /* Tier 1.5b — Coded by Afrobama/Claude. Invoker bridge to the
     * private renderModel(...) method so FastPathRenderer can call it
     * from outside the class without an AccessWidener entry (memory:
     * feedback_mixin_no_public_static — prefer mixin over public-bumping). */
    /* CML beta-2 added two trailing params to renderModel: a second MatrixStack (world) and a
     * FormRenderingContext. Signature verified against the jar. */
    @Invoker("renderModel")
    void bbstools$renderModel(
        IEntity target,
        Supplier<ShaderProgram> program,
        MatrixStack stack,
        ModelInstance model,
        int light,
        int overlay,
        Color color,
        boolean ui,
        StencilMap stencilMap,
        float transition,
        boolean renderEquipment,
        MatrixStack world,
        mchorse.bbs_mod.forms.renderers.FormRenderingContext renderContext);
}
