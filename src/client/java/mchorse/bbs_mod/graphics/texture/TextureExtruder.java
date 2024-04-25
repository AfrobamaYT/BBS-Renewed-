package mchorse.bbs_mod.graphics.texture;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.gl.VertexBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextureExtruder
{
    private Map<Link, CachedExtrudedData> extruded = new HashMap<>();

    /**
     * Fill a quad for {@link net.minecraft.client.render.VertexFormats#POSITION_TEXTURE_COLOR_NORMAL}. Points should
     * be supplied in this order:
     *
     *     3 -------> 4
     *     ^
     *     |
     *     |
     *     2 <------- 1
     *
     * I.e. bottom left, bottom right, top left, top right, where left is -X and right is +X,
     * in case of a quad on fixed on Z axis.
     */
    public static void fillTexturedNormalQuad(List<Float> vertices, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float nx, float ny, float nz)
    {
        /* 1 - BL, 2 - BR, 3 - TR, 4 - TL */
        vertices.add(x2);
        vertices.add(y2);
        vertices.add(z2);
        vertices.add(u1);
        vertices.add(v2);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);

        vertices.add(x1);
        vertices.add(y1);
        vertices.add(z1);
        vertices.add(u2);
        vertices.add(v2);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);

        vertices.add(x4);
        vertices.add(y4);
        vertices.add(z4);
        vertices.add(u2);
        vertices.add(v1);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);

        vertices.add(x2);
        vertices.add(y2);
        vertices.add(z2);
        vertices.add(u1);
        vertices.add(v2);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);

        vertices.add(x4);
        vertices.add(y4);
        vertices.add(z4);
        vertices.add(u2);
        vertices.add(v1);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);

        vertices.add(x3);
        vertices.add(y3);
        vertices.add(z3);
        vertices.add(u1);
        vertices.add(v1);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
    }

    public static float[] fromList(List<Float> list)
    {
        float[] floats = new float[list.size()];

        for (int i = 0; i < floats.length; i++)
        {
            floats[i] = list.get(i);
        }

        return floats;
    }

    public void delete(Link key)
    {
        this.extruded.remove(key);
    }

    public void deleteAll()
    {
        this.extruded.clear();
    }

    public CachedExtrudedData get(Link key)
    {
        if (this.extruded.containsKey(key))
        {
            return this.extruded.get(key);
        }

        Pixels pixels = null;

        try
        {
            pixels = BBSModClient.getTextures().getPixels(key);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (pixels == null)
        {
            this.extruded.put(key, null);

            return null;
        }

        CachedExtrudedData buffer = this.generate(pixels);

        pixels.delete();
        this.extruded.put(key, buffer);

        return buffer;
    }

    private CachedExtrudedData generate(Pixels pixels)
    {
        List<Float> vertices = new ArrayList<>();

        float p = 0.5F;
        float n = -0.5F;
        float u1 = 0F;
        float v1 = 0F;
        float u2 = 1F;
        float v2 = 1F;
        float d = 0.5F / 16F;

        fillTexturedNormalQuad(vertices,
            p, n, d,
            n, n, d,
            n, p, d,
            p, p, d,
            u1, v1, u2, v2,
            0F, 0F, 1F
        );

        fillTexturedNormalQuad(vertices,
            n, n, -d,
            p, n, -d,
            p, p, -d,
            n, p, -d,
            u2, v1, u1, v2,
            0F, 0F, -1F
        );

        for (int i = 0; i < pixels.width; i++)
        {
            for (int j = 0; j < pixels.height; j++)
            {
                if (this.hasPixel(pixels, i, j))
                {
                    this.generateNeighbors(pixels, vertices, i, j, i, j, d);
                }
            }
        }

        return new CachedExtrudedData(fromList(vertices));
    }

    private void generateNeighbors(Pixels pixels, List<Float> vertices, int i, int j, int x, int y, float d)
    {
        float w = pixels.width;
        float h = pixels.height;
        float u = (x + 0.5F) / w;
        float v = (y + 0.5F) / h;

        if (!this.hasPixel(pixels, x - 1, y) || i == 0)
        {
            fillTexturedNormalQuad(vertices,
                i / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                i / w - 0.5F, -j / h + 0.5F, -d,
                i / w - 0.5F, -j / h + 0.5F, d,
                i / w - 0.5F, -(j + 1) / h + 0.5F, d,
                u, v, u, v,
                -1F, 0F, 0F
            );
        }

        if (!this.hasPixel(pixels, x + 1, y) || i == 15)
        {
            fillTexturedNormalQuad(vertices,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, d,
                (i + 1) / w - 0.5F, -j / h + 0.5F, d,
                (i + 1) / w - 0.5F, -j / h + 0.5F, -d,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                u, v, u, v,
                1F, 0F, 0F
            );
        }

        if (!this.hasPixel(pixels, x, y - 1) || j == 0)
        {
            fillTexturedNormalQuad(vertices,
                (i + 1) / w - 0.5F, -j / h + 0.5F, d,
                i / w - 0.5F, -j / h + 0.5F, d,
                i / w - 0.5F, -j / h + 0.5F, -d,
                (i + 1) / w - 0.5F, -j / h + 0.5F, -d,
                u, v, u, v,

                0F, 1F, 0F
            );
        }

        if (!this.hasPixel(pixels, x, y + 1) || j == 15)
        {
            fillTexturedNormalQuad(vertices,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                i / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                i / w - 0.5F, -(j + 1) / h + 0.5F, d,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, d,
                u, v, u, v,
                0F, -1F, 0F
            );
        }
    }

    private boolean hasPixel(Pixels pixels, int x, int y)
    {
        Color pixel = pixels.getColor(x, y);

        return pixel != null && pixel.a >= 1;
    }

    public static class CachedExtrudedData
    {
        public float[] data;

        public CachedExtrudedData(float[] data)
        {
            this.data = data;
        }

        public int getCount()
        {
            return this.data.length / (3 + 2 + 3);
        }
    }
}