package mchorse.bbs_mod.ui.framework.elements.utils;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mchorse.bbs_mod.mixin.client.GuiGraphicsExtractorAccessor;

/**
 * BBS' own 2D drawing. MC 26.2 draws the GUI in TWO phases: {@link GuiGraphicsExtractor}
 * only appends {@link GuiElementRenderState}s to a {@link GuiRenderState}, batched later by
 * {@code GuiRenderer.render()} — no immediate mode, no {@code flush()}.
 *
 * <p>Trap this class exists to avoid: a name-for-name swap to the old Tesselator/
 * BufferUploader path compiles cleanly and draws NOTHING (GL state nobody submits).</p>
 */
public class Batcher2D
{
    private static FontRenderer fontRenderer = new FontRenderer();

    private GuiGraphicsExtractor context;
    private FontRenderer font;

    /** Mirror of the extractor's private scissor stack ({@link #clip}/{@link #unclip} sind
     *  BBS' einzige Eintritte); {@code null} = kein Clipping. */
    private final Deque<ScreenRectangle> scissors = new ArrayDeque<>();

    /**
     * BBS' raeumlicher Stapel in der Oberflaeche. 26.2 machte aus {@code GuiGraphics.pose()}
     * einen {@link org.joml.Matrix3x2fStack} (Flaeche ohne Tiefe/Normalen) — dieser eigene
     * {@link PoseStack} traegt die AKTUELLE 2D-Transformation als 4×4-Basis, damit
     * Modell-/Form-/Item-Vorschauen weiter am Elementversatz (+Scroll/Skalierung) haengen.
     */
    private final PoseStack stapel = new PoseStack();

    public static FontRenderer getDefaultTextRenderer()
    {
        fontRenderer.setRenderer(Minecraft.getInstance().font);

        return fontRenderer;
    }

    public Batcher2D(GuiGraphicsExtractor context)
    {
        this.context = context;
        this.font = getDefaultTextRenderer();
    }

    /**
     * The extractor only exists for the duration of one extraction pass — vanilla hands it to
     * {@code Screen.extractRenderState()} and drops it. UIScreen pushes the current one in
     * here at the start of every pass; see {@code UIScreen.extractRenderState}.
     */
    public void setContext(GuiGraphicsExtractor context)
    {
        this.context = context;
    }

    public GuiGraphicsExtractor getContext()
    {
        return this.context;
    }

    public FontRenderer getFont()
    {
        return this.font;
    }

    /**
     * Der raeumliche Stapel fuer alles, was in der Oberflaeche dreidimensional zeichnet.
     * Ersetzt 1:1 das alte {@code batcher.pose()}.
     *
     * <p>Steht nichts auf dem Stapel, wird die unterste Ebene bei jedem Aufruf frisch mit der
     * 2D-Transformation der Oberflaeche gefuellt — sie aendert sich waehrend eines Bildes
     * staendig, weil jedes Element seinen Versatz aufschiebt.
     *
     * <p>⚠️ Steht dagegen schon etwas darauf ({@code pushPose} eines Aufrufers), bleibt der
     * Stapel unangetastet. Sonst wuerde ein verschachtelter Aufruf die Verschiebung des
     * aeusseren wegwischen — genau das Verhalten, das der geteilte GuiGraphics-Stapel frueher
     * hatte. Wer aufschiebt, muss darum auch wieder abraeumen.
     */
    public PoseStack pose()
    {
        if (this.stapel.isEmpty())
        {
            this.stapel.setIdentity();

            /* ⛔ OHNE EXTRAKTOR KEINE OBERFLAECHEN-TRANSFORMATION (2026-08-04, live
             * abgestuerzt). Der Extraktor lebt nur waehrend des Sammel-Durchlaufs; wer diese
             * Methode ausserhalb ruft -- etwa am Ende des Weltdurchgangs -- traf bisher auf
             * `this.context == null` und riss das Bild mit:
             *
             *   NullPointerException: Cannot invoke "GuiGraphicsExtractor.pose()"
             *       at Batcher2D.pose(Batcher2D.java:129)
             *       at BBSRendering.onWorldRenderEnd(BBSRendering.java:637)
             *   crash-2026-08-04_13.01.54-client.txt
             *
             * Die Einheitsmatrix ist hier die ehrliche Antwort: es GIBT gerade keine
             * Elementverschiebung, weil kein Element zeichnet. Der Aufrufer ist trotzdem
             * repariert (BBSRendering prueft jetzt selbst) -- diese Wache ist die zweite
             * Linie, damit derselbe Fehler nicht ueber einen anderen Aufrufer wiederkommt. */
            if (this.context != null)
            {
                this.stapel.last().pose().mul(this.context.pose());
            }
        }

        return this.stapel;
    }

    /* Screen space clipping */

    public void clip(Area area, UIContext context)
    {
        this.clip(area.x, area.y, area.w, area.h, context);
    }

    public void clip(int x, int y, int w, int h, UIContext context)
    {
        this.clip(context.globalX(x), context.globalY(y), w, h, context.menu.width, context.menu.height);
    }

    /**
     * Scissor (clip) the screen, in global units. Spiegel und Vanilla im selben Raum, der
     * Fall des leeren Schnitts: {@link SchnittSpiegel}. {@code sw}/{@code sh} bleiben fuer
     * die Aufrufer; der Bildschirm kommt dort aus dem Kontext.
     */
    public void clip(int x, int y, int w, int h, int sw, int sh)
    {
        SchnittSpiegel.schneiden(this.context, this.scissors, x, y, w, h);
    }

    public void unclip(UIContext context)
    {
        this.unclip(context.menu.width, context.menu.height);
    }

    public void unclip(int sw, int sh)
    {
        this.context.disableScissor();

        if (!this.scissors.isEmpty())
        {
            this.scissors.pop();
        }
    }

    /* Solid rectangles */

    public void normalizedBox(float x1, float y1, float x2, float y2, int color)
    {
        float temp = x1;

        x1 = Math.min(x1, x2);
        x2 = Math.max(temp, x2);

        temp = y1;

        y1 = Math.min(y1, y2);
        y2 = Math.max(temp, y2);

        this.box(x1, y1, x2, y2, color);
    }

    public void box(float x1, float y1, float x2, float y2, int color)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, color, color, color, color);
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        /* c1 ---- c2
         * |        |
         * c3 ---- c4 */
        this.quad(
            x, y, color1,
            x, y + h, color3,
            x + w, y + h, color4,
            x + w, y, color2
        );
    }

    /**
     * ⛔ NIMMT {@link VertexConsumer}, NICHT {@code BufferBuilder}: der Sofort-Puffer landet
     * in 26.2 UNTER dem gesamten GUI (zwei Phasen — daran hingen die unsichtbaren
     * Keyframe-Symbole). {@code BufferBuilder} implementiert {@code VertexConsumer}, alte
     * Aufrufer bleiben gueltig; eingereihte Wege teilen sich so denselben Helfer.
     */
    public void fillRect(VertexConsumer builder, Matrix4f matrix4f, float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        /* c1 ---- c2
         * |        |
         * c3 ---- c4 */
        builder.addVertex(matrix4f, x, y, 0).setColor(color1);
        builder.addVertex(matrix4f, x, y + h, 0).setColor(color3);
        builder.addVertex(matrix4f, x + w, y + h, 0).setColor(color4);
        builder.addVertex(matrix4f, x + w, y, 0).setColor(color2);
    }

    public void bevelBox(int x1, int y1, int x2, int y2, int fill, boolean shadow, boolean border)
    {
        if (border)
        {
            this.box(x1, y1, x2, y2, Colors.A100);

            x1++;
            y1++;
            x2--;
            y2--;
        }

        this.box(x1, y1, x2, y2, fill);

        if (!BBSSettings.interfaceShadows.get())
        {
            return;
        }

        int light = Colors.lerp(fill, Colors.WHITE, 0.35F);

        this.box(x1, y1, x2, y1 + 1, light);
        this.box(x1, y1, x1 + 1, y2, light);

        if (shadow)
        {
            this.box(x1, y2 - 2, x2, y2, Colors.lerp(fill, Colors.A100, 0.4F));
        }
    }

    public void dropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        /* P2: weicher Schein statt Trapez-Ring — derselbe Trichter fuer alle Karten. */
        if (mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.an())
        {
            mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.schein(this, left, top, right - left, bottom - top, offset, opaque, shadow);

            return;
        }

        left -= offset;
        top -= offset;
        right += offset;
        bottom += offset;

        /* Mitte deckend, dann vier Schatten-Trapeze (oben/unten/links/rechts). */
        this.quad(
            left + offset, top + offset, opaque,
            left + offset, bottom - offset, opaque,
            right - offset, bottom - offset, opaque,
            right - offset, top + offset, opaque
        );
        this.quad(
            left, top, shadow,
            left + offset, top + offset, opaque,
            right - offset, top + offset, opaque,
            right, top, shadow
        );
        this.quad(
            left + offset, bottom - offset, opaque,
            left, bottom, shadow,
            right, bottom, shadow,
            right - offset, bottom - offset, opaque
        );
        this.quad(
            left, top, shadow,
            left, bottom, shadow,
            left + offset, bottom - offset, opaque,
            left + offset, top + offset, opaque
        );
        this.quad(
            right - offset, top + offset, opaque,
            right - offset, bottom - offset, opaque,
            right, bottom, shadow,
            right, top, shadow
        );
    }

    /* Gradients */

    public void gradientHBox(float x1, float y1, float x2, float y2, int leftColor, int rightColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, leftColor, rightColor, leftColor, rightColor);
    }

    public void gradientVBox(float x1, float y1, float x2, float y2, int topColor, int bottomColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, topColor, topColor, bottomColor, bottomColor);
    }

    public void dropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)
    {
        /* PORT-HINWEIS 26.2: die 1.21.1-fassung baute hier einen TRIANGLE_FAN auf und lud
         * ihn NIE hoch (kein BufferUploader-aufruf) -- sie zeichnete also nichts. Diese
         * fassung zeichnet den faecher wirklich; das ist eine bewusste verhaltensaenderung
         * gegenueber dem original und im bericht vermerkt. */
        float previousX = 0F;
        float previousY = 0F;

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;
            float px = (float) (x - Math.cos(a) * radius);
            float py = (float) (y + Math.sin(a) * radius);

            if (i > 0)
            {
                this.triangle(x, y, opaque, previousX, previousY, shadow, px, py, shadow);
            }

            previousX = px;
            previousY = py;
        }
    }

    public void dropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
        if (offset >= radius)
        {
            this.dropCircleShadow(x, y, radius, segments, opaque, shadow);

            return;
        }

        /* Draw opaque base */
        float previousX = 0F;
        float previousY = 0F;

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;
            float px = (int) (x - Math.cos(a) * offset);
            float py = (int) (y + Math.sin(a) * offset);

            if (i > 0)
            {
                this.triangle(x, y, opaque, previousX, previousY, opaque, px, py, opaque);
            }

            previousX = px;
            previousY = py;
        }

        /* Draw outer shadow */
        for (int i = 0; i < segments; i ++)
        {
            double alpha1 = i / (double) segments * Math.PI * 2 - Math.PI / 2;
            double alpha2 = (i + 1) / (double) segments * Math.PI * 2 - Math.PI / 2;

            float ox1 = (float) (x - Math.cos(alpha1) * offset);
            float oy1 = (float) (y + Math.sin(alpha1) * offset);
            float ox2 = (float) (x - Math.cos(alpha2) * offset);
            float oy2 = (float) (y + Math.sin(alpha2) * offset);
            float rx1 = (float) (x - Math.cos(alpha1) * radius);
            float ry1 = (float) (y + Math.sin(alpha1) * radius);
            float rx2 = (float) (x - Math.cos(alpha2) * radius);
            float ry2 = (float) (y + Math.sin(alpha2) * radius);

            this.triangle(ox2, oy2, opaque, ox1, oy1, opaque, rx1, ry1, shadow);
            this.triangle(ox2, oy2, opaque, rx1, ry1, shadow, rx2, ry2, shadow);
        }
    }

    /* Outline methods */

    public void outlineCenter(float x, float y, float offset, int color)
    {
        this.outlineCenter(x, y, offset, color, 1);
    }

    public void outlineCenter(float x, float y, float offset, int color, int border)
    {
        this.outline(x - offset, y - offset, x + offset, y + offset, color, border);
    }

    public void outline(float x1, float y1, float x2, float y2, int color)
    {
        this.outline(x1, y1, x2, y2, color, 1);
    }

    /**
     * Draw rectangle outline with given border.
     */
    public void outline(float x1, float y1, float x2, float y2, int color, int border)
    {
        this.box(x1, y1, x1 + border, y2, color);
        this.box(x2 - border, y1, x2, y2, color);
        this.box(x1 + border, y1, x2 - border, y1 + border, color);
        this.box(x1 + border, y2 - border, x2 - border, y2, color);
    }

    /* Icon */

    /** In the light theme white foreground (text/icons) becomes black; other colours pass through. */
    private static int darkenWhite(int color)
    {
        return (color & 0xFFFFFF) == 0xFFFFFF ? (color & 0xFF000000) : color;
    }

    public void icon(Icon icon, float x, float y)
    {
        this.icon(icon, Colors.WHITE, x, y);
    }

    public void icon(Icon icon, int color, float x, float y)
    {
        this.icon(icon, color, x, y, 0F, 0F);
    }

    public void icon(Icon icon, float x, float y, float ax, float ay)
    {
        this.icon(icon, Colors.WHITE, x, y, ax, ay);
    }

    public void icon(Icon icon, int color, float x, float y, float ax, float ay)
    {
        if (icon.texture == null)
        {
            return;
        }

        /* P3: neugezeichnete Icons kommen aus dem skalenreinen Vektor-Atlas. */
        icon = mchorse.bbs_mod.ui.utils.icons.VektorIcons.ersetze(icon,
            (int) Math.ceil(BBSModClient.getGUIScale() - 1E-3F));

        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        x -= icon.w * ax;
        y -= icon.h * ay;

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, icon.w, icon.h, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    public void iconArea(Icon icon, float x, float y, float w, float h)
    {
        this.iconArea(icon, Colors.WHITE, x, y, w, h);
    }

    public void iconArea(Icon icon, int color, float x, float y, float w, float h)
    {
        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        this.texturedArea(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, w, h, icon.x, icon.y, icon.w, icon.h, icon.textureW, icon.textureH);
    }

    public void outlinedIcon(Icon icon, float x, float y, float ax, float ay)
    {
        this.outlinedIcon(icon, x, y, Colors.WHITE, ax, ay);
    }

    /**
     * Draw an icon with a black outline.
     */
    public void outlinedIcon(Icon icon, float x, float y, int color, float ax, float ay)
    {
        this.icon(icon, Colors.A100, x - 1, y, ax, ay);
        this.icon(icon, Colors.A100, x + 1, y, ax, ay);
        this.icon(icon, Colors.A100, x, y - 1, ax, ay);
        this.icon(icon, Colors.A100, x, y + 1, ax, ay);
        this.icon(icon, color, x, y, ax, ay);
    }

    /* Textured box */

    public void fullTexturedBox(Texture texture, float x, float y, float w, float h)
    {
        this.fullTexturedBox(texture, Colors.WHITE, x, y, w, h);
    }

    public void fullTexturedBox(Texture texture, int color, float x, float y, float w, float h)
    {
        this.texturedBox(texture, color, x, y, w, h, 0, 0, w, h, (int) w, (int) h);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2)
    {
        this.texturedBox(texture, color, x, y, w, h, u1, v1, u2, v2, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u, float v)
    {
        this.texturedBox(texture, color, x, y, w, h, u, v, u + w, v + h, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.texturedBox(() -> RenderPipelines.GUI_TEXTURED, texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    /**
     * 26.2: der Supplier liefert eine ganze {@link RenderPipeline} (kein setShader mehr).
     * ⛔ Die Ueberladung mit roher GL-nummer ist WEG — die GUI benennt ihre Textur ueber
     * {@code TextureSetup(GpuTextureView, GpuSampler)}, und einen View liefert nur das
     * {@link Texture}-objekt. Aufrufer reichen das Objekt durch, nie {@code texture.id}.
     */
    public void texturedBox(Supplier<RenderPipeline> pipeline, Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        /* Ueber die Setup-Ueberladung, damit deren batcher.setup-Messpunkt BEIDE Wege sieht. */
        this.texturedBox(pipeline, texture.guiSetup(), color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    /**
     * Fuer texturen, die nicht BBS gehoeren — vor allem Mojangs {@code RenderTarget}, dessen
     * farbanhang ein {@code GpuTextureView} ohne BBS-{@link Texture} ist. Frueher reichte
     * dort die rohe GL-nummer aus {@code getColorTextureId()}; die gibt es in 26.2 nicht
     * mehr, wohl aber {@code getColorTextureView()}.
     */
    public void texturedBox(Supplier<RenderPipeline> pipeline, TextureSetup setup, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        if (textureW >= 512 && mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            /* Record-Accessor (texure0 — Mojang-Tippfehler); isClosed() entlarvt tote Views. */
            var view = setup.texure0();
            String tex = view == null ? "?" : (view.texture() instanceof com.mojang.blaze3d.opengl.GlTexture gl ? String.valueOf(gl.glId())
                : "vk" + System.identityHashCode(view.texture())) + (view.texture().isClosed() ? "-TOT" : ""); /* Vulkan has no GL id */

            mchorse.bbs_mod.graphics.Messpunkt.zaehle("batcher.setup." + tex,
                textureW + "x" + textureH + " @" + (int) x + "," + (int) y + " " + (int) w + "x" + (int) h
                    + " von=" + mchorse.bbs_mod.graphics.Messpunkt.rufer(4));
        }

        this.blit(pipeline.get(), setup, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    /* Repeatable textured box */

    public void texturedArea(Texture texture, int color, float x, float y, float w, float h, float u, float v, float tileW, float tileH, int tw, int th)
    {
        int countX = (int) (((w - 1) / tileW) + 1);
        int countY = (int) (((h - 1) / tileH) + 1);
        float fillerX = w - (countX - 1) * tileW;
        float fillerY = h - (countY - 1) * tileH;

        for (int i = 0, c = countX * countY; i < c; i ++)
        {
            float ix = i % countX;
            float iy = i / countX;
            float xx = x + ix * tileW;
            float yy = y + iy * tileH;
            float xw = ix == countX - 1 ? fillerX : tileW;
            float yh = iy == countY - 1 ? fillerY : tileH;

            this.blit(RenderPipelines.GUI_TEXTURED, texture.guiSetup(), color, xx, yy, xw, yh, u, v, u + xw, v + yh, tw, th);
        }
    }

    /* Text with default font */

    public void text(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, false);
    }

    public void text(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, false);
    }

    public void textShadow(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, true);
    }

    public void textShadow(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, true);
    }

    /**
     * Text um {@code winkel} GRAD gedreht; Drehpunkt ist der Ankerpunkt (x, y), positiv
     * dreht im Uhrzeigersinn (Bildschirmachsen, y zeigt nach unten).
     *
     * <p>⛔ Die Drehung gehoert auf {@code context.pose()}, NICHT auf {@link #pose()}:
     * eingereiht wird mit {@code new Matrix3x2f(this.context.pose())}, der PoseStack aus
     * {@code pose()} wird nur EINMAL aus dem Kontext befuellt und nie zurueckgeschrieben.
     * Wer dort dreht, dreht ins Leere.
     */
    public void text(String label, float x, float y, int color, boolean shadow, float winkel)
    {
        if (winkel == 0F)
        {
            this.text(label, x, y, color, shadow);

            return;
        }

        Matrix3x2fStack pose = this.context.pose();

        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) Math.toRadians(winkel));
        /* Gezeichnet wird im Ursprung — der Versatz steckt in der Matrix. Damit bleibt die
         * ganzzahlige Rundung in drawTextDirect ohne Wirkung auf die Lage. */
        this.text(label, 0F, 0F, color, shadow);
        pose.popMatrix();
    }

    public void text(String label, float x, float y, int color, boolean shadow)
    {
        if (BBSSettings.isLightTheme())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        this.drawTextDirect(label, x, y, color, shadow);
    }

    /** Actual text draw (theming is applied by the public text() before calling this). */
    private void drawTextDirect(String label, float x, float y, int color, boolean shadow)
    {
        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        /* P1 Vektor-Text: DER Trichter — alle 128 text()-Aufrufstellen laufen hier durch. */
        if (mchorse.bbs_mod.ui.framework.elements.utils.schrift.VektorSchrift.zeichnet(label))
        {
            mchorse.bbs_mod.ui.framework.elements.utils.schrift.VektorSchrift.holen().zeichne(this, label, x, y, color, shadow);

            return;
        }

        /* 26.2: GuiGraphicsExtractor.drawString -> GuiGraphicsExtractor.text; the trailing
         * RenderSystem.depthFunc(GL_ALWAYS) is gone with it — GUI space has no depth any
         * more, overlap is decided by GuiGraphicsExtractor.nextStratum(). */
        this.context.text(this.font.getRenderer(), label, (int) x, (int) y, color, shadow);
    }

    /* Text helpers */

    public int wallText(String text, int x, int y, int color, int width)
    {
        return this.wallText(text, x, y, color, width, 12);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight)
    {
        return this.wallText(text, x, y, color, width, lineHeight, 0F, 0F);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay)
    {
        return wallText(text, x, y, color, width, lineHeight, ax, ay, true);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay, boolean shadow)
    {
        return TextHelfer.wallText(this, text, x, y, color, width, lineHeight, ax, ay, shadow);
    }

    public void textCard(String text, float x, float y)
    {
        TextHelfer.textCard(this, text, x, y);
    }

    /** Text mit Hintergrund-Karte. */
    public void textCard(String text, float x, float y, int color, int background)
    {
        this.textCard(text, x, y, color, background, 3);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset)
    {
        this.textCard(text, x, y, color, background, offset, true);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset, boolean shadow)
    {
        TextHelfer.textCard(this, text, x, y, color, background, offset, shadow);
    }

    /** P2: Rundeck-Quad mit weichem Rand — CPU-Tessellation durch die GUI-Farb-Pipeline. */
    /** Konvexes Polygon mit AA-Kante (P2) — fuer Pfeile, Fahnen, Dreiecke. */
    public void polygon(float[] px, float[] py, int color)
    {
        mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.polygon(this, px, py, color);
    }

    public void rundeck(float x, float y, float w, float h, float radius, int color)
    {
        mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.rundeck(this, x, y, w, h, radius, color);
    }

    /** No-op since MC 26.2 (nothing left to flush — see class header); kept for the many call sites. */
    public void flush()
    {}

    /* Element submission — everything above ends up here */

    /**
     * Enqueue one triangle. MC 26.2's GUI pipelines are quad-topology, so a triangle is sent
     * as a quad whose last vertex is doubled: the shared quad index buffer then produces the
     * wanted triangle plus one zero-area (invisible) one.
     */
    private void triangle(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3)
    {
        this.quad(x1, y1, c1, x2, y2, c2, x3, y3, c3, x3, y3, c3);
    }

    /** Enqueue one flat-coloured quad in the winding vanilla uses (TL, BL, BR, TR). */
    private void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4)
    {
        Matrix3x2f pose = new Matrix3x2f(this.context.pose());

        /* Messfuehler Aufgabe 96: WER malt den Kasten, unter dem die Modellvorschau liegt?
         * Gemessen wurde die Farbe (#0F1217, 98,53 % der Flaeche), aber nicht der Urheber --
         * und Greppen half nicht, weil ein Dutzend Stellen dieselbe Farbe benutzt. Die
         * Bedingung haelt die teure Stapelabfrage von jedem anderen Kasten fern. */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn()
            && (c1 & 0xFFFFFF) == 0x0F1217 && Math.abs(x3 - x1) > 300F && Math.abs(y3 - y1) > 200F)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("hintergrund.tief",
                ((int) x1) + "," + ((int) y1) + " " + ((int) (x3 - x1)) + "x" + ((int) (y3 - y1))
                + "  von " + mchorse.bbs_mod.graphics.Messpunkt.rufer(3));
        }

        this.submit(new UIQuad(pose, x1, y1, c1, x2, y2, c2, x3, y3, c3, x4, y4, c4,
            this.scissors.peek(), bounds(pose, x1, y1, x2, y2, x3, y3, x4, y4)));
    }

    /** Enqueue one textured quad. Vanilla's own {@link BlitRenderState} already carries a tint. */
    private void blit(RenderPipeline pipeline, TextureSetup setup, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        Matrix3x2f pose = new Matrix3x2f(this.context.pose());
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = (int) (x + w);
        int y1 = (int) (y + h);
        this.submit(new BlitRenderState(pipeline, setup, pose,
            x0, y0, x1, y1,
            u1 / (float) textureW, u2 / (float) textureW,
            v1 / (float) textureH, v2 / (float) textureH,
            color, this.scissors.peek(),
            bounds(pose, x0, y0, x0, y1, x1, y1, x1, y0)));
    }

    /**
     * Eigene Geometrie in die Oberflaeche einreihen (frueher Tesselator+BufferUploader).
     * ⛔ KEIN {@code SofortZeichner} in der Oberflaeche: 26.2 sammelt nur und zeichnet
     * spaeter — ein Sofort-Durchgang landet UNTER dem GUI (still unsichtbar). Der
     * Eckenaufbau laeuft im Lambda erst beim Renderer — Bezugspunkte dort lesen.
     */
    public void netz(RenderPipeline pipeline, Consumer<VertexConsumer> ecken, ScreenRectangle bereich)
    {
        this.netz(pipeline, ecken, null, bereich);
    }

    /**
     * Wie oben, mit Texturbindung. ⚠️ 26.2: die Textur gehoert zum ZUG, nicht zum Zustand —
     * ohne {@code textur} bleiben Sampler ungebunden (schwarz/Muell, kein Fehler).
     */
    public void netz(RenderPipeline pipeline, Consumer<VertexConsumer> ecken, TextureSetup textur, ScreenRectangle bereich)
    {
        this.submit(new UINetz(pipeline, ecken, textur, this.scissors.peek(), bereich));
    }

    private void submit(GuiElementRenderState element)
    {
        /* ⛔ 26.2 VERBIETET DEN LEEREN AUSSCHNITT, 1.21.1 ERLAUBTE IHN.
         *
         * Ueberschneiden sich zwei clip()-Rechtecke nicht, legt {@link #clip} nach Vanillas
         * eigener Regel {@link ScreenRectangle#empty()} ab — ein 0x0-Rechteck. Bis 1.21.1 war
         * das harmlos: der GL-Scissor schnitt dann eben alles weg. In 26.2 nennt jeder
         * Zeichenzustand seinen Ausschnitt, und {@code RenderPass.enableScissor} wirft bei
         * nicht-positiver Groesse:
         *
         *     IllegalArgumentException: Scissor size must be >0, was 0x0
         *
         * Der Absturz kommt dabei NICHT an der Stelle, die falsch geclippt hat, sondern erst
         * am Bildende in GuiRenderer.executeDraw — im Trace steht keine einzige BBS-Zeile
         * (live: 2026-08-01 00:41, run_3/crash-reports).
         *
         * Ein leerer Ausschnitt heisst "nichts davon ist sichtbar". Dann ist Einreichen
         * ohnehin sinnlos, also gar nicht erst einreichen. Der Stapel bleibt unangetastet —
         * clip/unclip muessen paarig bleiben, sonst erbt das naechste Element den Ausschnitt. */
        ScreenRectangle ausschnitt = this.scissors.peek();

        if (ausschnitt != null && (ausschnitt.width() <= 0 || ausschnitt.height() <= 0))
        {
            /* Zaehler, weil dieser Waechter selbst zum Verdaechtigen wurde: er kann nur
             * ENTFERNEN, und wenn Geometrie unsichtbar bleibt, muss man wissen, ob er es war. */
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("submit.verworfen",
                element.getClass().getSimpleName());

            return;
        }

        mchorse.bbs_mod.graphics.Messpunkt.zaehle("submit.durch", element.getClass().getSimpleName());

        guiRenderState(this.context).addGuiElement(element);
    }

    /**
     * Reiht einen Bild-im-Bild-Zustand ein -- der Weg fuer 3D-Inhalte in der Oberflaeche.
     *
     * <p>Anders als {@link #netz} bekommt so ein Zustand vom Bildgraphen eine EIGENE
     * Textur mit eigener Projektion und eigenem Tiefenpuffer. Genau das braucht eine
     * Modellvorschau: {@code netz} reiht die Geometrie zwar korrekt ein, sie wird dann
     * aber mit der GUI-Projektion gezeichnet statt mit der Kameraprojektion der Vorschau
     * (gemessen 2026-07-31, Aufgabe 96 -- die Geometrie kam an und blieb unsichtbar).
     *
     * <p>⚠️ Zu jedem Zustand muss ein Zeichner registriert sein
     * ({@code PictureInPictureRendererRegistry}, siehe BBSModClient). Fehlt er, wird der
     * Zustand STILL verworfen -- kein Fehler, kein Log, nur ein leeres Feld.
     */
    /**
     * Der gerade gueltige Schnitt, oder {@code null} wenn nichts geschnitten wird.
     *
     * <p>⛔ WOFUER (2026-08-05, im A/B gegen 1.21.1 gefunden): {@code clip(...)} wirkt nur auf
     * Zuege, die SOFORT gezeichnet werden. Formvorschauen werden im Port aber EINGEREIHT
     * ({@link mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange}) und
     * spaeter gezeichnet -- da ist der Schnitt laengst wieder weg.</p>
     *
     * <p>Sichtbar in der Replay-Liste: {@code UIReplayList#renderElementPart} zeichnet eine
     * 40x40-Vorschau und klemmt sie mit {@code clip(formX, y, 40, 20, context)} auf halbe
     * Hoehe. In 1.21.1 schneidet das (kompaktes Bild am Zeilenrand), im Port nicht -- dort
     * laeuft die Miniatur ueberlang aus der Zeile heraus.</p>
     */
    public ScreenRectangle aktuellerSchnitt()
    {
        return this.scissors.peek();
    }

    public void bildImBild(net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState zustand)
    {
        /* Messfuehler Aufgabe 96: eingereiht ja/nein -- die Gegenprobe zum Zaehler im
         * Zeichner. Stimmen beide Zahlen nicht ueberein, wird der Zustand unterwegs
         * verworfen (fehlender Zeichner, siehe Warnung oben). */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("pip.eingereiht",
            zustand.getClass().getSimpleName());

        guiRenderState(this.context).addPicturesInPictureState(zustand);
    }

    /**
     * Axis-aligned screen bounds of a quad, as {@code GuiElementRenderState.bounds()} demands
     * (the renderer uses it for culling and for sorting inside a stratum).
     */
    private static ScreenRectangle bounds(Matrix3x2fc pose, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4)
    {
        int minX = (int) Math.floor(Math.min(Math.min(x1, x2), Math.min(x3, x4)));
        int minY = (int) Math.floor(Math.min(Math.min(y1, y2), Math.min(y3, y4)));
        int maxX = (int) Math.ceil(Math.max(Math.max(x1, x2), Math.max(x3, x4)));
        int maxY = (int) Math.ceil(Math.max(Math.max(y1, y2), Math.max(y3, y4)));

        return new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
    }

    /**
     * Der {@link GuiRenderState} liegt privat in {@link GuiGraphicsExtractor}
     * (javap 26.2: {@code private final ...GuiRenderState guiRenderState;}) und hat keinen
     * getter — vanilla reicht ihn selbst herein ({@code Gui.extractRenderState} baut den
     * extractor mit {@code GuiGraphicsExtractor(Minecraft, GuiRenderState, int, int)}), ein
     * Screen bekommt aber nur den fertigen extractor.
     *
     * <p>Der Zugriff laeuft ueber {@link GuiGraphicsExtractorAccessor} — ein Accessor-Mixin,
     * kein Reflection-Griff. Der erste Anlauf las das Feld per {@code getDeclaredField}; das
     * laeuft zwar, bindet BBS aber an einen Feldnamen, den kein Compiler prueft. Benennt
     * Mojang ihn um, verschwindet die gesamte BBS-Oberflaeche — beim Nutzer, nicht im Bau.
     */
    private static GuiRenderState guiRenderState(GuiGraphicsExtractor context)
    {
        return ((GuiGraphicsExtractorAccessor) (Object) context).bbs$getGuiRenderState();
    }

    /**
     * One flat-coloured quad with a free colour per corner.
     *
     * <p>Vanilla's {@code ColoredRectangleRenderState} only knows two colours (top/bottom) and
     * only axis-aligned rectangles; BBS needs four corner colours (gradientHBox) and real
     * trapezoids (dropShadow), so it carries its own state. Vertex order and
     * {@code addVertexWith2DPose} are copied from
     * {@code ColoredRectangleRenderState.buildVertices} (javap -c).
     */
    public record UIQuad(
        Matrix3x2fc pose,
        float x1, float y1, int c1,
        float x2, float y2, int c2,
        float x3, float y3, int c3,
        float x4, float y4, int c4,
        ScreenRectangle scissorArea,
        ScreenRectangle bounds
    ) implements GuiElementRenderState
    {
        @Override
        public void buildVertices(VertexConsumer consumer)
        {
            consumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setColor(this.c1);
            consumer.addVertexWith2DPose(this.pose, this.x2, this.y2).setColor(this.c2);
            consumer.addVertexWith2DPose(this.pose, this.x3, this.y3).setColor(this.c3);
            consumer.addVertexWith2DPose(this.pose, this.x4, this.y4).setColor(this.c4);
        }

        @Override
        public RenderPipeline pipeline()
        {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup()
        {
            return TextureSetup.noTexture();
        }
    }
}
