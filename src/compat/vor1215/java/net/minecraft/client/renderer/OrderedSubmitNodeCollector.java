package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * BBS-Shim fuer Minecraft vor 1.21.9: die Abgabe-Schnittstelle des Feature-Renderings, soweit
 * BBS sie nutzt, mit 1.21.4-Typen (Model ist dort nicht generisch; den Ueberlagerungs-Typ fuer
 * Bruchbilder gibt es nicht, er kommt als Object und wird ignoriert). Umsetzung: SofortSammler.
 */
public interface OrderedSubmitNodeCollector
{
    void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer);

    <S> void submitModel(Model model, S state, PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int outline, Object crumbling);

    default <S> void submitModel(Model model, S state, PoseStack pose, RenderType type, int light, int overlay, int outline, Object crumbling)
    {
        this.submitModel(model, state, pose, type, light, overlay, -1, null, outline, crumbling);
    }

    void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean sheeted, boolean foil, int color, Object crumbling, int outline);

    default void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite)
    {
        this.submitModelPart(part, pose, type, light, overlay, sprite, false, false, -1, null, 0);
    }

    void submitBlock(PoseStack pose, BlockState state, int light, int overlay, int outline);

    void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode, int light, int color, int background, int outline);

    void submitNameTag(PoseStack pose, Vec3 offset, int y, Component text, boolean visible, int light, double distance, CameraRenderState camera);
}
