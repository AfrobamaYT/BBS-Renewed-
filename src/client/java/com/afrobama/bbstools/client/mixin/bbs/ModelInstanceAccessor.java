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
    /* AUTONOMIE 2026-07-27: der texture-accessor ist ENTFERNT. fs haelt die textur nicht
     * mehr als feld auf ModelInstance, sondern in config (ValueLink) — und bietet dafuer
     * die echte methode ModelInstance.getTexture(), die zusaetzlich auf baseTexture
     * zurueckfaellt. Der @Accessor fand sein feld nicht und liess den ganzen mixin
     * scheitern (dann fehlten auch getModel/getVaos). Kein aufrufer hatte ihn benutzt. */

    @Accessor("vaos")
    Map<ModelGroup, ModelVAO> bbstools$getVaos();
}
