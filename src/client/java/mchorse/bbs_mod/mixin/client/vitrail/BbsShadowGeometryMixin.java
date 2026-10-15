package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Mixin(targets = "dev.vitrail.render.ShadowGeometry", remap = false)
public abstract class BbsShadowGeometryMixin
{
    @Shadow @Final private static LevelRenderState STATE;
    @Shadow private static SubmitNodeStorage storage;

    @Inject(method = "gather", at = @At("HEAD"))
    private static void bbs$begin(Matrix4f light, Vec3 camera, @Coerce Object casters, CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShadowLogik", "begin"); }

    @WrapOperation(method = "gather", at = @At(value = "INVOKE", target =
        "Ldev/vitrail/render/ShadowGeometry;extractEntities(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ldev/vitrail/pack/source/ShadowCasters;)I"))
    private static int bbs$gather(Minecraft minecraft, Camera view, Frustum frustum,
        @Coerce Object casters, Operation<Integer> original) throws Throwable
    { return ((Number) MixinBruecke.rufePflicht("VitrailShadowLogik", "gather", minecraft, view, frustum, STATE, casters, original)).intValue(); }

    @Inject(method = "gatherBlockEntities", at = @At("RETURN"))
    private static void bbs$prepare(@Coerce Object walk, CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShadowLogik", "prepare", STATE); }

    @WrapOperation(method = "walk", at = @At(value = "INVOKE", target =
        "Ldev/vitrail/render/ShadowGeometry;submit(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/phys/Vec3;)V"))
    private static void bbs$submit(Minecraft minecraft, Vec3 camera, Operation<Void> original) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShadowLogik", "submit", minecraft, camera, STATE, storage, original); }

    @WrapOperation(method = "walk", at = @At(value = "INVOKE", target = "Ldev/vitrail/render/EntityDraw;shadowFeatures(Z)V"))
    private static void bbs$window(boolean open, Operation<Void> original) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShadowLogik", "window", open, original); }

    @WrapMethod(method = "draw(Lnet/minecraft/world/phys/Vec3;)V")
    private static void bbs$draw(Vec3 camera, Operation<Void> original) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShadowLogik", "draw", camera, original); }
}
