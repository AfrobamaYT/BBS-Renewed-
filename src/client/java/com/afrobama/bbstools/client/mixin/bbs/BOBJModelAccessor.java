// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin.bbs;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the private {@code armature}, {@code meshData} and
 * {@code vao} fields on {@link BOBJModel}.
 *
 * <p>Upstream already exposes equivalent {@code getArmature()},
 * {@code getMeshData()} and {@code getVao()} public getters, but we
 * route through {@code bbstools$} accessors so our batch renderer
 * never depends on upstream method names.
 */
@Mixin(value = BOBJModel.class, remap = false)
public interface BOBJModelAccessor
{
    @Accessor("armature")
    BOBJArmature bbstools$getArmature();
}
