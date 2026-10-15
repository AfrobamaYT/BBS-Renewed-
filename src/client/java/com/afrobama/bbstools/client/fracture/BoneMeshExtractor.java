package com.afrobama.bbstools.client.fracture;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pulls per-bone vertex sets and world-space transforms out of a BBS
 * actor's BOBJ-model. Each {@link BoneFragment} returned describes one
 * bone in enough detail for {@link FractureSpawner} to build a
 * convex-hull rigidbody at the bone's current pose.
 *
 * <p><b>Vertex partitioning:</b> BOBJ stores vertices in one merged
 * {@code float[]} array. Each vertex has up to 4 bone influences with
 * weights. We treat the highest-weighted bone as the "owner" of that
 * vertex — vertices with weight=1 to one bone end up cleanly in that
 * bone's fragment; soft-skinned vertices get assigned to the dominant
 * bone (and the visual seam at bone boundaries is acceptable for
 * fracture purposes).</p>
 *
 * <p><b>World transform:</b> {@code BOBJArmature.matrices[bone.index]}
 * is the bone's world-space transform after
 * {@code armature.setupMatrices()} has been called for the current
 * frame. This matrix already includes the actor's pose, current
 * animation, and the bone's local transform — exactly what we need to
 * place the physics body where the bone visually is at the trigger
 * tick. The actor's world-position offset must still be added on top
 * (BBS armature matrices are model-local; the actor is at some
 * world position).</p>
 *
 * <p><b>Failure modes:</b> non-BOBJ models (cubic/glTF) return an
 * empty list — fracture is BOBJ-only. Models without a loaded
 * armature also return empty. Callers should handle the empty case
 * by skipping the fracture (the actor stays whole).</p>
 */
public final class BoneMeshExtractor
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/extractor");

    /**
     * Per-bone fracture data. Vertices are in BOBJ model-local space
     * (NOT world-space) — combine with {@link #worldMatrix} and the
     * actor's world position to spawn a physics body in the right
     * place.
     */
    public static final class BoneFragment
    {
        public final int boneIndex;
        public final String boneName;
        public final List<Vector3f> verticesLocal;
        /** {@code armature.matrices[bone.index]} at fracture time —
         *  the SKINNING matrix (currentBoneTransform * invBoneMat). */
        public final Matrix4f worldMatrix;
        /** {@code bone.boneMat} — the bind-pose bone matrix
         *  (model-local space, at rest). Its translation part is the
         *  bone's bind-pose pivot which we need for the matrix
         *  override math to avoid double-counting. */
        public final Matrix4f bindBoneMatrix;
        /** Parent bone's index in the armature, or -1 if this bone has
         *  no parent (root bone). Used by the joint-constraint
         *  ragdoll path to wire bind-pose distance constraints. */
        public final int parentBoneIndex;
        /** Bind-pose distance from this bone's pivot to parent's
         *  pivot. The constraint solver tries to maintain this
         *  distance during physics integration so connected bones
         *  visually stay together while still tumbling. 0 if no
         *  parent. */
        public final float bindDistanceToParent;

        public BoneFragment(int boneIndex, String boneName,
                            List<Vector3f> verticesLocal,
                            Matrix4f worldMatrix,
                            Matrix4f bindBoneMatrix,
                            int parentBoneIndex,
                            float bindDistanceToParent)
        {
            this.boneIndex = boneIndex;
            this.boneName = boneName;
            this.verticesLocal = verticesLocal;
            this.worldMatrix = worldMatrix;
            this.bindBoneMatrix = bindBoneMatrix;
            this.parentBoneIndex = parentBoneIndex;
            this.bindDistanceToParent = bindDistanceToParent;
        }

        public boolean isEmpty()
        {
            return this.verticesLocal == null || this.verticesLocal.isEmpty();
        }
    }

    private BoneMeshExtractor()
    {
    }

    /**
     * Extract every bone of the given actor's BOBJ model. Bones with
     * zero vertices (purely structural, no mesh) are filtered out.
     */
    public static List<BoneFragment> extract(IEntity actor)
    {
        if (actor == null)
        {
            LOG.debug("[FRACTURE-DIAG] extract: actor is null");
            return Collections.emptyList();
        }

        Form form = actor.getForm();
        LOG.debug("[FRACTURE-DIAG] extract: form = {}", form == null ? "NULL" : form.getClass().getSimpleName());

        if (!(form instanceof ModelForm modelForm))
        {
            LOG.warn("[FRACTURE-DIAG] extract aborted — form is not a ModelForm (got {})",
                form == null ? "null" : form.getClass().getName());
            return Collections.emptyList();
        }
        LOG.debug("[FRACTURE-DIAG] extract: model id = '{}'", modelForm.model.get());

        ModelInstance instance;
        try
        {
            instance = BBSModClient.getModels().getModel(modelForm.model.get());
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to resolve ModelInstance for fracture: {}", t.toString());
            return Collections.emptyList();
        }

        if (instance == null || !(instance.model instanceof BOBJModel bobj))
        {
            LOG.warn("[FRACTURE-DIAG] extract aborted — instance={}, model class={} (need BOBJModel)",
                instance == null ? "null" : "ok",
                instance == null || instance.model == null ? "null" : instance.model.getClass().getSimpleName());
            return Collections.emptyList();
        }

        BOBJArmature armature = bobj.getArmature();
        if (armature == null || armature.orderedBones == null || armature.orderedBones.isEmpty())
        {
            LOG.warn("[FRACTURE-DIAG] extract aborted — armature={}, orderedBones={}",
                armature == null ? "null" : "ok",
                armature == null || armature.orderedBones == null ? "null" : "size=" + armature.orderedBones.size());
            return Collections.emptyList();
        }
        LOG.debug("[FRACTURE-DIAG] extract: armature has {} bones", armature.orderedBones.size());

        /* Make sure matrices[] are populated for the current pose. */
        try
        {
            armature.setupMatrices();
        }
        catch (Throwable t)
        {
            LOG.warn("setupMatrices failed: {}", t.toString());
            return Collections.emptyList();
        }

        BOBJLoader.CompiledData mesh = bobj.getMeshData();
        if (mesh == null || mesh.posData == null || mesh.boneIndexData == null
            || mesh.weightData == null)
        {
            LOG.warn("[FRACTURE-DIAG] extract aborted — compiled mesh data missing/incomplete (mesh={}, posData={}, boneIdx={}, weights={})",
                mesh == null ? "null" : "ok",
                mesh == null || mesh.posData == null ? "null" : "len=" + mesh.posData.length,
                mesh == null || mesh.boneIndexData == null ? "null" : "len=" + mesh.boneIndexData.length,
                mesh == null || mesh.weightData == null ? "null" : "len=" + mesh.weightData.length);
            return Collections.emptyList();
        }
        LOG.debug("[FRACTURE-DIAG] extract: mesh has {} vertices, {} bone-index slots, {} weight slots",
            mesh.posData.length / 3, mesh.boneIndexData.length, mesh.weightData.length);

        List<BoneFragment> fragments = new ArrayList<>(armature.orderedBones.size());
        int skippedEmpty = 0;

        for (BOBJBone bone : armature.orderedBones)
        {
            int idx = bone.index;
            if (idx < 0) continue;

            Matrix4f world = (idx < armature.matrices.length) ? new Matrix4f(armature.matrices[idx]) : new Matrix4f();
            Matrix4f bindBoneMat = bone.boneMat == null ? new Matrix4f() : new Matrix4f(bone.boneMat);
            List<Vector3f> verts = collectDominantVertices(idx, mesh);

            if (verts.isEmpty())
            {
                skippedEmpty++;
                continue;
            }

            /* Parent linkage for the ragdoll joint solver: the bone's
             *  parentBone reference (if any) gives us the bind-pose
             *  pivot of the parent. Bind-distance is the rest length
             *  the constraint should preserve. */
            int parentIdx = -1;
            float bindDist = 0F;
            if (bone.parentBone != null && bone.parentBone.boneMat != null && bone.boneMat != null)
            {
                parentIdx = bone.parentBone.index;
                float dx = bone.boneMat.m30() - bone.parentBone.boneMat.m30();
                float dy = bone.boneMat.m31() - bone.parentBone.boneMat.m31();
                float dz = bone.boneMat.m32() - bone.parentBone.boneMat.m32();
                bindDist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }

            fragments.add(new BoneFragment(idx, bone.name, verts, world, bindBoneMat,
                parentIdx, bindDist));
        }

        LOG.debug("[FRACTURE-DIAG] extract: produced {} fragments (skipped {} empty bones)",
            fragments.size(), skippedEmpty);
        return fragments;
    }

    /**
     * Walk every vertex of the model. For each vertex, find which of
     * its (up to 4) bone influences has the highest weight; if that
     * bone is {@code targetBoneIndex}, add the vertex to the fragment.
     *
     * <p>Hard-weighted models (recommended via the Blender-chat prompt
     * in {@code FRACTURE_PLAN.md}) end up with each vertex cleanly
     * assigned to exactly one bone. Soft-skinned models still work but
     * produce visible seams at bone boundaries.</p>
     */
    private static List<Vector3f> collectDominantVertices(int targetBoneIndex,
                                                          BOBJLoader.CompiledData mesh)
    {
        float[] positions = mesh.posData;
        int[] boneIdx = mesh.boneIndexData;
        float[] weights = mesh.weightData;

        int vertexCount = positions.length / 3;
        List<Vector3f> result = new ArrayList<>();

        for (int v = 0; v < vertexCount; v++)
        {
            int dominantBone = -1;
            float dominantWeight = 0F;

            int slotBase = v * 4;
            for (int slot = 0; slot < 4; slot++)
            {
                if (slotBase + slot >= weights.length) break;
                float w = weights[slotBase + slot];
                int b = boneIdx[slotBase + slot];

                if (w > dominantWeight)
                {
                    dominantWeight = w;
                    dominantBone = b;
                }
            }

            if (dominantBone == targetBoneIndex && dominantWeight > 0F)
            {
                int posBase = v * 3;
                result.add(new Vector3f(
                    positions[posBase],
                    positions[posBase + 1],
                    positions[posBase + 2]
                ));
            }
        }

        return result;
    }

}
