// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin.bbs;

import java.util.List;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@link BOBJArmature}'s bone matrices and ordered bone
 * list. All target fields are already declared public on upstream BBS;
 * we wrap them so the batch renderer has a single stable read API.
 *
 * <p>{@code matrices} is the per-bone {@link Matrix4f} array that
 * {@link BOBJArmature#setupMatrices()} fills each frame — what the
 * Veil instanced renderer uploads as bone uniforms.
 * {@code orderedBones} drives our bone-count computation.
 */
@Mixin(value = BOBJArmature.class, remap = false)
public interface BOBJArmatureAccessor
{
    @Accessor("matrices")
    Matrix4f[] bbstools$getMatrices();

    @Accessor("orderedBones")
    List<BOBJBone> bbstools$getOrderedBones();
}
