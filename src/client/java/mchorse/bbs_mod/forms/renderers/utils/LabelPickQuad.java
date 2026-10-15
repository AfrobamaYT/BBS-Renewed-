package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.PickZiel;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gui.Font;

import java.util.List;

/** A text object's bounds are picked as one opaque ID quad, like the existing gizmo handles. */
public final class LabelPickQuad
{
    private LabelPickQuad()
    {}

    public static void render(LabelForm form, FormRenderingContext context, Font font)
    {
        Color color = new Color().set(context.color, true);

        FormColorBlend.blend(color, form.color.get(), form.additiveColor.get());
        if (color.a < 0.1F)
        {
            return;
        }

        String content = StringUtils.processColoredText(form.text.get());
        List<String> lines = form.max.get() <= 10 ? List.of(content) : FontRenderer.wrap(font, content, form.max.get());
        int width = 1;

        for (String line : lines)
        {
            width = Math.max(width, font.width(line) - 1);
        }
        int height = Math.max(1, font.lineHeight - 2 + Math.max(0, lines.size() - 1) * 12);
        int x = (int) (-width * form.anchorX.get());
        int y = (int) (-height * form.anchorY.get());
        float margin = Math.max(0F, form.offset.get());
        int index = context.getPickingIndex();
        float r = (index & 0xFF) / 255F;
        float g = ((index >> 8) & 0xFF) / 255F;
        float b = ((index >> 16) & 0xFF) / 255F;

        try (ByteBufferBuilder memory = new ByteBufferBuilder(6 * 16))
        {
            BufferBuilder vertices = SofortZeichner.beginnen(memory, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            Draw.fillQuad(vertices, context.stack, x - margin, y - margin, 0F,
                x + width + margin, y - margin, 0F, x + width + margin, y + height + margin, 0F,
                x - margin, y + height + margin, 0F, r, g, b, 1F);
            SofortZeichner.zeichnen(FormPipelines.holen(VertexFormat.Mode.TRIANGLES, false, true, false),
                vertices.buildOrThrow(), new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()), null, null,
                PickZiel.pflicht(), PickZiel.tiefe());
        }
    }
}
