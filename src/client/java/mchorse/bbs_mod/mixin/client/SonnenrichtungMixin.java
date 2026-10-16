package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Turns the sun's and moon's path around the vertical axis by a curve clip's "sun horizontal
 * rotation" (BBS FS f60b3a7a5, Wemppy4/bbs-fs, MIT): vanilla lays the path with a fixed -90 degree
 * turn, the curve adds to it. Vitrail's shadows don't follow yet.
 */
@Mixin(SkyRenderer.class)
public class SonnenrichtungMixin
{
    @ModifyConstant(method = "renderSunMoonAndStars", constant = @Constant(floatValue = -90F))
    private float bbs$sonnenrichtung(float rotation)
    {
        return rotation + ShaderCurves.sonnenrichtung();
    }
}
