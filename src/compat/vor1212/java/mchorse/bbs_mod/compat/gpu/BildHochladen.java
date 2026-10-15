package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.platform.NativeImage;

/** Vor 1.21.2: NativeImage.upload nimmt zusaetzlich den Mipmap-Schalter (hier aus, wie 1.21.4s Form). */
final class BildHochladen
{
    private BildHochladen()
    {}

    static void hochladen(NativeImage bild, int mip, int x, int y, int quelleX, int quelleY, int breite, int hoehe)
    {
        bild.upload(mip, x, y, quelleX, quelleY, breite, hoehe, false, false);
    }
}
