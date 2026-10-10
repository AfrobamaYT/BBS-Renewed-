package com.afrobama.bbstools.client.hitbox;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;

import com.afrobama.bbstools.hitbox.DamageHitboxClip;

/**
 * Client-side UI editor for {@link DamageHitboxClip}. Inherits the default
 * UIClip presentation (keyframe channel sliders) which is enough for now —
 * NeoForge BBSTools never customised the panel either.
 */
public class UIDamageHitboxClip extends UIClip<DamageHitboxClip>
{
    public static void registerWireframe()
    {
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(context ->
        {
            if (!com.afrobama.bbstools.hitbox.FilmHitboxBridge.showWireframe
                || mchorse.bbs_mod.film.VideoExportSession.laeuftExport())
            {
                return;
            }
            com.mojang.blaze3d.vertex.PoseStack stack = new com.mojang.blaze3d.vertex.PoseStack();
            net.minecraft.world.phys.Vec3 camera = context.levelState().cameraRenderState.pos;
            stack.translate(-camera.x, -camera.y, -camera.z);
            for (double[] box : com.afrobama.bbstools.hitbox.FilmHitboxBridge.lastFrameBoxes)
            {
                net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(
                    box[0] - box[3], box[1], box[2] - box[5],
                    box[0] + box[3], box[1] + box[4] * 2D, box[2] + box[5]);
                context.submitNodeCollector().submitShapeOutline(stack,
                    net.minecraft.world.phys.shapes.Shapes.create(bounds),
                    net.minecraft.client.renderer.rendertype.RenderTypes.LINES, 0xffffaa00, 1.5F, false);
            }
        });
    }

    public UIDamageHitboxClip(DamageHitboxClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

}
