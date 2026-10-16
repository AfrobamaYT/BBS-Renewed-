package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.pipeline.TextureTarget;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The dock panel of {@link FilmKameraAnsicht}: the film camera's picture, letterboxed with the film's
 * aspect on black, and a "Camera" label as in Blender's camera view.
 */
public class UIFilmKameraAnsicht extends UIElement
{
    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;
        EditorTypAuswahl.kameraKopf(context, this);
        y += 26;
        h = Math.max(1, h - 26);
        float skala = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();

        context.batcher.box(x, y, x + w, y + h, Colors.A100);
        FilmKameraAnsicht.gesehen(Math.round(w * skala), Math.round(h * skala));

        TextureTarget ziel = FilmKameraAnsicht.ziel();

        if (FilmKameraAnsicht.kaputt())
        {
            context.batcher.textCard("Camera view failed (see log)", x + 6, y + 6);
        }
        else if (ziel != null && ziel.getColorTextureView() != null)
        {
            /* Fit the target's aspect into the panel */
            float seite = (float) ziel.width / ziel.height;
            int bw = Math.min(w, Math.round(h * seite));
            int bh = Math.round(bw / seite);
            int bx = x + (w - bw) / 2;
            int by = y + (h - bh) / 2;

            /* Opaque: the world pass clears to the fog colour with alpha 0 (LevelRenderer), and where
             * nothing draws over it (beyond the terrain, below the sky dome) a blended blit showed
             * the black box behind -- a black band at the horizon */
            context.batcher.texturedBox(
                () -> net.minecraft.client.renderer.RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
                net.minecraft.client.gui.render.TextureSetup.singleTexture(ziel.getColorTextureView(),
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                        com.mojang.renderpearl.api.textures.AddressMode.CLAMP_TO_EDGE,
                        com.mojang.renderpearl.api.textures.AddressMode.CLAMP_TO_EDGE,
                        com.mojang.renderpearl.api.textures.FilterMode.LINEAR,
                        com.mojang.renderpearl.api.textures.FilterMode.LINEAR, false)),
                Colors.WHITE, bx, by, bw, bh, 0, ziel.height, ziel.width, 0, ziel.width, ziel.height);
        }

        super.render(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        return EditorTypAuswahl.kameraKlick(context, this)
            || context.mouseButton < 3 && this.area.isInside(context);
    }
}
