package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Feature-Abgaben von 1.21.9+ fuer die Schicht vor 1.21.9: zeichnet sofort in einen
 * MultiBufferSource von 1.21.4 (der Aufrufer leert ihn, wie Vanilla nach seinen Renderern).
 * Reihenfolgen (order) gibt es dabei nicht: abgegeben wird in Aufrufreihenfolge.
 */
public final class SofortSammler implements SubmitNodeCollector
{
    private final MultiBufferSource puffer;

    public SofortSammler(MultiBufferSource puffer)
    {
        this.puffer = puffer;
    }

    public MultiBufferSource puffer()
    {
        return this.puffer;
    }

    /** Der Puffer hinter einer Abgabe; vor 1.21.9 zeichnet nur der SofortSammler. */
    public static MultiBufferSource puffer(SubmitNodeCollector sammler)
    {
        if (sammler instanceof SofortSammler sofort)
        {
            return sofort.puffer;
        }

        if (sammler instanceof net.minecraft.client.renderer.SubmitNodeStorage speicher)
        {
            return speicher.puffer();
        }

        throw new IllegalArgumentException("Vor 1.21.9 braucht diese Abgabe einen SofortSammler, bekam " + (sammler == null ? "null" : sammler.getClass().getName()));
    }

    /** Ueber 1.21.4s eigenen Puffer der Welt (Vanilla leert ihn am Ende des Weltdurchgangs). */
    public static SofortSammler welt()
    {
        return new SofortSammler(Minecraft.getInstance().renderBuffers().bufferSource());
    }

    @Override
    public OrderedSubmitNodeCollector order(int order)
    {
        return this;
    }

    @Override
    public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer)
    {
        renderer.render(pose.last(), this.puffer.getBuffer(type));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> void submitModel(Model model, S state, PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int outline, Object crumbling)
    {
        ModellAnimation.einrichten(model, state);

        var ecken = sprite == null ? this.puffer.getBuffer(type) : sprite.wrap(this.puffer.getBuffer(type));

        mchorse.bbs_mod.compat.alt.AltApi.modell(model, pose, ecken, light, overlay, color);
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean sheeted, boolean foil, int color, Object crumbling, int outline)
    {
        var ecken = sprite == null ? this.puffer.getBuffer(type) : sprite.wrap(this.puffer.getBuffer(type));

        mchorse.bbs_mod.compat.alt.AltApi.teil(part, pose, ecken, light, overlay, color);
    }

    @Override
    public void submitBlock(PoseStack pose, BlockState state, int light, int overlay, int outline)
    {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, this.puffer, light, overlay);
    }

    @Override
    public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode, int light, int color, int background, int outline)
    {
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, shadow, pose.last().pose(), this.puffer, mode, background, light);
    }

    @Override
    public void submitNameTag(PoseStack pose, Vec3 offset, int y, Component text, boolean visible, int light, double distance, CameraRenderState camera)
    {
        Font font = Minecraft.getInstance().font;

        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        pose.mulPose(camera.orientation);
        pose.scale(0.025F, -0.025F, 0.025F);

        float x = -font.width(text) / 2F;
        int hintergrund = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255F) << 24;

        font.drawInBatch(text, x, y, 0x20FFFFFF, false, pose.last().pose(), this.puffer, visible ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, hintergrund, light);

        if (visible)
        {
            font.drawInBatch(text, x, y, -1, false, pose.last().pose(), this.puffer, Font.DisplayMode.NORMAL, 0, light);
        }

        pose.popPose();
    }
}
