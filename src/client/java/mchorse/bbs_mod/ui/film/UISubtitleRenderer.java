package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;
import java.util.List;

/**
 * Subtitles over the frame. Until 1.21 they were drawn into a framebuffer of their own and that
 * texture was put on the frame, through a shader that blurred its shadow. In 26.3 the GUI draws
 * are deferred, so the text never reached that framebuffer: it landed on the screen at the
 * framebuffer's local coordinates, and the composite's PoseStack translation was dropped as
 * well, so every subtitle sat at the window's top-left. They are drawn into the GUI directly
 * now, through its 2D pose, mapped onto the frame; the blurred shadow is gone, the text shadow
 * stays. Positions keep their old space: half the main render target.
 */
public class UISubtitleRenderer
{
    public static void renderSubtitles(Batcher2D batcher, List<Subtitle> subtitles, Area frame)
    {
        if (subtitles.isEmpty() || frame.w <= 0 || frame.h <= 0)
        {
            return;
        }

        /* Placement units: a frame 1080 high, as wide as the aspect makes it (BBS FS 7f19dfbd5) */
        float width = UIImageRenderer.getUnitWidth();
        float height = mchorse.bbs_mod.camera.data.Placement.HEIGHT;
        Matrix3x2fStack pose = batcher.getContext().pose();

        for (Subtitle subtitle : subtitles)
        {
            float alpha = Colors.getA(subtitle.color);

            if (alpha <= 0)
            {
                continue;
            }

            String label = StringUtils.processColoredText(subtitle.label);
            /* Screen pixels per layout unit: the placement's scale over the frame's units, at the GUI scale */
            float pixels = subtitle.placement.scaleX * frame.w / width * (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            FontRenderer custom = subtitle.font == null || subtitle.font.path.isEmpty() ? null : mchorse.bbs_mod.fonts.FontManager.get().get(subtitle.font, subtitle.fontSize, pixels);
            FontRenderer font = custom == null ? Batcher2D.getDefaultTextRenderer() : custom;
            /* 0 has always drawn every line on top of the last, so it is free to mean "ask the font" (BBS FS fd189d58f) */
            int lineHeight = subtitle.lineHeight > 0 ? subtitle.lineHeight : font.getLineHeight();
            List<String> strings = subtitle.maxWidth <= 10 ? Arrays.asList(label) : font.wrap(label, subtitle.maxWidth);
            int w = 0;

            for (String string : strings)
            {
                w = Math.max(w, font.getWidth(string.trim()));
            }

            int h = (strings.size() - 1) * lineHeight + font.getHeight();
            Texture image = null;
            float gap = 6F;
            float imageW = 0F;
            float imageH = 0F;

            if (subtitle.image != null && BBSModClient.getTextures().has(subtitle.image))
            {
                image = BBSModClient.getTextures().getTexture(subtitle.image);

                if (image != BBSModClient.getTextures().getError())
                {
                    imageH = Math.max(0, lineHeight * subtitle.imageScale);
                    imageW = image.height > 0 ? image.width * (imageH / image.height) : 0F;
                }
                else
                {
                    image = null;
                }
            }

            boolean withImage = image != null && imageH > 0;
            float contentW = w + (withImage ? gap + imageW : 0);
            float contentH = Math.max(h, imageH);
            Transform transform = new Transform();

            transform.lerp(subtitle.transform, 1F - subtitle.factor);

            pose.pushMatrix();
            pose.translate(frame.x, frame.y);
            pose.scale(frame.w / width, frame.h / height);
            mchorse.bbs_mod.camera.data.Placement placement = subtitle.placement;
            float x = width * placement.windowX + placement.offsetX;
            float y = height * placement.windowY + placement.offsetY;
            float fw = (contentW + 10) * placement.scaleX;
            float fh = (contentH + 10) * placement.scaleY;

            subtitle.box.set(x - fw * placement.anchorX, y - fh * placement.anchorY, fw, fh, width);

            pose.translate(x + transform.translate.x, y + transform.translate.y);
            pose.rotate(transform.rotate.z);
            pose.scale(transform.scale.x * placement.scaleX, transform.scale.y * placement.scaleY);
            pose.translate(-(contentW + 10) * placement.anchorX, -(contentH + 10) * placement.anchorY);

            float baseX = 5F;
            float textLeft = baseX + (withImage && !subtitle.imageRight ? imageW + gap : 0F);
            float yy = 5F + (contentH - h) / 2F;

            if (Colors.getA(subtitle.backgroundColor) > 0)
            {
                float o = subtitle.backgroundOffset;

                batcher.box(baseX - o, yy - o, baseX + contentW + o - 1F, yy + h + o, Colors.mulA(subtitle.backgroundColor, alpha));
            }

            if (withImage)
            {
                float imageX = subtitle.imageRight ? baseX + contentW - imageW : baseX;

                batcher.texturedBox(image, Colors.setA(Colors.WHITE, alpha), imageX, 5F + (contentH - imageH) / 2F, imageW, imageH, 0, 0, image.width, image.height, image.width, image.height);
            }

            FontRenderer previous = batcher.font;

            batcher.font = font;

            try
            {
                for (String string : strings)
                {
                    string = string.trim();
                    batcher.text(string, (int) (textLeft + (w - font.getWidth(string)) / 2F), (int) yy, Colors.setA(subtitle.color, alpha), subtitle.textShadow);
                    yy += lineHeight;
                }
            }
            finally
            {
                batcher.font = previous;
            }

            pose.popMatrix();
        }
    }
}
