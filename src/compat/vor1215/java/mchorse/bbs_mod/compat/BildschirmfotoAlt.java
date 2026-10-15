package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;

import java.util.function.Consumer;

/** Screenshot.takeScreenshot mit Rueckruf (1.21.5+) vor 1.21.5: 1.21.4 liest synchron, der Rueckruf laeuft sofort. */
public final class BildschirmfotoAlt
{
    private BildschirmfotoAlt()
    {}

    public static void takeScreenshot(RenderTarget ziel, Consumer<NativeImage> rueckruf)
    {
        rueckruf.accept(Screenshot.takeScreenshot(ziel));
    }
}
