// Coded by Afrobama/Claude — Tier 2-a cubic Model → SkinnedMeshIR.
//
// Walks a BBS cubic Model and produces the flat-skin IR for Tier 2 GPU
// rendering. Cubic models use RIGID per-group skinning: every vertex of
// a cube belongs entirely to its parent group's bone slot. This matches
// how BBS's CubicRenderer applies group.current Transform to all of a
// cube's vertices uniformly.
//
// Per group hierarchy: bone-index = group.index (assigned at model build
// time by BBS, stable). Tier 2 GPU shader uses boneIdx to index into a
// per-instance Matrix4f[] uploaded as UBO/SSBO per frame.
//
// Cubes already have their quads pre-generated (ModelCube.generateQuads
// populates each cube's `quads` list with ModelVertex {pos, uv} + face
// normal in group-local space, position already /16F). We just collect.
package com.afrobama.bbstools.client.bbs.mesh;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.utils.MathUtils;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class CubicToSkinnedConverter
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/mesh/cubic-conv");

    private CubicToSkinnedConverter() {}

    public static SkinnedMeshIR convert(String modelId, Model cubic)
    {
        if (cubic == null || cubic.topGroups == null || cubic.topGroups.isEmpty())
        {
            LOG.warn("[cubic-conv] empty/null model — id={}", modelId);
            return new SkinnedMeshIR(modelId, 0, 0, 0, 'C',
                new float[0], new float[0], new float[0], new int[0], new float[0]);
        }

        /* First pass — count quads + max bone index. */
        int[] counts = new int[2]; /* {quadCount, maxBoneIdx} */
        for (ModelGroup top : cubic.topGroups)
        {
            countRecursive(top, counts);
        }
        int quadCount = counts[0];
        int boneCount = counts[1] + 1;
        int vertCount = quadCount * 4;

        if (vertCount == 0)
        {
            LOG.warn("[cubic-conv] no quads — id={} groups={}", modelId, cubic.topGroups.size());
            return new SkinnedMeshIR(modelId, 0, 0, boneCount, 'C',
                new float[0], new float[0], new float[0], new int[0], new float[0]);
        }

        float[] positions   = new float[vertCount * 3];
        float[] normals     = new float[vertCount * 3];
        float[] uvs         = new float[vertCount * 2];
        int[]   boneIndices = new int[vertCount * 4];
        float[] weights     = new float[vertCount * 4];

        Cursor cursor = new Cursor();
        for (ModelGroup top : cubic.topGroups)
        {
            emitRecursive(top, positions, normals, uvs, boneIndices, weights, cursor);
        }

        LOG.info("[cubic-conv] built IR id={} verts={} quads={} bones={} sizeKB={}",
            modelId, vertCount, quadCount, boneCount,
            new SkinnedMeshIR(modelId, vertCount, quadCount, boneCount, 'C',
                positions, normals, uvs, boneIndices, weights).byteSize() / 1024);

        return new SkinnedMeshIR(modelId, vertCount, quadCount, boneCount, 'C',
            positions, normals, uvs, boneIndices, weights);
    }

    private static void countRecursive(ModelGroup group, int[] counts)
    {
        if (group == null) return;
        if (group.index > counts[1]) counts[1] = group.index;

        if (group.cubes != null)
        {
            for (ModelCube cube : group.cubes)
            {
                if (cube == null || cube.quads == null) continue;
                counts[0] += cube.quads.size();
            }
        }

        if (group.children != null)
        {
            for (ModelGroup child : group.children) countRecursive(child, counts);
        }
    }

    private static void emitRecursive(ModelGroup group,
                                      float[] positions, float[] normals, float[] uvs,
                                      int[] boneIndices, float[] weights,
                                      Cursor cursor)
    {
        if (group == null) return;
        int boneIdx = group.index;

        if (group.cubes != null)
        {
            for (ModelCube cube : group.cubes)
            {
                if (cube == null || cube.quads == null) continue;

                /* Bake cube.rotate around cube.pivot into vertex/normal at
                 * IR build time. BBS's CubicCubeRenderer.renderCube does
                 * stack.push → moveToPivot → rotate → moveBackFromPivot →
                 * write quads. Since the bone matrix our Tier 2-b shader
                 * uses captures only group-level transforms, we must bake
                 * the cube-local rotation into the rest-pose vertex. */
                boolean hasRotation = (cube.rotate.x != 0F || cube.rotate.y != 0F || cube.rotate.z != 0F);
                Matrix4f cubeRot = null;
                float pivX = 0F, pivY = 0F, pivZ = 0F;
                if (hasRotation)
                {
                    cubeRot = new Matrix4f();
                    /* Rotation order matches CubicCubeRenderer.rotate static:
                     * modelM = Rz × Ry × Rx (post-multiplied). Degrees → rad. */
                    cubeRot.rotateZ(MathUtils.toRad(cube.rotate.z));
                    cubeRot.rotateY(MathUtils.toRad(cube.rotate.y));
                    cubeRot.rotateX(MathUtils.toRad(cube.rotate.x));
                    pivX = cube.pivot.x / 16F;
                    pivY = cube.pivot.y / 16F;
                    pivZ = cube.pivot.z / 16F;
                }

                for (ModelQuad quad : cube.quads)
                {
                    emitQuad(quad, boneIdx, cubeRot, pivX, pivY, pivZ,
                        positions, normals, uvs, boneIndices, weights, cursor);
                }
            }
        }

        if (group.children != null)
        {
            for (ModelGroup child : group.children)
            {
                emitRecursive(child, positions, normals, uvs, boneIndices, weights, cursor);
            }
        }
    }

    private static void emitQuad(ModelQuad quad, int boneIdx,
                                 Matrix4f cubeRot, float pivX, float pivY, float pivZ,
                                 float[] positions, float[] normals, float[] uvs,
                                 int[] boneIndices, float[] weights,
                                 Cursor cursor)
    {
        if (quad == null || quad.vertices == null || quad.vertices.size() < 4) return;

        float nx = quad.normal.x, ny = quad.normal.y, nz = quad.normal.z;

        /* Bake the cube rotation into the quad normal (rotation only). */
        if (cubeRot != null)
        {
            Vector3f n = new Vector3f(nx, ny, nz);
            cubeRot.transformDirection(n);
            nx = n.x; ny = n.y; nz = n.z;
        }

        List<ModelVertex> verts = quad.vertices;
        Vector4f tmp = (cubeRot != null) ? new Vector4f() : null;

        for (int i = 0; i < 4; i++)
        {
            ModelVertex mv = verts.get(i);
            int pi = cursor.vert * 3;
            int ui = cursor.vert * 2;
            int bi = cursor.vert * 4;

            float px = mv.vertex.x;
            float py = mv.vertex.y;
            float pz = mv.vertex.z;

            if (cubeRot != null)
            {
                /* pos' = pivot + R × (pos - pivot) */
                tmp.set(px - pivX, py - pivY, pz - pivZ, 1F);
                cubeRot.transform(tmp);
                px = tmp.x + pivX;
                py = tmp.y + pivY;
                pz = tmp.z + pivZ;
            }

            positions[pi    ] = px;
            positions[pi + 1] = py;
            positions[pi + 2] = pz;

            normals[pi    ] = nx;
            normals[pi + 1] = ny;
            normals[pi + 2] = nz;

            uvs[ui    ] = mv.uv.x;
            uvs[ui + 1] = mv.uv.y;

            boneIndices[bi    ] = boneIdx;
            boneIndices[bi + 1] = 0;
            boneIndices[bi + 2] = 0;
            boneIndices[bi + 3] = 0;

            weights[bi    ] = 1F;
            weights[bi + 1] = 0F;
            weights[bi + 2] = 0F;
            weights[bi + 3] = 0F;

            cursor.vert++;
        }
    }

    private static final class Cursor
    {
        int vert = 0;
    }

}
