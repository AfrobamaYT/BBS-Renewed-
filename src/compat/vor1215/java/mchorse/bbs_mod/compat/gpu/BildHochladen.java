package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.platform.NativeImage;

/** NativeImage.upload in der Form von 1.21.4; vor 1.21.2 ersetzt vor1212 diese Datei (andere Signatur). */
final class BildHochladen
{
    private BildHochladen()
    {}

    static void hochladen(NativeImage bild, int mip, int x, int y, int quelleX, int quelleY, int breite, int hoehe)
    {
        bild.upload(mip, x, y, quelleX, quelleY, breite, hoehe, false);
    }
}
