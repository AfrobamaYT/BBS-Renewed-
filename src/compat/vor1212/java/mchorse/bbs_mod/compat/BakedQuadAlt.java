package mchorse.bbs_mod.compat;

import mchorse.bbs_mod.compat.alt.MaterialInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * 26.2's BakedQuad-Zugriffe vor 1.21.2 (Quads leuchten dort nie selbst, Eigenleuchten 0), aus 1.21.4s Eckdaten (BLOCK-Format, 8 int je Ecke:
 * Position 0..2, Farbe 3, UV 4..5). UV wird wie UVPair.pack von 1.21.5+ gepackt (u hoch, v tief).
 * Injiziert in BakedQuad.
 */
public interface BakedQuadAlt
{
    default MaterialInfo materialInfo()
    {
        BakedQuad quad = (BakedQuad) this;

        return new MaterialInfo(quad.getSprite(), quad.getTintIndex(), quad.isShade(), 0);
    }

    default Vector3f position(int ecke)
    {
        int[] daten = ((BakedQuad) this).getVertices();
        int i = ecke * 8;

        return new Vector3f(Float.intBitsToFloat(daten[i]), Float.intBitsToFloat(daten[i + 1]), Float.intBitsToFloat(daten[i + 2]));
    }

    default long packedUV(int ecke)
    {
        int[] daten = ((BakedQuad) this).getVertices();
        int i = ecke * 8;

        return (long) daten[i + 4] << 32 | daten[i + 5] & 0xFFFFFFFFL;
    }

    default Direction direction()
    {
        return ((BakedQuad) this).getDirection();
    }

    default int tintIndex()
    {
        return ((BakedQuad) this).getTintIndex();
    }

    static float unpackU(long uv)
    {
        return Float.intBitsToFloat((int) (uv >>> 32));
    }

    static float unpackV(long uv)
    {
        return Float.intBitsToFloat((int) uv);
    }
}
