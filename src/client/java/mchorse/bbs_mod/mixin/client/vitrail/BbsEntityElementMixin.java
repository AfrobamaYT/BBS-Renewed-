package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.vitrail.render.EntityDraw$Element", remap = false)
public abstract class BbsEntityElementMixin
{
    @ModifyReturnValue(method = "format()Lcom/mojang/renderpearl/api/vertex/VertexFormat;", at = @At("RETURN"))
    private VertexFormat bbs$nativeFormat(VertexFormat original) throws Throwable
    { return (VertexFormat) MixinBruecke.rufePflicht("VitrailGeometryLogik", "format", this, original); }
}
