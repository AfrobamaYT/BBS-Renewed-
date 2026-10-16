package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

/**
 * Draws the image clips over the frame, in the units of a 1080-tall frame (Placement). From BBS FS
 * e898ba310 and 7f19dfbd5 (Wemppy4/bbs-fs, MIT); FS's video overlays are not part of this.
 */
public class UIImageRenderer
{
    /** The frame's width in units: always {@link Placement#HEIGHT} tall, as wide as its aspect makes it */
    public static float getUnitWidth()
    {
        com.mojang.blaze3d.pipeline.RenderTarget fb = Minecraft.getInstance().gameRenderer.mainRenderTarget();

        return fb.width * Placement.HEIGHT / (float) fb.height;
    }

    /**
     * Draws into the frame rectangle (x, y, w, h) of the GUI: the film preview in the editor, the
     * whole screen in the game. 26.3's GUI reads only its own 2D pose, so the frame's units are
     * mapped there; a PoseStack translation is silently dropped.
     */
    public static void renderImages(Batcher2D batcher, List<ImageOverlay> images, float frameX, float frameY, float frameW, float frameH)
    {
        if (images.isEmpty() || frameW <= 0 || frameH <= 0)
        {
            return;
        }

        float width = getUnitWidth();
        float height = Placement.HEIGHT;
        org.joml.Matrix3x2fStack pose = batcher.getContext().pose();

        pose.pushMatrix();
        pose.translate(frameX, frameY);
        pose.scale(frameW / width, frameH / height);

        for (ImageOverlay image : images)
        {
            if (Colors.getA(image.color) <= 0 || image.texture == null || !BBSModClient.getTextures().has(image.texture))
            {
                continue;
            }

            Texture texture = BBSModClient.getTextures().getTexture(image.texture);

            if (texture == BBSModClient.getTextures().getError())
            {
                continue;
            }

            texture.setFilter(image.smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Placement placement = image.placement;
            float w = image.fullscreen ? width : texture.width * placement.scaleX;
            float h = image.fullscreen ? height : texture.height * placement.scaleY;
            float x = image.fullscreen ? width / 2F : width * placement.windowX + placement.offsetX;
            float y = image.fullscreen ? height / 2F : height * placement.windowY + placement.offsetY;
            float anchorX = image.fullscreen ? 0.5F : placement.anchorX;
            float anchorY = image.fullscreen ? 0.5F : placement.anchorY;

            image.box.set(x - w * anchorX, y - h * anchorY, w, h, width);

            /* The fade transform in 2D: offset, turn about the view axis, scale */
            Transform transform = new Transform();

            transform.lerp(image.transform, 1F - image.factor);

            pose.pushMatrix();
            pose.translate(x + transform.translate.x, y + transform.translate.y);
            pose.rotate(transform.rotate.z);
            pose.scale(transform.scale.x, transform.scale.y);
            batcher.texturedBox(texture, image.color, -w * anchorX, -h * anchorY, w, h, 0, 0, texture.width, texture.height, texture.width, texture.height);
            pose.popMatrix();
        }

        pose.popMatrix();
    }
}
