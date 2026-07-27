// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for BBS {@link BOBJModelVAO} internals.
 *
 * <p>Originally exposed only {@code dominantBonePerTriangle} for the
 * fracture mixin. Extended for the Veil-based replay batch renderer so
 * we can pull the underlying mesh {@link BOBJLoader.CompiledData}, the
 * bound {@link BOBJArmature} and the raw GL VAO handle without forcing
 * those fields public.
 *
 * <p>Note: {@code data}, {@code armature}, and the vertex/normal/uv
 * buffer ids on BBS' {@code BOBJModelVAO} are already declared public,
 * but routing them through {@code bbstools$} accessors gives us a
 * stable API surface that survives upstream visibility changes.
 */
@Mixin(value = BOBJModelVAO.class, remap = false)
public interface BOBJModelVAOAccessor
{
    @Accessor("dominantBonePerTriangle")
    int[] bbstools$getDominantBonePerTriangle();

    @Accessor("data")
    BOBJLoader.CompiledData bbstools$getData();

    @Accessor("armature")
    BOBJArmature bbstools$getArmature();

    @Accessor("vao")
    int bbstools$getVao();
}
