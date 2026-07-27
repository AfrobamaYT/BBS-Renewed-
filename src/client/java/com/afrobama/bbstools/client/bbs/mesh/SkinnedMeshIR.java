// Coded by Afrobama/Claude — Tier 2-a Skinned-Mesh intermediate representation.
//
// Flat per-attribute arrays for ONE model rest pose. SoA (struct-of-arrays)
// layout is cache-friendly for CPU skin and trivially uploadable as VBO
// attributes for Tier 2-b GPU draw (each array becomes one VBO).
//
// vertCount = total quads × 4 (cubic) OR sum of mesh primitive verts (BOBJ).
// We do NOT triangulate at build time — Tier 2-b uses GL_QUADS or emits
// 2-tri index buffer at upload.
//
// boneCount = number of distinct group indices referenced (cubic) OR
// armature.orderedBones.size() (BOBJ). Tier 2 GPU path allocates an SSBO
// of mat4[boneCount × instanceCount] per frame for animator-driven bone
// matrices.
package com.afrobama.bbstools.client.bbs.mesh;

public final class SkinnedMeshIR
{
    /** Stable model identifier this IR was built from (ModelInstance.id). */
    public final String modelId;

    /** Total vertex count (== quadCount × 4 for cubic). */
    public final int vertCount;

    /** Quad count — useful for Tier 2-b index-buffer allocation
     *  (2 triangles × 3 indices per quad = 6 indices/quad). */
    public final int quadCount;

    /** Distinct bone slots this mesh references. For cubic this equals
     *  max(group.index) + 1. For BOBJ it equals armature.orderedBones.size(). */
    public final int boneCount;

    /** Format: 'C' (cubic, rigid 1-bone) or 'B' (BOBJ, up to 4-bone weighted). */
    public final char format;

    /* Flat per-attribute arrays. Total memory per vertex:
     *   position: 12 B, normal: 12 B, uv: 8 B,
     *   boneIdx: 16 B, weights: 16 B  =  64 B/vertex.
     * 1000 verts ≈ 64 KiB — fits in L2 trivially. */
    public final float[] positions;   /* vertCount * 3 */
    public final float[] normals;     /* vertCount * 3 */
    public final float[] uvs;         /* vertCount * 2 */
    public final int[]   boneIndices; /* vertCount * 4 */
    public final float[] weights;     /* vertCount * 4 */

    public SkinnedMeshIR(String modelId, int vertCount, int quadCount, int boneCount, char format,
                         float[] positions, float[] normals, float[] uvs,
                         int[] boneIndices, float[] weights)
    {
        this.modelId = modelId;
        this.vertCount = vertCount;
        this.quadCount = quadCount;
        this.boneCount = boneCount;
        this.format = format;
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.boneIndices = boneIndices;
        this.weights = weights;
    }

    public long byteSize()
    {
        long verts = vertCount;
        return (verts * 3 * 4)   /* positions */
             + (verts * 3 * 4)   /* normals   */
             + (verts * 2 * 4)   /* uvs       */
             + (verts * 4 * 4)   /* boneIdx   */
             + (verts * 4 * 4);  /* weights   */
    }

}
