package mchorse.bbs_mod.forms.renderers;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.LabelPickQuad;
import mchorse.bbs_mod.forms.renderers.utils.WorldLabelPhases;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class LabelFormRenderer extends FormRenderer<LabelForm>
{
    public static void fillQuad(BufferBuilder builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.last().pose();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a).setUv(0F, 0F);
        builder.addVertex(matrix4f, x2, y2, z2).setColor(r, g, b, a).setUv(0F, 0F);
        builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a).setUv(0F, 0F);
        builder.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a).setUv(0F, 0F);
        builder.addVertex(matrix4f, x3, y3, z3).setColor(r, g, b, a).setUv(0F, 0F);
        builder.addVertex(matrix4f, x4, y4, z4).setColor(r, g, b, a).setUv(0F, 0F);
    }

    public LabelFormRenderer(LabelForm form)
    {
        super(form);
    }

    /** Die Vorschau ist reine Textausgabe ueber den Batcher — sie gehoert nicht in die
     *  eingereihte Kachel, siehe {@link FormRenderer#renderUI}. */
    @Override
    protected boolean flacheVorschau()
    {
        return true;
    }

    /**
     * The label's own font, or the default one when it has none or it cannot load. {@code scale} is how
     * many screen pixels a layout unit covers where it is drawn (BBS FS b0224f806).
     */
    private mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer getFont(float scale)
    {
        mchorse.bbs_mod.resources.Link link = this.form.font.get();
        int size = this.form.fontSize.get();
        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font = link == null || link.path.isEmpty() ? null : mchorse.bbs_mod.fonts.FontManager.get().get(link, size < mchorse.bbs_mod.fonts.FontManager.MIN_SIZE ? 9 : size, scale);

        return font == null ? mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D.getDefaultTextRenderer() : font;
    }

    private int getLineHeight(mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font)
    {
        int lineHeight = this.form.lineHeight.get();

        return lineHeight > 0 ? lineHeight : font.getLineHeight();
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = this.form.color.get().getARGBColor();
        String text = StringUtils.processColoredText(this.form.text.get());
        /* The interface draws a layout unit over as many pixels as it is scaled by */
        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font = this.getFont((float) Minecraft.getInstance().getWindow().getGuiScale());
        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer previous = context.batcher.font;

        context.batcher.font = font;

        try
        {
            List<String> wrap = font.wrap(text, x2 - x1 - 4);
            int th = font.getHeight();
            int lineHeight = th + 4;
            int h = th + (wrap.size() - 1) * lineHeight;
            int y = (y2 + y1) / 2 - h / 2;

            for (String s : wrap)
            {
                context.batcher.textShadow(s, x1 + 2, y, color);

                y += lineHeight;
            }
        }
        finally
        {
            context.batcher.font = previous;
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        context.stack.pushPose();

        if (this.form.billboard.get())
        {
            WorldLabelPhases.billboard(context);
        }

        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer renderer = this.getFont(mchorse.bbs_mod.fonts.FontManager.MAX_DETAIL);
        float scale = 1F / 16F;
        int light = context.light;

        MatrixStackUtils.scaleStack(context.stack, scale, -scale, scale);

        /* 26.2: disableCull ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: beidseitig zeichnen. */

        if (context.isPicking())
        {
            try
            {
                LabelPickQuad.render(this.form, context, renderer, this.getLineHeight(renderer));
            }
            finally
            {
                context.stack.popPose();
            }

            return;
        }
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        /* Root world glyphs go to the current frame's collector first. Only their background
         * may defer in AFTER; legacy non-world label groups keep their original ordering. */
        boolean grouped = !context.isPicking() && !WorldLabelPhases.collecting(context) && FormTranslucentQueue.isActive();

        if (grouped)
        {
            Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

            FormTranslucentQueue.beginGroup(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).transformPosition(origin), false);
        }

        if (this.form.max.get() <= 10)
        {
            this.renderString(context, consumers, renderer, light);
        }
        else
        {
            this.renderLimitedString(context, consumers, renderer, light);
        }

        if (grouped)
        {
            FormTranslucentQueue.endGroup();
        }


        /* 26.2: Tiefentest und Cull waren hier nur das Zuruecksetzen des globalen
         * Zustands nach dem disableCull am Anfang von render3D. Einen globalen Zustand
         * gibt es nicht mehr — jeder folgende Zeichenaufruf bringt Blend, Tiefe und Cull
         * in der Pipeline seines RenderType mit. Es ist nichts zurueckzusetzen. */

        context.stack.popPose();
    }

    private void renderString(FormRenderingContext context, CustomVertexConsumerProvider consumers, mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font, int light)
    {
        Font renderer = font.getRenderer();
        String content = StringUtils.processColoredText(this.form.text.get());
        float transition = context.getTransition();
        int w = renderer.width(content) - 1;
        int h = font.getHeight();
        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());

        Color shadowColor = this.form.shadowColor.get().copy();
        Color color = new Color().set(context.color, true);

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());
        shadowColor.mul(context.color);

        if (WorldLabelPhases.text(context) && shadowColor.a > 0)
        {
            context.stack.pushPose();
            context.stack.translate(0F, 0F, -0.1F);
            text(context.stack, consumers, x + this.form.shadowX.get(), y + this.form.shadowY.get(), content, shadowColor.getARGBColor(), light, font.description);
            context.stack.popPose();
        }

        if (WorldLabelPhases.text(context))
        {
            text(context.stack, consumers, x, y, content, color.getARGBColor(), light, font.description);
        }

        this.renderShadow(context, x, y, w, h);
    }

    private void renderLimitedString(FormRenderingContext context, CustomVertexConsumerProvider consumers, mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font, int light)
    {
        Font renderer = font.getRenderer();
        int lineHeight = this.getLineHeight(font);
        float transition = context.getTransition();
        int w = 0;
        int h = font.getHeight();
        String content = StringUtils.processColoredText(this.form.text.get());
        List<String> lines = FontRenderer.wrap(renderer, content, this.form.max.get());

        if (lines.size() <= 1)
        {
            this.renderString(context, consumers, font, light);

            return;
        }

        for (int i = 0; i < lines.size(); i++)
        {
            lines.set(i, lines.get(i).trim());
        }

        for (String line : lines)
        {
            w = Math.max(renderer.width(line) - 1, w);
            h += lineHeight;
        }

        h -= lineHeight;

        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());
        int y2 = y;

        Color shadowColor = this.form.shadowColor.get().copy();

        shadowColor.mul(context.color);

        if (WorldLabelPhases.text(context) && shadowColor.a > 0)
        {
            context.stack.pushPose();
            context.stack.translate(0F, 0F, -0.1F);

            for (String line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.width(line)) * this.form.anchorX.get()) : 0);

                text(context.stack, consumers, x2 + this.form.shadowX.get(), y2 + this.form.shadowY.get(), line, shadowColor.getARGBColor(), light, font.description);

                y2 += lineHeight;
            }

            context.stack.popPose();

            y2 = y;
        }

        Color cColor = new Color().set(context.color, true);

        FormColorBlend.blend(cColor, this.form.color.get(), this.form.additiveColor.get());

        int color = cColor.getARGBColor();

        if (WorldLabelPhases.text(context))
        {
            for (String line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.width(line)) * this.form.anchorX.get()) : 0);

                text(context.stack, consumers, x2, y2, line, color, light, font.description);

                y2 += lineHeight;
            }
        }

        this.renderShadow(context, x, y, w, h);
    }

    /**
     * Eine Textzeile einreichen -- der Ersatz fuer {@code Font.drawInBatch}.
     *
     * <h2>Was 26.2 daran geaendert hat</h2>
     *
     * <p>{@code drawInBatch} gibt es nicht mehr; Text wird beim Sammler EINGEREICHT
     * ({@code submitText}) und vom Bildgraph in seiner Textphase gezeichnet. Die Reihenfolge
     * der Argumente ist eine andere und im Bytecode von {@code TextFeatureRenderer.Submit}
     * nachgelesen: Pose, x, y, Text, Schatten, Anzeigeart, LICHT, FARBE, Hintergrund, Umriss.
     * ⚠️ Licht und Farbe stehen damit VOR dem Hintergrund -- im alten Aufruf war die Farbe
     * das vierte und das Licht das letzte Argument. Wer die Werte mechanisch in derselben
     * Folge stehen laesst, bekommt eine Schrift in Lichtfarbe.
     *
     * <p>⚠️ Der Sammler nimmt einen {@code PoseStack}, keine Matrix: er kopiert die oberste
     * Pose beim Einreichen. Der Stapel darf danach weiterwandern.
     *
     * <p>{@code FormattedCharSequence.forward(text, Style.EMPTY)} ist die genaue Entsprechung
     * der alten String-Ueberladung: beide gehen ueber {@code StringDecomposer.iterateFormatted}
     * und werten damit die §-Codes aus, die {@code StringUtils.processColoredText} erzeugt.
     */
    private static void text(PoseStack stapel, CustomVertexConsumerProvider consumers, float x, float y, String inhalt, int farbe, int licht, net.minecraft.network.chat.FontDescription schrift)
    {
        consumers.sammler().submitText(
            stapel, x, y,
            FormattedCharSequence.forward(inhalt, schrift == null ? Style.EMPTY : Style.EMPTY.withFont(schrift)),
            false,
            Font.DisplayMode.NORMAL,
            licht,
            farbe,
            0,
            0
        );
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h)
    {
        if (!WorldLabelPhases.background(context))
        {
            return;
        }

        float offset = this.form.offset.get();
        Color color = this.form.background.get().copy();

        color.mul(context.color);

        if (color.a <= 0)
        {
            return;
        }

        context.stack.pushPose();
        context.stack.translate(0, 0, -0.2F);


        /* 26.2: ein Viereck aus zwei Dreiecken = 6 Ecken a 16 Byte (POSITION_COLOR).
         * Der Aufbau bleibt gleich, nur Anfang und Ende gehen ueber SofortZeichner. */
        try (ByteBufferBuilder speicher = new ByteBufferBuilder(6 * 16))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            fillQuad(
                builder, context.stack,
                x + w + offset, y - offset, 0,
                x - offset, y - offset, 0,
                x - offset, y + h + offset, 0,
                x + w + offset, y + h + offset, 0,
                color.r, color.g, color.b, color.a
            );

            if (FormTranslucentQueue.isGroupOpen())
            {
                /* 26.2: VertexBuffer ist entfallen, die Aufgabe nicht -- das Netz muss bis zum
                 * Leeren der Warteschlange auf der Karte liegenbleiben. Das macht jetzt
                 * GehaltenesNetz; der Befehl gibt es in release() wieder frei.
                 *
                 * Der Shader war GameRenderer::getPositionColorShader; ihn gibt es nicht mehr
                 * einzeln, er steckt samt Blend, Tiefe und Cull in der Pipeline. Die vier
                 * Schalter sind dieselben wie im Sofort-Zweig unten: POSITION_COLOR, gemischt
                 * (der Hintergrund hat eine eigene Deckkraft), Tiefe an, ohne Cull. */
                GehaltenesNetz netz = new GehaltenesNetz(builder.buildOrThrow());

                FormTranslucentQueue.add(new FormTranslucentQueue.NetzBefehl(
                    netz,
                    () -> FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, false),
                    null,
                    new Matrix4f(RenderSystem.getModelViewMatrixCopy()),
                    null, new Vector3f(FormTranslucentQueue.getSortOrigin()), false, null, null
                ));
            }
            else
            {
                /* Schalter aus den drei Zeilen, die hier vorher standen: enableBlend →
                 * mischen (der Hintergrund hat eine eigene Deckkraft), enableDepthTest →
                 * tiefePruefen. Kein Cull, weil render3D am Anfang disableCull ruft und
                 * das fuer das ganze Label gilt. Die Modellansicht ist dieselbe, die der
                 * Zweig darueber in die Warteschlange legt — die Ecken tragen die Pose
                 * schon, also bleibt nur die globale Modellansicht. */
                SofortZeichner.zeichnen(
                    FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, false),
                    builder.build(),
                    RenderSystem.getModelViewMatrixCopy(),
                    null,
                    null);
                /* MERGE fs-2.5: ihr translucent-queue-zweig uebernommen, unsere
                 * null-absicherung darueber. builder.end() wirft, wenn nichts gezeichnet
                 * wurde; endNullable() gibt dann null. Beides wird gebraucht. */
            }
        }

        context.stack.popPose();
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        if (WorldLabelPhases.collecting(context))
        {
            return;
        }

        boolean worldLabel = context.worldLabel;

        /* Only the root Label is collected twice. Do not draw its attached models twice
         * or change the legacy one-pass behavior of nested Label body parts. */
        context.worldLabel = false;
        try
        {
            super.renderBodyParts(context);
        }
        finally
        {
            context.worldLabel = worldLabel;
        }
    }
}
