package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * BBS-Shim fuer Minecraft vor 1.21.9: ein eigener Abgabespeicher. Er zeichnet sofort in einen
 * eigenen Pufferquell, den erst {@link #zeichnen()} (1.21.11: FeatureRenderDispatcher.
 * renderAllFeatures(speicher)) abschliesst. Der Speicher entsteht beim ersten Zug; ein nie
 * benutzter Wegwerf-Speicher belegt nichts.
 */
public class SubmitNodeStorage implements SubmitNodeCollector
{
    private ByteBufferBuilder speicher;
    private MultiBufferSource.BufferSource quelle;
    private SofortSammler sofort;

    private SofortSammler sofort()
    {
        if (this.sofort == null)
        {
            this.speicher = new ByteBufferBuilder(1536);
            this.quelle = mchorse.bbs_mod.compat.alt.AltApi.quelle(this.speicher);
            this.sofort = new SofortSammler(this.quelle);
        }

        return this.sofort;
    }

    public MultiBufferSource puffer()
    {
        return this.sofort().puffer();
    }

    /** Zeichnet alles Abgegebene und gibt den Speicher frei. */
    public void zeichnen()
    {
        if (this.quelle == null)
        {
            return;
        }

        this.quelle.endBatch();
        this.speicher.close();
        this.speicher = null;
        this.quelle = null;
        this.sofort = null;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order)
    {
        return this;
    }

    @Override
    public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer)
    {
        this.sofort().submitCustomGeometry(pose, type, renderer);
    }

    @Override
    public <S> void submitModel(Model model, S state, PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int outline, Object crumbling)
    {
        this.sofort().submitModel(model, state, pose, type, light, overlay, color, sprite, outline, crumbling);
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean sheeted, boolean foil, int color, Object crumbling, int outline)
    {
        this.sofort().submitModelPart(part, pose, type, light, overlay, sprite, sheeted, foil, color, crumbling, outline);
    }

    @Override
    public void submitBlock(PoseStack pose, BlockState state, int light, int overlay, int outline)
    {
        this.sofort().submitBlock(pose, state, light, overlay, outline);
    }

    @Override
    public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode, int light, int color, int background, int outline)
    {
        this.sofort().submitText(pose, x, y, text, shadow, mode, light, color, background, outline);
    }

    @Override
    public void submitNameTag(PoseStack pose, Vec3 offset, int y, Component text, boolean visible, int light, double distance, CameraRenderState camera)
    {
        this.sofort().submitNameTag(pose, offset, y, text, visible, light, distance, camera);
    }
}
