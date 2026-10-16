package mchorse.bbs_mod.ui.utils;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.PickZiel;
import mchorse.bbs_mod.graphics.Sammelpass;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo.Handle;
import mchorse.bbs_mod.ui.utils.Gizmo.Op;
import mchorse.bbs_mod.utils.Axis;

/**
 * Der Auswahl-Durchgang des Gizmos: dieselben Griffe, aber als Kennungsfarben in BBS'
 * eigenen Puffer statt sichtbar ins Bild.
 *
 * <h2>Warum das aus Gizmo heraus ist</h2>
 *
 * <p>Gizmo war ueber 1900 Zeilen lang. Dieser Teil hat mit dem sichtbaren Zeichnen nur die
 * GEOMETRIE gemeinsam, nicht den Zweck: hier zaehlt keine Farbe, kein Mischen und keine
 * Tiefe, sondern nur, dass jede Flaeche ihre eindeutige Kennung traegt. Nebeneinander
 * gelesen sahen die beiden Wege verwechselbar aus -- und genau diese Verwechslung ist
 * teuer, weil sie nichts meldet: Kennungen im Bild und eine Auswahl, die ins Leere greift.
 *
 * <p>Der Zustand bleibt im {@link Gizmo}; diese Klasse liest ihn nur.
 */
final class GizmoAuswahl
{
    static void achsenZeichnen(Gizmo gizmo, PoseStack stack, StencilMap map, float axisSize, float axisOffset)
    {
        /* All pick ids go to the same picking target: one render pass instead of one per part. */
        Sammelpass.beginnen();

        try
        {
            kennungenZeichnen(gizmo, stack, map, axisSize, axisOffset);
        }
        finally
        {
            Sammelpass.beenden();
        }
    }

    private static void kennungenZeichnen(Gizmo gizmo, PoseStack stack, StencilMap map, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        Handle active = gizmo.activeDragHandle();

        boolean showMove = gizmo.mode.shows(Op.MOVE) && (active == null || active.op == Op.MOVE || active.op == Op.SCREEN);
        boolean showScale = gizmo.mode.shows(Op.SCALE) && (active == null || active.op == Op.SCALE || active.op == Op.SCALE_ALL);
        boolean showRotate = gizmo.mode.shows(Op.ROTATE) && (active == null || active.op == Op.ROTATE || active.op == Op.VIEW || active.op == Op.TRACKBALL);

        axisSize *= scale * gizmo.combinedInnerScale();
        axisOffset *= scale * thickness;

        /* 26.2: der Tiefentest ist kein globaler Schalter mehr. Dass die Kennungen ohne ihn
         * gezeichnet werden, steht in der Pipeline am Ende dieser Methode. */

        if (showRotate)
        {
            gizmo.updateVbos();

            if (!BBSSettings.rotateHideRings.get())
            {
                /* Match the visual ring thickness (Gizmo#drawRotateHandles) so the
                 * pick tube lines up with the drawn ring. */
                float stencilThickness = 0.02F * scale * thickness;
                float radius = 0.22F * scale;

                if (active == null || active == Handle.ROTATE_Z) gizmo.drawOccludedRing(stack, Axis.Z, radius, stencilThickness, Gizmo.STENCIL_ROTATE_Z / 255F, 0F, 0F);
                if (active == null || active == Handle.ROTATE_X) gizmo.drawOccludedRing(stack, Axis.X, radius, stencilThickness, Gizmo.STENCIL_ROTATE_X / 255F, 0F, 0F);
                if (active == null || active == Handle.ROTATE_Y) gizmo.drawOccludedRing(stack, Axis.Y, radius, stencilThickness, Gizmo.STENCIL_ROTATE_Y / 255F, 0F, 0F);
            }

            /* View ring stays pickable even when the rings are hidden (see drawAxes visual pass).
             * Uses the same ring VBO as the visual so the pick matches its thickness.
             *
             * ⚠️ Der Blickring ist ein GEHALTENES Netz (GizmoRinge.billboardZeichnen) und holt
             * sein Ziel dort selbst bei PickZiel -- der Weg durch Gizmo.drawCachedRingBillboard
             * reicht keines durch. Frueher fehlte es ganz: die Kennung ging ins Hauptbild, also
             * ein dunkelroter Fleck IM BILD und ein Ring, den der Klick nicht fand. Der Aufruf
             * hier prueft nur, dass ein Ziel angemeldet ist -- fehlt es, scheitert der
             * Auswahl-Durchgang LAUT statt still wieder ins Bild zu malen. */
            if (active == null || active == Handle.VIEW)
            {
                PickZiel.pflicht();

                gizmo.drawCachedRingBillboard(stack, Gizmo.STENCIL_VIEW / 255F, 0F, 0F, 1F);
            }
        }

        if (showMove || showScale)
        {

            /* The bar reads as move when move is on screen (combined) and as scale
             * only when scale stands alone; the scale handle then lives on the end
             * cubes, so move and scale never share an id under the cursor. */
            Handle barX = showMove ? Handle.MOVE_X : Handle.SCALE_X;
            Handle barY = showMove ? Handle.MOVE_Y : Handle.SCALE_Y;
            Handle barZ = showMove ? Handle.MOVE_Z : Handle.SCALE_Z;
            Handle planeXZ = showMove ? Handle.MOVE_XZ : Handle.SCALE_XZ;
            Handle planeXY = showMove ? Handle.MOVE_XY : Handle.SCALE_XY;
            Handle planeZY = showMove ? Handle.MOVE_ZY : Handle.SCALE_ZY;

            /* Dieser Durchgang schreibt Kennungen in BBS' Auswahl-Puffer, nicht ins Bild.
             * Wohin genau, sagt PickZiel -- angemeldet von StencilFormFramebuffer.apply().
             * Fehlt die Anmeldung, wirft PickZiel.pflicht(): Kennungen im Bild und eine
             * Auswahl, die ins Leere greift, waere ein doppelter stiller Fehler. */
            ByteBufferBuilder eckenSpeicher = new ByteBufferBuilder(8192);
            BufferBuilder builder = SofortZeichner.beginnen(eckenSpeicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            if (active == null || active == barX) Draw.fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, barX.index / 255F, 0F, 0F);
            if (active == null || active == barY) Draw.fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, barY.index / 255F, 0F, 0F);
            if (active == null || active == barZ) Draw.fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, barZ.index / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, 0F, 0F, 0F);

            /* Screen-space handle hitbox: drawn before the planes so they win the pick
             * where they overlap (planes overlay the cube). Matches the visual cube. */
            if (showMove && (active == null || active == Handle.SCREEN))
            {
                float screenHalf = Gizmo.SCREEN_CUBE_HALF * scale * thickness;

                Draw.fillBox(builder, stack, -screenHalf, -screenHalf, -screenHalf, screenHalf, screenHalf, screenHalf, Gizmo.STENCIL_SCREEN / 255F, 0F, 0F);
            }

            /* Uniform-scale hitbox: matches the visual centre cube in scale-only mode. */
            if (showScale && !showMove && (active == null || active == Handle.SCALE_ALL))
            {
                float scaleAllHalf = Gizmo.SCREEN_CUBE_HALF * scale * thickness;

                Draw.fillBox(builder, stack, -scaleAllHalf, -scaleAllHalf, -scaleAllHalf, scaleAllHalf, scaleAllHalf, scaleAllHalf, Gizmo.STENCIL_SCALE_ALL / 255F, 0F, 0F);
            }

            /* The plane quad's footprint is a fixed fraction of the axis length,
             * independent of axesThickness — thickness only fattens the bars and
             * the flat slab depth, not how big the two-axis plane reads. */
            float planeStart = axisSize * 0.2F;
            float planeEnd = planeStart + axisSize * 0.2F;
            float planeThickness = axisOffset * 0.5F;

            if (active == null || active == planeXZ) Draw.fillBox(builder, stack, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, planeXZ.index / 255F, 0F, 0F);
            if (active == null || active == planeXY) Draw.fillBox(builder, stack, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, planeXY.index / 255F, 0F, 0F);
            if (active == null || active == planeZY) Draw.fillBox(builder, stack, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, planeZY.index / 255F, 0F, 0F);

            if (showScale)
            {
                float cubeHalf = Gizmo.SCALE_CUBE_HALF * scale * thickness;

                if (active == null || active == Handle.SCALE_X) Draw.fillBox(builder, stack, axisSize - cubeHalf, -cubeHalf, -cubeHalf, axisSize + cubeHalf, cubeHalf, cubeHalf, Gizmo.STENCIL_SCALE_X / 255F, 0F, 0F);
                if (active == null || active == Handle.SCALE_Y) Draw.fillBox(builder, stack, -cubeHalf, axisSize - cubeHalf, -cubeHalf, cubeHalf, axisSize + cubeHalf, cubeHalf, Gizmo.STENCIL_SCALE_Y / 255F, 0F, 0F);
                if (active == null || active == Handle.SCALE_Z) Draw.fillBox(builder, stack, -cubeHalf, -cubeHalf, axisSize - cubeHalf, cubeHalf, cubeHalf, axisSize + cubeHalf, Gizmo.STENCIL_SCALE_Z / 255F, 0F, 0F);
            }

            /* Kein Mischen (Kennungen duerfen nicht verrechnet werden), keine Tiefenpruefung
             * (frueher disableDepthTest davor), Culling an, keine Farbmodulation. */
            SofortZeichner.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, false, false, true),
                builder.build(), RenderSystem.getModelViewMatrixCopy(), null, null,
                mchorse.bbs_mod.graphics.PickZiel.pflicht(), null);

            eckenSpeicher.close();
        }

    }


    private GizmoAuswahl()
    {}
}
