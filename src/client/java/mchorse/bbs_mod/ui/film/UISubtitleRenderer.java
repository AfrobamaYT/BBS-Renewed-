package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class UISubtitleRenderer
{
    private static Framebuffer getTextFramebuffer()
    {
        return BBSModClient.getFramebuffers().getFramebuffer(Link.bbs("camera_subtitles"), (f) ->
        {
            Texture texture = BBSModClient.getTextures().createTexture(Link.bbs("test"));

            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            f.deleteTextures();
            f.attach(texture, GL30.GL_COLOR_ATTACHMENT0);

            f.unbind();
        });
    }

    public static void renderSubtitles(PoseStack stack, Batcher2D batcher, List<Subtitle> subtitles)
    {
        if (subtitles.isEmpty())
        {
            return;
        }

        RenderPipeline program = BBSShaders.getSubtitlesProgram();
        Uniform blur = program.getUniform("Blur");
        Uniform textureSize = program.getUniform("TextureSize");
        Supplier<RenderPipeline> supplier = () -> program;

        com.mojang.blaze3d.pipeline.RenderTarget fb = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = fb.width;
        int height = fb.height;

        /* 26.2: die Projektion liegt auf der Grafikkarte und kommt nicht als Zahlen zurueck.
         * Gesichert wird darum der Puffer selbst. */
        mchorse.bbs_mod.graphics.Projektion.sichern();

        width /= 2;
        height /= 2;

        Framebuffer framebuffer = getTextFramebuffer();
        Texture texture = framebuffer.getMainTexture();
        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -100, 100);
        FontRenderer font = Batcher2D.getDefaultTextRenderer();

        /* 26.2: depthFunc(GL_ALWAYS) entfaellt ersatzlos — der GUI-Raum hat keine Tiefe mehr,
         * die Ueberdeckung entscheidet GuiGraphicsExtractor.nextStratum() (siehe Batcher2D),
         * und den Tiefenzustand bringt jeder Draw aus seiner Pipeline mit. */
        /* 26.2: disableCull ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: beidseitig zeichnen. */

        for (Subtitle subtitle : subtitles)
        {
            float alpha = Colors.getA(subtitle.color);

            if (alpha <= 0)
            {
                continue;
            }

            String label = StringUtils.processColoredText(subtitle.label);
            int w = 0;
            int h = 0;
            int x = (int) (width * subtitle.windowX + subtitle.x);
            int y = (int) (height * subtitle.windowY + subtitle.y);
            float scale = subtitle.size;
            int subColor = subtitle.color;

            List<String> strings = subtitle.maxWidth <= 10 ? Arrays.asList(label) : font.wrap(label, subtitle.maxWidth);

            for (String string : strings)
            {
                w = Math.max(w, font.getWidth(string.trim()));
            }

            h = (strings.size() - 1) * subtitle.lineHeight + font.getHeight();

            Texture imgTex = null;
            float gap = 6F;
            float imgW = 0F;
            float imgH = 0F;

            if (subtitle.image != null && BBSModClient.getTextures().has(subtitle.image))
            {
                imgTex = BBSModClient.getTextures().getTexture(subtitle.image);

                if (imgTex != BBSModClient.getTextures().getError())
                {
                    int base = subtitle.lineHeight > 0 ? subtitle.lineHeight : font.getHeight();
                    imgH = base * subtitle.imageScale;
                    if (imgH <= 0) imgH = 0;
                    if (imgTex.height > 0)
                    {
                        imgW = imgTex.width * (imgH / imgTex.height);
                    }
                }
            }

            float contentW = w + (imgTex != null && imgH > 0 ? (gap + imgW) : 0);
            float contentH = Math.max(h, imgH);

            int fw = (int) ((contentW + 10) * scale);
            int fh = (int) ((contentH + 10) * scale);

            mchorse.bbs_mod.graphics.Projektion.setzen(new Matrix4f().ortho(0, contentW + 10, 0, contentH + 10, -100, 100), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

            framebuffer.resize(fw, fh);
            framebuffer.applyClear();

            float baseX = 5F;
            float baseY = 5F;
            float textLeft = baseX + ((imgTex != null && imgH > 0 && !subtitle.imageRight) ? (imgW + gap) : 0F);
            float textAreaW = w;
            float yy = baseY + (contentH - h) / 2F;

            if (Colors.getA(subtitle.backgroundColor) > 0)
            {
                float o = subtitle.backgroundOffset;
                float bgX1 = baseX - o;
                float bgY1 = yy - o;
                float bgX2 = baseX + contentW + o - 1F;
                float bgY2 = yy + h + o;

                batcher.box(bgX1, bgY1, bgX2, bgY2, Colors.mulA(subtitle.backgroundColor, alpha));
            }

            if (imgTex != null && imgH > 0)
            {
                float imgX = subtitle.imageRight ? baseX + contentW - imgW : baseX;
                float imgY = baseY + (contentH - imgH) / 2F;

                batcher.texturedBox(imgTex, Colors.setA(Colors.WHITE, 1F), imgX, imgY, imgW, imgH, 0, 0, imgTex.width, imgTex.height, imgTex.width, imgTex.height);
            }

            for (String string : strings)
            {
                string = string.trim();

                int xx = (int) (textLeft + (textAreaW - font.getWidth(string)) / 2F);
                batcher.text(string, xx, (int) yy, Colors.setA(subColor, 1F), subtitle.textShadow);

                yy += subtitle.lineHeight;
            }

            /* Render the texture */
            /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
             * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
             * zeichnen will, gibt das Ziel dort an. */

            mchorse.bbs_mod.graphics.Projektion.setzen(ortho, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

            Transform transform = new Transform();

            transform.lerp(subtitle.transform, 1F - subtitle.factor);

            stack.pushPose();
            stack.translate(x, y, 0);
            MatrixStackUtils.applyTransform(stack, transform);

            if (blur != null)
            {
                blur.set(subtitle.shadow, subtitle.shadowOpaque ? 1F : 0F);
            }

            if (textureSize != null)
            {
                textureSize.set((float) texture.width, (float) texture.height);
            }

            /* 26.2: enableBlend ist entfallen -- der Zustand steckt in der Pipeline
             * des Zuges. Absicht war: mischen an. */
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            batcher.texturedBox(supplier, texture.id, Colors.setA(Colors.WHITE, alpha), -fw * subtitle.anchorX, -fh * subtitle.anchorY, texture.width, texture.height, 0, 0, texture.width, texture.height, texture.width, texture.height);

            stack.popPose();
        }

        mchorse.bbs_mod.graphics.Projektion.zurueck();

        /* Das enableCull() entfaellt ersatzlos: einen globalen Cull-Schalter, den man
         * zuruecksetzen koennte, gibt es nicht mehr — jede Pipeline bringt ihren eigenen mit.
         * Das disableCull() oben bleibt dagegen offen: dort steht die Absicht (beidseitig
         * zeichnen, weil die Untertitel-Transform spiegeln darf). */
    }
}
