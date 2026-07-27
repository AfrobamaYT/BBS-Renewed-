// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin.bbs;

import java.util.Map;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.resources.Link;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only accessor for {@link ModelInstance} so the Veil batch
 * renderer can grab the bound {@link IModel} (usually a BOBJModel),
 * its texture {@link Link} and the per-group VAO map without making
 * fields public.
 *
 * <p>Fields are already public on upstream BBS — accessor wrapping
 * gives a stable API for our renderer if upstream tightens visibility.
 * The armature is derived from {@code (BOBJModel) bbstools$getModel()}.
 */
@Mixin(value = ModelInstance.class, remap = false)
public interface ModelInstanceAccessor
{
    @Accessor("model")
    IModel bbstools$getModel();

    @Accessor("texture")
    Link bbstools$getTexture();

    @Accessor("vaos")
    Map<ModelGroup, ModelVAO> bbstools$getVaos();
}
