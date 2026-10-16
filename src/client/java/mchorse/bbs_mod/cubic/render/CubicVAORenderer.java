package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.util.LightCoordsUtil;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class CubicVAORenderer extends CubicCubeRenderer
{
    private RenderPipeline pipeline;
    private ModelInstance model;
    private Function<String, Link> textureResolver;

    /**
     * Non-null puts the renderer in hybrid mode (a welded model): these groups — and any group with no baked VAO —
     * fall through to the CPU immediate path so their welded cubes can deform against a live neighbour, while every
     * other group still rides its VAO on the GPU. Null keeps the plain all-VAO behaviour for unwelded models.
     */
    private Set<ModelGroup> weldedGroups;

    public CubicVAORenderer(RenderPipeline pipeline, ModelInstance model, int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys, Function<String, Link> textureResolver)
    {
        super(light, overlay, stencilMap, shapeKeys);

        this.pipeline = pipeline;
        this.model = model;
        this.textureResolver = textureResolver;
    }

    public void setWeldedGroups(Set<ModelGroup> weldedGroups)
    {
        this.weldedGroups = weldedGroups;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, PoseStack stack, ModelGroup group, Model model)
    {
        Map<String, ModelVAO> groupVaos = this.model.getVaos().get(group);

        if (this.weldedGroups != null)
        {
            /* A welded bone tessellates on the CPU only while its seam actually bends — at rest it rides
             * its VAO like everything else. Groups with no VAO (shape-keyed meshes) always render immediate. */
            boolean welded = this.weldedGroups.contains(group) && WeldBinding.hasActiveSeam(this.welds, group);

            if (welded || groupVaos == null || groupVaos.isEmpty())
            {
                return super.renderGroup(builder, stack, group, model);
            }
        }

        if (groupVaos == null || groupVaos.isEmpty() || !group.isVisible())
        {
            return false;
        }

        /* The bone's overlay over the form's; picking keeps the plain one (BBS FS f95662b30) */
        int overlay = this.stencilMap == null ? mchorse.bbs_mod.forms.renderers.utils.FormUeberlagerung.knochen(this.model.form, group.overlay, this.overlay) : this.overlay;
        float r = this.r * group.color.r;
        float g = this.g * group.color.g;
        float b = this.b * group.color.b;
        float a = this.a * group.color.a;
        int light = this.light;

        if (this.stencilMap != null)
        {
            light = this.stencilMap.increment ? group.index : 0;
        }
        else
        {
            int u = (int) Lerps.lerp(light & '\uffff', net.minecraft.util.LightCoordsUtil.pack(15, 0), MathUtils.clamp(group.lighting, 0F, 1F));
            int v = light >> 16 & '\uffff';

            light = u | v << 16;
        }

        /* One draw per material; bind that material's resolved texture before each. */
        for (Map.Entry<String, ModelVAO> entry : groupVaos.entrySet())
        {
            Texture texture = null;

            if (this.textureResolver != null)
            {
                Link link = this.textureResolver.apply(entry.getKey());

                if (link != null)
                {
                    texture = BBSModClient.getTextures().getTexture(link);
                    BBSModClient.getTextures().bindTexture(texture);
                }
            }

            if (texture == null)
            {
                /* No per-material override — the draw uses the form's base texture bound earlier. */
                texture = BBSModClient.getTextures().getLastBound();
            }
            if (mchorse.bbs_mod.client.renderer.NativeMorphCapture.suppresses(this.pipeline, entry.getValue(),
                ModelVAORenderer.captureModelView(stack), r, g, b, a, light, overlay, 0, texture, 0F)) continue;

            if (FormTranslucentQueue.needsSplit(this.pipeline, this.stencilMap, texture, a))
            {
                Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                Matrix3f normalMat = new Matrix3f(stack.last().normal());

                /* Erster Durchgang: nur die undurchsichtigen Texel, die schreiben Tiefe.
                 * Die Durchgangsart reist als Parameter mit, statt vorher gesetzt und
                 * danach zurueckgestellt zu werden. */
                ModelVAORenderer.render(this.pipeline, entry.getValue(), modelView, normalMat, r, g, b, a, light, overlay, FormTranslucentQueue.PASS_OPAQUE);

                FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(entry.getValue(), texture, modelView, normalMat, r, g, b, a, light, overlay, this.model.isCulling()));
            }
            else if (FormTranslucentQueue.needsWholeDefer(this.pipeline, this.stencilMap, a))
            {
                /* A uniform colour fade defers the whole draw into the sorted end-of-frame pass
                 * with depth kept on, so the faded model still self-occludes. */
                Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                Matrix3f normalMat = new Matrix3f(stack.last().normal());
                RenderPipeline pipeline = this.pipeline;

                FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(entry.getValue(), () -> pipeline, FormTranslucentQueue.PASS_SINGLE, true, texture, modelView, normalMat, r, g, b, a, light, overlay, this.model.isCulling()));
            }
            else
            {
                /* Der Weg des Auswahl-Durchgangs: needsSplit und needsWholeDefer verlangen
                 * beide stencilMap == null, beim Picken landet der Zug also immer hier.
                 * light traegt den KNOCHEN-Index (oben gesetzt). Der Shader addiert zur Kennung
                 * nur den Knochenteil je Ecke (picker_models.fsh:43), und den schreibt ModelVAO
                 * als Nullen -- jeder Klick auf ein Wuerfelmodell traf darum den ersten Knochen
                 * (gemessen 2026-10-15: Kennung 20 = anchor ueber Kopf, Armen und Beinen).
                 * Ein Wuerfelknochen ist genau ein Zug, also gehoert er hier in die Kennung. */
                ModelVAORenderer.render(this.pipeline, entry.getValue(), stack, r, g, b, a, light, overlay, texture, this.stencilMap == null ? 0 : this.stencilMap.aktuelleBasis + light);
            }
        }

        return false;
    }
}
