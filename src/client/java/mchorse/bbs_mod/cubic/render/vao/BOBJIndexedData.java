package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.bobj.BOBJLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Packs BOBJ's face-corner arrays for indexed rendering without changing the original
 * CompiledData that fracture, Veil and other consumers still read.
 *
 * <p>Only corners with the same source position, normal AND UV index share a render
 * vertex. Weights and every shape-key trajectory belong to the source position, so
 * this preserves hard normals, UV seams, skinning and morphs. The index stream keeps
 * the source triangle order exactly.</p>
 */
final class BOBJIndexedData
{
    private record Corner(int position, int normal, int uv) {}

    final BOBJLoader.CompiledData vertices;
    final int[] indices;

    private BOBJIndexedData(BOBJLoader.CompiledData vertices, int[] indices)
    {
        this.vertices = vertices;
        this.indices = indices;
    }

    static BOBJIndexedData pack(BOBJLoader.CompiledData source)
    {
        int corners = source.posData.length / 3;

        if (source.mesh == null || source.mesh.faces.size() * 3 != corners
            || source.normData.length != corners * 3
            || source.texData.length != corners * 2
            || source.weightData == null || source.weightData.length != corners * 4
            || source.boneIndexData == null || source.boneIndexData.length != corners * 4)
        {
            throw new IllegalArgumentException("BOBJ indexed layout does not match face corners");
        }

        Map<Corner, Integer> unique = new HashMap<>();
        int[] firstCorner = new int[corners];
        int[] indices = new int[corners];
        int corner = 0;
        int count = 0;

        for (BOBJLoader.Face face : source.mesh.faces)
        {
            for (BOBJLoader.IndexGroup group : face.idxGroups)
            {
                Corner key = new Corner(group.idxPos, group.idxVecNormal, group.idxTextCoord);
                Integer index = unique.get(key);

                if (index == null)
                {
                    index = count++;
                    unique.put(key, index);
                    firstCorner[index] = corner;
                }

                indices[corner++] = index;
            }
        }

        float[] pos = new float[count * 3];
        float[] norm = new float[count * 3];
        float[] uv = new float[count * 2];
        float[] weight = new float[count * 4];
        int[] bones = new int[count * 4];

        for (int i = 0; i < count; i++)
        {
            int from = firstCorner[i];

            System.arraycopy(source.posData, from * 3, pos, i * 3, 3);
            System.arraycopy(source.normData, from * 3, norm, i * 3, 3);
            System.arraycopy(source.texData, from * 2, uv, i * 2, 2);
            System.arraycopy(source.weightData, from * 4, weight, i * 4, 4);
            System.arraycopy(source.boneIndexData, from * 4, bones, i * 4, 4);
        }

        BOBJLoader.CompiledData packed = new BOBJLoader.CompiledData(
            pos, uv, norm, weight, bones, indices, source.mesh);

        for (Map.Entry<String, float[]> entry : source.shapeData.entrySet())
        {
            float[] sourceShape = entry.getValue();

            if (sourceShape.length != corners * 3)
            {
                throw new IllegalArgumentException("BOBJ shape key " + entry.getKey()
                    + " does not match face corners");
            }

            float[] shape = new float[count * 3];

            for (int i = 0; i < count; i++)
            {
                System.arraycopy(sourceShape, firstCorner[i] * 3, shape, i * 3, 3);
            }

            packed.shapeData.put(entry.getKey(), shape);
        }

        return new BOBJIndexedData(packed, indices);
    }
}
