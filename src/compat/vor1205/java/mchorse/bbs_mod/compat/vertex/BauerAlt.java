package mchorse.bbs_mod.compat.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/** vor1205: Ersatz fuer 1.21s new BufferBuilder(speicher, ...), Tesselator#begin, BufferUploader(MeshData). */
public final class BauerAlt
{
    private BauerAlt()
    {}

    public static BufferBuilder neu(ByteBufferBuilder speicher, VertexFormat.Mode art, VertexFormat format)
    {
        BufferBuilder bauer = speicher.bauer();

        bauer.begin(art, format);

        return bauer;
    }

    public static BufferBuilder tesselator(VertexFormat.Mode art, VertexFormat format)
    {
        BufferBuilder bauer = Tesselator.getInstance().getBuilder();

        bauer.begin(art, format);

        return bauer;
    }

    public static void zeichnen(MeshData netz)
    {
        BufferUploader.drawWithShader(netz.alt());
    }
}
