package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.DebugOverlay;
import mchorse.bbs_mod.cubic.render.RigWidgets;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Der Auswahlpuffer-Pass der IK-Debugmarken — je Ziel/Pol eine eigene Kennung.
 *
 * <p>Aus {@link ModelIKDebug} herausgeloest, weil die Datei die 800-Zeilen-Grenze
 * erreicht hatte und der Waechter zu Recht nur noch Schrumpfen zulaesst. Der Schnitt
 * liegt an der natuerlichen Naht: das Zeichnen fuers Auge und das Zeichnen fuer den
 * Klick teilen sich nur die Rahmen-Sammlung, sonst nichts.
 */
public final class ModelIKStencil
{
    private ModelIKStencil()
    {}

    /**
     * Mirrors the goals into the picking pass: a pickable marker at each goal,
     * registered under the chain's target bone. Must run after the model's bones
     * are registered so the goal ids fall right after them — the marker encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(PoseStack stack, IModel model, MapType ikData, StencilMap stencilMap, Form form)
    {
        ValueIKDebug config = BBSSettings.ikDebug;
        boolean targets = config.target.visible.get();
        boolean poles = config.pole.visible.get();

        /* Messfuehler: laeuft der Marker-Pick-Pass ueberhaupt, und woran scheitert er? */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("ikdbg.stencil",
                "enabled=" + config.enabled.get() + " targets=" + targets + " poles=" + poles
                    + " model=" + (model != null) + " ik=" + (ikData != null) + " map=" + (stencilMap != null)
                    + " von=" + mchorse.bbs_mod.graphics.Messpunkt.rufer(4));
        }

        if (!config.enabled.get() || (!targets && !poles) || model == null || ikData == null || stencilMap == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = ModelIKDebug.collectFrames(model, compiled);

        /* Pick-Pass = ID-Farben: kein Blend (gemischte ID = falsche ID), ohne Cull
         * (fehlende Flaeche = Loch im Klickziel), Tiefe wie der Sichtpass. */
        stack.pushPose();

        if (model instanceof BOBJModel)
        {
            stack.mulPose(Axis.YP.rotation(MathUtils.PI));
        }

        /* Control-Rig vorhanden -> die Original-Shapes sind die Klickziele, nicht die Kloetze. */
        java.util.List<RigCtrl.Eintrag> rig = model instanceof BOBJModel bm && form instanceof mchorse.bbs_mod.forms.forms.ModelForm mf
            ? RigCtrl.fuer(bm.getArmature(), mf.model.get()) : null;

        /* Ab hier Widget-Kennungen — der Umriss-Shader endet an dieser Grenze. */
        stencilMap.widgetBeginn();

        {
            ByteBufferBuilder speicher = mchorse.bbs_mod.cubic.render.ControlVertexBatch.speicher(1);
            BufferBuilder builder = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            if (rig != null)
            {
                mchorse.bbs_mod.cubic.render.RigCtrlZeichner.stencilPass(builder, stack, stencilMap, form, ((BOBJModel) model).getArmature(), rig);
            }

            for (ModelIKCache.CompiledChain chain : compiled.chains())
            {
                if (rig != null)
                {
                    break;
                }

                float unit = ModelIKDebug.chainUnit(frames, chain.chainRootToEffector());

                if (targets)
                {
                    Vector3f goal = ModelIKDebug.position(frames, chain.target());

                    if (goal != null)
                    {
                        pickMarker(builder, stack, stencilMap, form, config.target, goal, unit, chain.target());
                    }
                }

                if (poles && chain.poleTarget() != null && !chain.poleTarget().isEmpty())
                {
                    Vector3f pole = ModelIKDebug.position(frames, chain.poleTarget());

                    if (pole != null)
                    {
                        pickMarker(builder, stack, stencilMap, form, config.pole, pole, unit, chain.poleTarget());
                    }
                }
            }

            /* ⛔ INS PICKZIEL, NICHT INS HAUPTBILD (2026-08-13 gemessen): die 5-Arg-Variante
             * zeichnet ins Hauptbild — die Kennungen der Marker kamen dort unsichtbar-dunkel
             * an und der Auswahlpuffer blieb ohne sie; ein Klick auf die Box fand NIE etwas.
             * pflicht() wirft laut, wenn kein Auswahl-Durchgang angemeldet ist. */
            SofortZeichner.zeichnen(
                FormPipelines.holen(PrimitiveTopology.TRIANGLES, false, !config.xray.get(), false),
                builder.build(),
                ModelIKDebug.modellansicht(),
                null,
                null,
                mchorse.bbs_mod.graphics.PickZiel.pflicht(),
                mchorse.bbs_mod.graphics.PickZiel.tiefe());
        }

        stack.popPose();
    }

    /** Draws one pickable marker — the element's own shape and size — encoding the next stencil id, and claims it for {@code bone}. */
    private static void pickMarker(BufferBuilder builder, PoseStack stack, StencilMap stencilMap, Form form, ValueDebugElement element, Vector3f p, float unit, String bone)
    {
        int id = stencilMap.objectIndex;
        float[] col = {(id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F};

        /* Wire-Widgets picken ueber ihr Innenvolumen in gleicher Groesse — Blenders
         * Klick-Toleranz-Aequivalent; Haarlinien waeren im Stencil nur Luecken. */
        int shape = element.shape.get();

        DebugOverlay.marker(builder, stack, RigWidgets.isWire(shape) ? ValueDebugElement.SHAPE_CUBE : shape, p, unit * element.size.get(), col, 1F);

        stencilMap.addPicking(form, bone);
    }
}
