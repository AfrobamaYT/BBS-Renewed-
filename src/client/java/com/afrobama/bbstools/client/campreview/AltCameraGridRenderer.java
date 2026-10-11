package com.afrobama.bbstools.client.campreview;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Reference grid for an additional camera, clipped before the line shader divides by W. */
public final class AltCameraGridRenderer
{
    private static final int HALF_EXTENT = 256;
    private static final float NEAR = 0.05F;

    public static void render(Minecraft client, Camera camera, Matrix4f projection, RenderTarget target)
    {
        if (client.player == null)
        {
            return;
        }
        int x = (int) Math.floor(client.player.getX());
        int y = (int) Math.floor(client.player.getY());
        int z = (int) Math.floor(client.player.getZ());
        Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf()))
            .translate(-(float) camera.position().x, -(float) camera.position().y, -(float) camera.position().z);
        float width = 2F * client.getWindow().getWidth() / target.width;
        try (ByteBufferBuilder memory = new ByteBufferBuilder((2 * HALF_EXTENT + 1) * 8 * 24))
        {
            BufferBuilder builder = SofortZeichner.beginnen(memory, PrimitiveTopology.LINES, RenderTypes.LINES.format());
            for (int i = -HALF_EXTENT; i <= HALF_EXTENT; i++)
            {
                int alpha = i == 0 ? 220 : i % 8 == 0 ? 140 : 60;
                int color = (alpha << 24) | (i == 0 ? 0x64c8ff : 0xb4b4c8);
                line(builder, view, new Vector3f(x + i, y, z - HALF_EXTENT),
                    new Vector3f(x + i, y, z + HALF_EXTENT), color, width);
                line(builder, view, new Vector3f(x - HALF_EXTENT, y, z + i),
                    new Vector3f(x + HALF_EXTENT, y, z + i), color, width);
            }
            SofortZeichner.zeichnen(FormPipelines.linien(false), builder.build(), new Matrix4f(), null, null, target);
        }
    }

    private static void line(BufferBuilder builder, Matrix4f view, Vector3f start, Vector3f end, int color, float width)
    {
        view.transformPosition(start);
        view.transformPosition(end);
        if (start.z >= -NEAR && end.z >= -NEAR)
        {
            return;
        }
        if (start.z > -NEAR)
        {
            start.lerp(end, (-NEAR - start.z) / (end.z - start.z));
        }
        else if (end.z > -NEAR)
        {
            end.lerp(start, (-NEAR - end.z) / (start.z - end.z));
        }
        Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() < 0.000001F)
        {
            return;
        }
        direction.normalize();
        /* At both endpoints the shader's auxiliary point must remain inside the near plane. */
        builder.addVertex(start.x, start.y, start.z).setColor(color)
            .setNormal(direction.x, direction.y, direction.z).setLineWidth(width);
        builder.addVertex(end.x, end.y, end.z).setColor(color)
            .setNormal(-direction.x, -direction.y, -direction.z).setLineWidth(width);
    }

    private AltCameraGridRenderer()
    {}
}
