package mchorse.bbs_mod.forms.renderers.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MobFormPose;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where a mob form's bones are, in the form's own space, without drawing anything: so anchors,
 * trackers and the gizmo can sit on a mob's head or arm as they sit on a model's. It replays what
 * {@code LivingEntityRenderer.submit} does before it draws (scale, setupRotations, the flip, the
 * entity's scale, the 1.501 drop), poses the model with {@code setupAnim} and the form's pose, then
 * walks the part tree for matrices. The model's parts are shared with the world's entities, so the
 * pose is taken off again at once. Each bone's frame is turned by {@code scale(-1, -1, 1)} back to the
 * frame a model form's bones have (a rotation, not a mirror). From BBS FS e341fa733 (Wemppy4/bbs-fs,
 * MIT); there a mixin captured the tree, 26.3's parts keep their children by name.
 */
public final class MobMatrizen
{
    private MobMatrizen()
    {}

    /** What runs in the posed model's frame: the renderer, its state, the root's parts and their bone names */
    public interface Rumpf
    {
        void lauf(LivingEntityRenderer<?, LivingEntityRenderState, ?> renderer, LivingEntityRenderState zustand, ModelPart wurzel, Map<ModelPart, String> namen);
    }

    /** Adds every bone under {@code prefix}, relative to the frame on {@code stack} */
    public static void sammeln(Entity entity, Pose pose, float transition, PoseStack stack, MatrixCache matrices, String prefix)
    {
        aufstellen(entity, pose, transition, stack, (renderer, zustand, wurzel, namen) ->
        {
            wurzel.translateAndRotate(stack);

            for (ModelPart kind : wurzel.children.values()) gehen(kind, namen, stack, matrices, prefix);
        });
    }

    /** Poses the model, puts the frame the renderer draws it in on {@code stack}, runs {@code rumpf} and takes the pose off again */
    public static void aufstellen(Entity entity, Pose pose, float transition, PoseStack stack, Rumpf rumpf)
    {
        if (entity == null || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?, ?> roh)
            || !(Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(entity, transition) instanceof LivingEntityRenderState zustand))
        {
            return;
        }

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<?, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> renderer = (LivingEntityRenderer<?, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>) roh;
        EntityModel<LivingEntityRenderState> model = renderer.getModel();
        MobKnochen rig = MobKnochen.von(entity);
        Map<ModelPart, String> namen = new IdentityHashMap<>();

        if (rig == null)
        {
            return;
        }

        rig.teile.forEach((name, teil) -> namen.put(teil, name));
        ((FabricRenderState) zustand).setData(MobFormPose.POSE_AM_ZUSTAND, pose);

        stack.pushPose();

        try
        {
            mchorse.bbs_mod.mixin.client.LivingEntityRendererInvoker aufrufer = (mchorse.bbs_mod.mixin.client.LivingEntityRendererInvoker) renderer;

            model.setupAnim(zustand);
            MobFormPose.auflegen(zustand);
            stack.scale(zustand.scale, zustand.scale, zustand.scale);
            aufrufer.bbs$setupRotations(zustand, stack, zustand.bodyRot, zustand.scale);
            stack.scale(-1F, -1F, 1F);
            aufrufer.bbs$scale(zustand, stack);
            stack.translate(0F, -1.501F, 0F);
            rumpf.lauf(renderer, zustand, model.root(), namen);
        }
        finally
        {
            MobFormPose.zuruecksetzen();
            stack.popPose();
        }
    }

    /** The descent ModelPart.render makes, minus the drawing; a hidden part has no bone here either */
    private static void gehen(ModelPart teil, Map<ModelPart, String> namen, PoseStack stack, MatrixCache matrices, String prefix)
    {
        if (!teil.visible)
        {
            return;
        }

        String name = namen.get(teil);
        Matrix4f eltern = new Matrix4f(stack.last().pose());

        stack.pushPose();
        teil.translateAndRotate(stack);

        if (name != null)
        {
            MatrixCacheEntry eintrag = matrices.acquire(StringUtils.combinePaths(prefix, name));

            eintrag.origin().set(eltern).translate(teil.x / 16F, teil.y / 16F, teil.z / 16F).scale(-1F, -1F, 1F);
            eintrag.matrix().set(stack.last().pose()).scale(-1F, -1F, 1F);
        }

        for (ModelPart kind : teil.children.values()) gehen(kind, namen, stack, matrices, prefix);

        stack.popPose();
    }
}
