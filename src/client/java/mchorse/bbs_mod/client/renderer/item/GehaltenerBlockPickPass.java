package mchorse.bbs_mod.client.renderer.item;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Separate exact-geometry selection pass; the normal gizmo/body stencil is never overwritten. */
final class GehaltenerBlockPickPass
{
    private static final StencilFormFramebuffer ZIEL = new StencilFormFramebuffer();
    private static Field basis;

    static GehaltenerBlockBild.Bild treffen(UIFilmPanel panel, UIContext context, List<GehaltenerBlockBild.Bild> bilder)
    {
        Framebuffer vorher = panel.getController().getStencil().getFramebuffer();
        Area area = panel.preview.getViewport();
        int w = vorher == null ? Math.max(1, area.w) : vorher.getMainTexture().width;
        int h = vorher == null ? Math.max(1, area.h) : vorher.getMainTexture().height;
        ZIEL.setup(Link.bbs("held-model-block-pick"));
        ZIEL.resize(w, h);
        GpuBufferSlice alteProjektion = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType alterTyp = RenderSystem.getProjectionType();
        Map<ModelFormRenderer, Integer> alteBasis = new IdentityHashMap<>();
        List<ProjectionMatrixBuffer> pufferListe = new ArrayList<>();
        boolean aktiv = false;
        RenderSystem.getModelViewStack().pushMatrix();

        try
        {
            ZIEL.apply();
            aktiv = true;
            if (vorher != null && vorher.depthView() != null)
            {
                RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(vorher.depthView().texture(),
                    ZIEL.getFramebuffer().depthView().texture(), 0, 0, 0, 0, 0, w, h);
            }

            StencilMap map = new StencilMap();
            map.setup();
            map.setIncrement(false);
            boolean egoTiefe = false;

            /* World items first, then foreground hands, matching vanilla's separate hand depth. */
            for (int phase = 0; phase < 2; phase++)
            {
                for (int i = 0; i < bilder.size(); i++)
                {
                    GehaltenerBlockBild.Bild bild = bilder.get(i);
                    boolean ego = bild.art() == mchorse.bbs_mod.forms.renderers.FormRenderType.ITEM_FP;
                    if (ego != (phase == 1))
                    {
                        continue;
                    }
                    if (ego && !egoTiefe)
                    {
                        GpuTexture tiefe = ZIEL.getFramebuffer().depthView().texture();
                        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(tiefe, 0D);
                        egoTiefe = true;
                    }

                    /* A separate buffer per draw: ProjectionMatrixBuffer uploads overwrite their slice. */
                    ProjectionMatrixBuffer puffer = new ProjectionMatrixBuffer("bbs-held-pick-projection");
                    pufferListe.add(puffer);
                    RenderSystem.setProjectionMatrix(puffer.getBuffer(bild.projektion()), ProjectionType.PERSPECTIVE);
                    RenderSystem.getModelViewStack().set(bild.modelView());
                    int id = 256 + i;
                    map.objectIndex = id;
                    map.aktuelleBasis = id;
                    vorbereiten(bild.form(), id, alteBasis);
                    PoseStack stack = new PoseStack();
                    stack.last().pose().set(bild.pose());
                    stack.last().normal().set(bild.normal());
                    FormRenderingContext draw = new FormRenderingContext()
                        .set(bild.art(), bild.entity(), stack, bild.licht(), bild.overlay(), bild.transition()).stencilMap(map);
                    draw.camera.copy(bild.kamera());
                    FormUtilsClient.render(bild.form(), draw);
                }
            }

            ZIEL.unbind();
            aktiv = false;
            int x = Math.min(w - 1, Math.max(0, (context.mouseX - area.x) * w / Math.max(1, area.w)));
            int y = Math.min(h - 1, Math.max(0, (area.ey() - context.mouseY) * h / Math.max(1, area.h)));
            ZIEL.pick(x, y);
            int index = ZIEL.getIndex() - 256;
            return index >= 0 && index < bilder.size() ? bilder.get(index) : null;
        }
        finally
        {
            if (aktiv)
            {
                ZIEL.unbind();
            }
            for (Map.Entry<ModelFormRenderer, Integer> entry : alteBasis.entrySet())
            {
                setBasis(entry.getKey(), entry.getValue());
            }
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(alteProjektion, alterTyp);
            for (ProjectionMatrixBuffer puffer : pufferListe)
            {
                puffer.close();
            }
        }
    }

    private static void vorbereiten(Form form, int id, Map<ModelFormRenderer, Integer> vorher)
    {
        FormRenderer renderer = FormUtilsClient.getRenderer(form);
        if (renderer instanceof ModelFormRenderer model)
        {
            try
            {
                if (basis == null)
                {
                    basis = ModelFormRenderer.class.getDeclaredField("stencilBasis");
                    basis.setAccessible(true);
                }
                vorher.putIfAbsent(model, basis.getInt(model));
                basis.setInt(model, id);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException("Cannot prepare held-model pick id", e);
            }
        }
        for (BodyPart part : form.parts.getAllTyped())
        {
            if (part.getForm() != null)
            {
                vorbereiten(part.getForm(), id, vorher);
            }
        }
    }

    private static void setBasis(ModelFormRenderer model, int value)
    {
        try
        {
            basis.setInt(model, value);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot restore held-model pick id", e);
        }
    }

    private GehaltenerBlockPickPass()
    {}
}
