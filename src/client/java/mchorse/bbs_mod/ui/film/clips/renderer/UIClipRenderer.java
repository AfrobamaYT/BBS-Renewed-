package mchorse.bbs_mod.ui.film.clips.renderer;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Envelope;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix4f;
import org.joml.Vector2f;

public class UIClipRenderer <T extends Clip> implements IUIClipRenderer<T>
{
    private static final Color ENVELOPE_COLOR = new Color(0, 0, 0, 0.25F);

    /* Temporary objects */
    private static Vector2f vector = new Vector2f();
    private static Vector2f previous = new Vector2f();

    @Override
    public void renderClip(UIContext context, UIClips clips, T clip, Area area, boolean selected, boolean current)
    {
        int y = area.y;
        int h = area.h;

        int left = area.x;
        int right = area.ex();

        ClipFactoryData data = clips.getFactory().getData(clip);
        /* ⛔ Gedaempfte Typfarbe. Die Rohfarben sind grell (Idle = volles Gruen) und stachen
         * in der neuen, neutralgrauen Oberflaeche heraus wie ein Fremdkoerper. Blender faerbt
         * seine Streifen ebenfalls nach Typ, aber gedeckt: die Farbe soll den Typ ERKENNBAR
         * machen, nicht das Auge fangen. */
        int color = Colors.A100 | gedaempft(data.color);

        if (current)
        {
            int shadow = data.color & Colors.RGB;

            context.batcher.dropShadow(left + 2, y + 2, right - 2, y + h - 2, 8, Colors.A75 | shadow, shadow);
        }

        if (clip.enabled.get())
        {
            this.renderBackground(context, color, clip, area, selected, current);
        }
        else
        {
            context.batcher.iconArea(Icons.DISABLED, color, left, y, (right - left), h);
        }

        /* Umrandung nur fuer den gewaehlten Clip deutlich; sonst eine dunkle Kante, die den
         * Streifen abgrenzt, ohne ein zweites Gitter zu zeichnen. */
        context.batcher.outline(left, y, right, y + h,
            selected ? Colors.WHITE : (Colors.A75 | 0x101010));

        if (right - left > 10 && clip.envelope.enabled.get())
        {
            this.renderEnvelope(context, clip.envelope, clip.duration.get(), left + 1, y + 1, right - 1, y + h - 1);
        }

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(clips.getClipDisplayName(clip), right - 6 - left);

        boolean alignTop = h >= 28;
        int labelY = alignTop ? y + 3 : y + (h - font.getHeight()) / 2;
        float iconAnchorY = alignTop ? 0F : 0.5F;
        int iconY = alignTop ? y + 3 : y + h / 2;

        if (right - left >= 20)
        {
            context.batcher.icon(data.icon, Colors.mulA(Colors.mulRGB(Colors.WHITE, 0.75F), 0.5F), right - 2, iconY, 1F, iconAnchorY);
        }

        if (!label.isEmpty())
        {
            context.batcher.textShadow(label, left + 5, labelY);
        }
    }

    protected void renderBackground(UIContext context, int color, T clip, Area area, boolean selected, boolean current)
    {
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), color);
    }

    /**
     * Render envelope's preview (either through keyframes or simple)
     */
    private void renderEnvelope(UIContext context, Envelope envelope, int duration, int x1, int y1, int x2, int y2)
    {
        Matrix4f matrix = new Matrix4f(context.batcher.pose().last().pose());

        /* ⛔ HIER STAND EIN SOFORTZUG (2026-08-01). Die Huelle zeichnet IN die
         * Oberflaeche: 26.2 sammelt die erst und zeichnet sie danach, ein eigener Durchgang
         * landet also unter dem gesamten GUI und wird vom Clip-Hintergrund uebermalt.
         *
         * Mischen bleibt an -- ENVELOPE_COLOR ist Schwarz mit Alpha 0.25 und soll den Clip
         * durchscheinen lassen; Tiefe und Cull waren nie eingeschaltet.
         *
         * ⛔ Der Bereich ist das Huellen-Rechteck selbst und NICHT null (null = gecullt);
         * mindestens 1 Pixel breit/hoch, weil ein leeres Rechteck nichts zeichnen wuerde. */
        context.batcher.netz(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false), (ecken) ->
        {
            if (envelope.keyframes.get())
            {
                if (!envelope.channel.isEmpty())
                {
                    this.renderEnvelopesKeyframes(ecken, matrix, envelope.channel, duration, x1, y1, x2, y2);
                }
            }
            else
            {
                this.renderSimpleEnvelope(ecken, matrix, envelope, duration, x1, y1, x2, y2);
            }
        }, new ScreenRectangle(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1)));
    }

    /**
     * Render keyframe based envelope.
     */
    private void renderEnvelopesKeyframes(VertexConsumer builder, Matrix4f matrix, KeyframeChannel<Double> channel, int duration, int x1, int y1, int x2, int y2)
    {
        Keyframe<Double> prevKeyframe = null;
        int c = ENVELOPE_COLOR.getARGBColor();

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (prevKeyframe != null)
            {
                Vector2f point = this.calculateEnvelopePoint(vector, (int) keyframe.getTick(), keyframe.getValue().floatValue(), duration, x1, y1, x2, y2);
                Vector2f prevPoint = this.calculateEnvelopePoint(previous, (int) prevKeyframe.getTick(), prevKeyframe.getValue().floatValue(), duration, x1, y1, x2, y2);

                this.shadeAbove(builder, matrix, c, prevPoint.x, prevPoint.y, point.x, point.y, y1);
            }
            else if (keyframe.getTick() > 0)
            {
                /* Before the first keyframe the envelope holds its value */
                Vector2f point = this.calculateEnvelopePoint(vector, (int) keyframe.getTick(), keyframe.getValue().floatValue(), duration, x1, y1, x2, y2);

                this.shadeAbove(builder, matrix, c, x1, point.y, point.x, point.y, y1);
            }

            prevKeyframe = keyframe;
        }

        /* Finish the end */
        if (prevKeyframe != null && prevKeyframe.getTick() < duration)
        {
            Vector2f point = this.calculateEnvelopePoint(vector, (int) prevKeyframe.getTick(), prevKeyframe.getValue().floatValue(), duration, x1, y1, x2, y2);

            this.shadeAbove(builder, matrix, c, point.x, point.y, x2, point.y, y1);
        }
    }

    /**
     * Render simple envelope by sampling its actual shape, so the fade in/out edges
     * follow the chosen {@code pre}/{@code post} interpolation instead of straight lines.
     */
    protected void renderSimpleEnvelope(VertexConsumer builder, Matrix4f matrix, Envelope envelope, int duration, int x1, int y1, int x2, int y2)
    {
        int width = x2 - x1;
        int height = y2 - y1;

        if (width <= 0)
        {
            return;
        }

        int c = ENVELOPE_COLOR.getARGBColor();
        int steps = Math.min(width, 200);

        float prevX = x1;
        float prevY = y1 + height * (1 - MathUtils.clamp(envelope.factor(duration, 0), 0F, 1F));

        for (int i = 1; i <= steps; i++)
        {
            float f = i / (float) steps;
            float x = x1 + width * f;
            float y = y1 + height * (1 - MathUtils.clamp(envelope.factor(duration, duration * f), 0F, 1F));

            this.shadeAbove(builder, matrix, c, prevX, prevY, x, y, y1);

            prevX = x;
            prevY = y;
        }
    }

    /**
     * Shade the part of the clip above one segment of the envelope, i.e. the part that's faded
     * away, so a clip without fades looks untouched (BBS FS e24692991).
     */
    private void shadeAbove(VertexConsumer builder, Matrix4f matrix, int c, float ax, float ay, float bx, float by, float top)
    {
        builder.addVertex(matrix, ax, top, 0F).setColor(c);
        builder.addVertex(matrix, ax, ay, 0F).setColor(c);
        builder.addVertex(matrix, bx, by, 0F).setColor(c);

        builder.addVertex(matrix, ax, top, 0F).setColor(c);
        builder.addVertex(matrix, bx, by, 0F).setColor(c);
        builder.addVertex(matrix, bx, top, 0F).setColor(c);
    }

    protected Vector2f calculateEnvelopePoint(Vector2f vector, int tick, float value, int duration, int x1, int y1, int x2, int y2)
    {
        int width = x2 - x1;
        int height = y2 - y1;

        /* 1 - value due to higher numbers are lower on the screen */
        vector.x = MathUtils.clamp((tick / (float) duration) * width + x1, x1, x2);
        vector.y = (1 - MathUtils.clamp(value, 0, 1)) * height + y1;

        return vector;
    }

    @Override
    public String getDefaultLabel(UIClips clips, T clip)
    {
        Link type = clips.getFactory().getType(clip);
        return UIKeys.C_CLIP.get(type).get();
    }

    /**
     * Typfarbe auf Blender-Mass: Saettigung herunter, Helligkeit auf mittlere Lage.
     *
     * <p>Ohne das leuchten die Clips in der neutralgrauen Oberflaeche wie Signalfarben. Der
     * Farbton bleibt, damit man den Clip-Typ weiter am Streifen erkennt.</p>
     */
    private static int gedaempft(int farbe)
    {
        int r = (farbe >> 16) & 0xFF;
        int g = (farbe >> 8) & 0xFF;
        int b = farbe & 0xFF;
        /* Grauwert nach Rec. 709 — ein naiver Mittelwert verschoebe Gruen nach hell. */
        int grau = (int) (0.2126F * r + 0.7152F * g + 0.0722F * b);

        /* 55 % zur Grauachse, dann auf ein mittleres Niveau gezogen. */
        r = (int) ((r * 0.45F + grau * 0.55F) * 0.72F);
        g = (int) ((g * 0.45F + grau * 0.55F) * 0.72F);
        b = (int) ((b * 0.45F + grau * 0.55F) * 0.72F);

        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
