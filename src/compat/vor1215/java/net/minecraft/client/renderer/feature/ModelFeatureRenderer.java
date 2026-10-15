package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * BBS-Shim fuer Minecraft vor 1.21.9: nur der Bruchbild-Typ, der in Renderer-Signaturen steht.
 * Das Feature-Rendering selbst gibt es dort nicht (Abgaben zeichnet SofortSammler sofort).
 */
public final class ModelFeatureRenderer
{
    private ModelFeatureRenderer()
    {}

    public record CrumblingOverlay(int progress, PoseStack.Pose cameraPose)
    {}
}
