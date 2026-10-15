package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix4f;

public class Draw
{
    public static void renderBox(PoseStack stack, double x, double y, double z, double w, double h, double d)
    {
        renderBox(stack, x, y, z, w, h, d, 1, 1, 1);
    }

    public static void renderBox(PoseStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b)
    {
        renderBox(stack, x, y, z, w, h, d, r, g, b, 1F);
    }

    public static void renderBox(PoseStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float a)
    {
        stack.pushPose();
        stack.translate(x, y, z);
        float fw = (float) w;
        float fh = (float) h;
        float fd = (float) d;
        float t = 1 / 96F + (float) (Math.sqrt(w * w + h + h + d + d) / 2000);

        /* 26.2: Tesselator und BufferUploader sind weg. Der Aufbau der Ecken bleibt
         * unveraendert, nur Anfang und Ende gehen ueber SofortZeichner. Vorab so gross wie
         * die Zeichnung wird: 12 Kaesten a 36 Ecken a 16 Byte (POSITION_COLOR), damit der
         * Speicher nicht in jedem Bild nachwaechst. */
        try (ByteBufferBuilder speicher = new ByteBufferBuilder(12 * 36 * 16))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            /* Pillars: fillBox(builder, -t, -t, -t, t, t, t, r, g, b, a); */
            fillBox(builder, stack, -t, -t, -t, t, t + fh, t, r, g, b, a);
            fillBox(builder, stack, -t + fw, -t, -t, t + fw, t + fh, t, r, g, b, a);
            fillBox(builder, stack, -t, -t, -t + fd, t, t + fh, t + fd, r, g, b, a);
            fillBox(builder, stack, -t + fw, -t, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);

            /* Top */
            fillBox(builder, stack, -t, -t + fh, -t, t + fw, t + fh, t, r, g, b, a);
            fillBox(builder, stack, -t, -t + fh, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);
            fillBox(builder, stack, -t, -t + fh, -t, t, t + fh, t + fd, r, g, b, a);
            fillBox(builder, stack, -t + fw, -t + fh, -t, t + fw, t + fh, t + fd, r, g, b, a);

            /* Bottom */
            fillBox(builder, stack, -t, -t, -t, t + fw, t, t, r, g, b, a);
            fillBox(builder, stack, -t, -t, -t + fd, t + fw, t, t + fd, r, g, b, a);
            fillBox(builder, stack, -t, -t, -t, t, t, t + fd, r, g, b, a);
            fillBox(builder, stack, -t + fw, -t, -t, t + fw, t, t + fd, r, g, b, a);

            /* Schalter: mischen JA — der Kasten wird mit Alpha gezeichnet (der
             * Modellblock-Renderer uebergibt 0.5F), ohne Mischung waere er deckend.
             * Tiefenpruefung NEIN — die Umrisse sind Markierungen und sollen durch
             * Geometrie sichtbar bleiben; genau dafuer stand beim Aufrufer in
             * ModelBlockEntityRenderer ein disableDepthTest direkt davor. Cull JA wie im
             * Weltzustand: fillBox windet alle Flaechen nach aussen, es faellt nichts weg.
             * Die Ecken tragen die Pose schon (fillQuad backt stack.last().pose() ein),
             * darum bleibt als Modellansicht nur die globale. */
            SofortZeichner.zeichnen(
                FormPipelines.holen(VertexFormat.Mode.TRIANGLES, true, false, true),
                builder.build(),
                new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()),
                null,
                null);
        }

        stack.popPose();
    }

    /**
     * Fill a quad for {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#POSITION_TEX_COLOR_NORMAL}. Points should
     * be supplied in this order:
     *
     *     3 -------> 4
     *     ^
     *     |
     *     |
     *     2 <------- 1
     *
     * I.e. bottom left, bottom right, top left, top right, where left is -X and right is +X,
     * in case of a quad on fixed on Z axis.
     */
    public static void fillTexturedNormalQuad(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float r, float g, float b, float a, float nx, float ny, float nz)
    {
        Matrix4f matrix4f = stack.last().pose();

        /* 1 - BL, 2 - BR, 3 - TR, 4 - TL */
        builder.addVertex(matrix4f, x2, y2, z2).setUv(u1, v2).setColor(r, g, b, a).setNormal(nx, ny, nz);
        builder.addVertex(matrix4f, x1, y1, z1).setUv(u2, v2).setColor(r, g, b, a).setNormal(nx, ny, nz);
        builder.addVertex(matrix4f, x4, y4, z4).setUv(u2, v1).setColor(r, g, b, a).setNormal(nx, ny, nz);

        builder.addVertex(matrix4f, x2, y2, z2).setUv(u1, v2).setColor(r, g, b, a).setNormal(nx, ny, nz);
        builder.addVertex(matrix4f, x4, y4, z4).setUv(u2, v1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        builder.addVertex(matrix4f, x3, y3, z3).setUv(u1, v1).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    public static void fillQuad(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        fillQuad(builder, stack, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, r, g, b, a, false);
    }

    /**
     * Eine Flaeche schreiben — als Dreiecksliste (Standard) oder als Viereck.
     *
     * <p>⛔ DIE TOPOLOGIE MUSS ZUR PIPELINE PASSEN, SONST GIBT ES SPLITTER STATT FLAECHEN
     * (2026-08-02 an der laufenden 26.2 belegt). {@code RenderTypes.debugFilledBox()} faehrt
     * {@code VertexFormat.Mode.QUADS} — an der laufenden JVM abgefragt, nicht vermutet. Wer
     * dort eine Dreiecksliste hineinschreibt, bekommt vom Indexpuffer 4er-Gruppen
     * {@code (0,1,2,2,3,0)} ueber eine 6er-Folge gelegt: von 12 richtigen Dreiecken je Kasten
     * ueberleben 3, der Rest wird zu flaechenuebergreifenden Splittern und entarteten
     * Dreiecken. Das ist das gemeldete Bild "manche Straenge haarfein, und man sieht
     * hindurch"; dass es sich mit der Blickrichtung aendert, kommt von {@code sortOnUpload()},
     * das 4er-Gruppen nach Abstand sortiert.
     *
     * <p>26.1 hatte das Problem nicht, weil es sein Netz selbst mit {@code Mode.TRIANGLES}
     * baute und fertig uebergab — in 26.2 gibt es {@code RenderType.draw(MeshData)} nicht
     * mehr, der Port wich auf {@code submitCustomGeometry} aus und verlor dabei die
     * Topologie.
     *
     * <p>Die Geometrie steht bewusst NUR HIER. Eine zweite Kastenroutine daneben waere eine
     * zweite Quelle fuer dieselbe Frage — die stimmen genau so lange ueberein, bis jemand
     * eine aendert.
     */
    public static void fillQuad(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a, boolean vierecke)
    {
        Matrix4f matrix4f = stack.last().pose();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        if (vierecke)
        {
            builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a);
            builder.addVertex(matrix4f, x2, y2, z2).setColor(r, g, b, a);
            builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a);
            builder.addVertex(matrix4f, x4, y4, z4).setColor(r, g, b, a);

            return;
        }

        builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x4, y4, z4).setColor(r, g, b, a);
    }

    public static void fillBoxTo(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a)
    {
        fillBoxTo(builder, stack, x1, y1, z1, x2, y2, z2, thickness, r, g, b, a, false);
    }

    /** Wie oben, aber mit Wahl der Topologie — siehe {@link #fillQuad} zum Warum. */
    public static void fillBoxTo(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a, boolean vierecke)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Angle angle = Angle.angle(dx, dy, dz);

        stack.pushPose();

        stack.translate(x1, y1, z1);
        stack.rotate(com.mojang.math.Axis.YP.rotationDegrees(angle.yaw));
        stack.rotate(com.mojang.math.Axis.XP.rotationDegrees(angle.pitch));

        fillBox(builder, stack, -thickness / 2, -thickness / 2, 0, thickness / 2, thickness / 2, (float) distance, r, g, b, a, vierecke);

        stack.popPose();
    }

    public static void fillBox(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, int color)
    {
        float alpha = Colors.getA(color);

        if (alpha <= 0F)
        {
            alpha = 1F;
        }

        fillBox(builder, stack, x1, y1, z1, x2, y2, z2, Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha);
    }

    public static void fillBox(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b)
    {
        fillBox(builder, stack, x1, y1, z1, x2, y2, z2, r, g, b, 1F);
    }

    public static void fillBox(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a)
    {
        fillBox(builder, stack, x1, y1, z1, x2, y2, z2, r, g, b, a, false);
    }

    /** Wie oben, aber mit Wahl der Topologie — siehe {@link #fillQuad} zum Warum. */
    public static void fillBox(VertexConsumer builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, boolean vierecke)
    {
        /* X */
        fillQuad(builder, stack, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a, vierecke);
        fillQuad(builder, stack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a, vierecke);

        /* Y */
        fillQuad(builder, stack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a, vierecke);
        fillQuad(builder, stack, x2, y2, z1, x1, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a, vierecke);

        /* Z */
        fillQuad(builder, stack, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a, vierecke);
        fillQuad(builder, stack, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a, vierecke);
    }

    public static void coolerAxes(PoseStack stack, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        axisSize *= scale;
        axisOffset *= scale * thickness;


        /* 4 Kaesten a 36 Ecken a 16 Byte (POSITION_COLOR). */
        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4 * 36 * 16))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, Colors.RED);
            fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, Colors.GREEN);
            fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, Colors.BLUE);
            fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, Colors.WHITE);

            /* Schalter: Tiefenpruefung NEIN — das stand hier vorher woertlich als
             * RenderSystem.disableDepthTest(), damit die Achsen durch das Modell zu sehen
             * sind (ModelBlockEntityRenderer verlaesst sich darauf: "der bringt seinen
             * Tiefenzustand ebenfalls selbst mit"). Kein Mischen, die Achsenfarben sind
             * undurchsichtig.
             *
             * ⛔ CULL NEIN, und das ist GEMESSEN (2026-08-12): mit Cull verwirft 26.2
             * SAEMTLICHE fillBox-Flaechen dieses Helfers als Rueckseiten — Orbit-Marker und
             * Entity-Achsen waren komplett unsichtbar, waehrend der wortgleiche Zug mit
             * Cull aus zeichnet (Gegenprobe MotionPath, gleiche Pipeline-Familie, cull=false,
             * sichtbar). Die Boxen sind geschlossen; von aussen aendert Cull nichts am Bild. */
            SofortZeichner.zeichnen(
                FormPipelines.holen(VertexFormat.Mode.TRIANGLES, false, false, false),
                builder.build(),
                new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()),
                null,
                null);
        }
    }

    public static void arc3D(BufferBuilder builder, PoseStack stack, Axis axis, float radius, float thickness, int color)
    {
        arc3D(builder, stack, axis, radius, thickness, Colors.getR(color), Colors.getG(color), Colors.getB(color), 0F, 360F);
    }

    public static void arc3D(BufferBuilder builder, PoseStack stack, Axis axis, float radius, float thickness, float r, float g, float b)
    {
        arc3D(builder, stack, axis, radius, thickness, r, g, b, 0F, 360F);
    }

    /**
     * Based on ElGatoPro300's code from BBS mod CML edition
     */
    public static void arc3D(BufferBuilder builder, PoseStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg)
    {
        int segU = 96;
        int segV = 24;
        double u0 = Math.toRadians(startDeg);
        double uStep = Math.toRadians(sweepDeg / (double) segU);
        double vStep = Math.PI * 2D / (double) segV;

        stack.pushPose();

        if (axis == Axis.X) stack.rotate(com.mojang.math.Axis.ZP.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.rotate(com.mojang.math.Axis.XP.rotation(MathUtils.PI / 2F));

        float tubeR = thickness * 0.5F;
        Matrix4f mat = stack.last().pose();

        for (int iu = 0; iu < segU; iu++)
        {
            double u1 = u0 + uStep * iu;
            double u2 = u0 + uStep * (iu + 1);

            for (int iv = 0; iv < segV; iv++)
            {
                double v1 = vStep * iv;
                double v2 = vStep * (iv + 1);
                double cos1 = radius + tubeR * Math.cos(v1);
                double cos2 = radius + tubeR * Math.cos(v2);

                float x11 = (float) (cos1 * Math.cos(u1));
                float z11 = (float) (cos1 * Math.sin(u1));
                float y11 = (float) (tubeR * Math.sin(v1));

                float x12 = (float) (cos2 * Math.cos(u1));
                float z12 = (float) (cos2 * Math.sin(u1));
                float y12 = (float) (tubeR * Math.sin(v2));

                float x21 = (float) (cos1 * Math.cos(u2));
                float z21 = (float) (cos1 * Math.sin(u2));
                float y21 = (float) (tubeR * Math.sin(v1));

                float x22 = (float) (cos2 * Math.cos(u2));
                float z22 = (float) (cos2 * Math.sin(u2));
                float y22 = (float) (tubeR * Math.sin(v2));

                builder.addVertex(mat, x11, y11, z11).setColor(r, g, b, 1F);
                builder.addVertex(mat, x12, y12, z12).setColor(r, g, b, 1F);
                builder.addVertex(mat, x22, y22, z22).setColor(r, g, b, 1F);

                builder.addVertex(mat, x11, y11, z11).setColor(r, g, b, 1F);
                builder.addVertex(mat, x22, y22, z22).setColor(r, g, b, 1F);
                builder.addVertex(mat, x21, y21, z21).setColor(r, g, b, 1F);
            }
        }

        stack.popPose();
    }

    public static void sphere(BufferBuilder builder, PoseStack stack, float radius, int rings, int sectors, float r, float g, float b, float a)
    {
        float constR = 1.0F / (float) (rings - 1);
        float constS = 1.0F / (float) (sectors - 1);
        
        Matrix4f mat = stack.last().pose();
        
        for (int i = 0; i < rings - 1; i++)
        {
            for (int j = 0; j < sectors - 1; j++)
            {
                float y0 = (float) Math.sin(-Math.PI / 2 + Math.PI * i * constR);
                float x0 = (float) Math.cos(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * i * constR);
                float z0 = (float) Math.sin(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * i * constR);
                
                float y1 = (float) Math.sin(-Math.PI / 2 + Math.PI * (i + 1) * constR);
                float x1 = (float) Math.cos(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                float z1 = (float) Math.sin(2 * Math.PI * j * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                
                float y2 = (float) Math.sin(-Math.PI / 2 + Math.PI * (i + 1) * constR);
                float x2 = (float) Math.cos(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                float z2 = (float) Math.sin(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * (i + 1) * constR);
                
                float y3 = (float) Math.sin(-Math.PI / 2 + Math.PI * i * constR);
                float x3 = (float) Math.cos(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * i * constR);
                float z3 = (float) Math.sin(2 * Math.PI * (j + 1) * constS) * (float) Math.sin(Math.PI * i * constR);
                
                builder.addVertex(mat, x0 * radius, y0 * radius, z0 * radius).setColor(r, g, b, a);
                builder.addVertex(mat, x1 * radius, y1 * radius, z1 * radius).setColor(r, g, b, a);
                builder.addVertex(mat, x2 * radius, y2 * radius, z2 * radius).setColor(r, g, b, a);
                
                builder.addVertex(mat, x0 * radius, y0 * radius, z0 * radius).setColor(r, g, b, a);
                builder.addVertex(mat, x2 * radius, y2 * radius, z2 * radius).setColor(r, g, b, a);
                builder.addVertex(mat, x3 * radius, y3 * radius, z3 * radius).setColor(r, g, b, a);
            }
        }
    }
}
