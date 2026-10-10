package mchorse.bbs_mod.film.markers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.film.VideoExportSession;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** One immutable native line submission, not entities/particles and not export geometry. */
final class TimerGroundRender
{
    private TimerGroundRender()
    {}

    static void submit(LevelRenderContext context)
    {
        TimerGroundGuidance.Snapshot snapshot = TimerGroundGuidance.snapshot();

        if (VideoExportSession.laeuftExport() || snapshot.points().isEmpty())
        {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, builder) ->
        {
            for (int index = 1; index < snapshot.points().size(); index++)
            {
                Vec3 from = snapshot.points().get(index - 1);
                Vec3 to = snapshot.points().get(index);

                /* A diagonal between floor heights cuts through the raised voxel. */
                Vec3 bend = corner(from, to);

                segment(builder, pose, camera, from, bend, snapshot.color());
                segment(builder, pose, camera, bend, to, snapshot.color());
                Vec3 flatStart = from.y < to.y ? bend : from;
                Vec3 flatEnd = from.y < to.y ? to : bend;
                Vec3 direction = flatEnd.subtract(flatStart);
                double length = direction.horizontalDistance();

                if (length > 0.01D)
                {
                    Vec3 back = new Vec3(direction.x / length, 0D, direction.z / length).scale(-0.22D);
                    Vec3 side = new Vec3(-back.z, 0D, back.x).scale(0.65D);
                    Vec3 tip = flatStart.lerp(flatEnd, 0.7D);

                    segment(builder, pose, camera, tip, tip.add(back).add(side), snapshot.color());
                    segment(builder, pose, camera, tip, tip.add(back).subtract(side), snapshot.color());
                }
            }
            Vec3 goal = snapshot.points().getLast();

            for (int index = 0; index < 8; index++)
            {
                double angle = index * Math.PI / 4D;
                double next = (index + 1) * Math.PI / 4D;

                segment(builder, pose, camera, goal.add(Math.cos(angle) * 0.35D, 0D, Math.sin(angle) * 0.35D),
                    goal.add(Math.cos(next) * 0.35D, 0D, Math.sin(next) * 0.35D), snapshot.color());
            }
        });
    }

    private static Vec3 corner(Vec3 from, Vec3 to)
    {
        return from.y < to.y ? new Vec3(from.x, to.y, from.z) : new Vec3(to.x, from.y, to.z);
    }

    private static void segment(VertexConsumer builder, PoseStack.Pose pose, Vec3 camera, Vec3 from, Vec3 to, int color)
    {
        if (from.distanceToSqr(to) < 0.000001D)
        {
            return;
        }
        Vec3 direction = to.subtract(from).normalize();

        builder.addVertex(pose, (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z))
            .setColor(color).setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z).setLineWidth(2F);
        builder.addVertex(pose, (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z))
            .setColor(color).setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z).setLineWidth(2F);
    }

    static void hud(GuiGraphicsExtractor context, DeltaTracker delta)
    {
        TimerGroundGuidance.Snapshot snapshot = TimerGroundGuidance.snapshot();
        Minecraft client = Minecraft.getInstance();

        if (VideoExportSession.laeuftExport() || snapshot.film() == null || client.gui.screen() != null)
        {
            return;
        }
        String text = snapshot.error().isEmpty() ? "Route: " + snapshot.label()
            + (snapshot.points().isEmpty() ? " (planning...)" : "") : "Route: " + snapshot.error();

        context.text(client.font, text, 5, 40, snapshot.error().isEmpty() ? snapshot.color() : 0xFFFF5555, true);
    }
}
