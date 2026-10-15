package net.minecraft.client.gui.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import mchorse.bbs_mod.compat.gpu.BildImBildRegister;
import mchorse.bbs_mod.compat.gpu.GuiZeichner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * BBS-Shim fuer Minecraft vor 1.21.6: GUI-Abgabe je GuiGraphics, sofort gezeichnet statt am
 * Bildende sortiert. Aufeinanderfolgende Elemente mit gleicher Pipeline, Texturen und Schere
 * werden gebuendelt (ein Zug), wie bei Mojangs GuiRenderer. Schreibt Vanilla zwischendurch in
 * seinen Puffer (zeichnungen zaehlt das, BufferSourceZaehlerMixin), geht das eigene Buendel
 * zuerst raus und danach Vanillas (GuiGraphics.flush) - so bleibt die Malreihenfolge.
 */
public class GuiRenderState
{
    private static final Map<GuiGraphics, GuiRenderState> ZUSTAENDE = new WeakHashMap<>();
    /** Zaehler der Vanilla-Puffergriffe (BufferSourceZaehlerMixin). */
    public static long zeichnungen;

    private final GuiGraphics grafik;
    private final ByteBufferBuilder speicher = new ByteBufferBuilder(1 << 16);
    private BufferBuilder buendel;
    private RenderPipeline pipeline;
    private TextureSetup texturen;
    private ScreenRectangle schere;
    private long stand = -1;

    public GuiRenderState()
    {
        this(null);
    }

    private GuiRenderState(GuiGraphics grafik)
    {
        this.grafik = grafik;
    }

    public static GuiRenderState von(GuiGraphics grafik)
    {
        return ZUSTAENDE.computeIfAbsent(grafik, GuiRenderState::new);
    }

    public void submitGuiElement(GuiElementRenderState element)
    {
        boolean gleich = this.buendel != null && this.stand == zeichnungen && element.pipeline() == this.pipeline
            && Objects.equals(element.textureSetup(), this.texturen) && Objects.equals(element.scissorArea(), this.schere);

        if (!gleich)
        {
            this.flush();

            if (this.grafik != null)
            {
                this.grafik.flush();
            }

            this.pipeline = element.pipeline();
            this.texturen = element.textureSetup();
            this.schere = element.scissorArea();
            this.buendel = mchorse.bbs_mod.compat.alt.AltApi.bauer(this.speicher, this.pipeline.getVertexFormatMode(), this.pipeline.getVertexFormat());
        }

        element.buildVertices(this.buendel);
        this.stand = zeichnungen;
    }

    public void submitBlitToCurrentLayer(BlitRenderState blit)
    {
        this.submitGuiElement(blit);
    }

    public void submitGlyphToCurrentLayer(GuiElementRenderState glyphe)
    {
        this.submitGuiElement(glyphe);
    }

    public void submitPicturesInPictureState(PictureInPictureRenderState zustand)
    {
        this.flush();

        if (this.grafik != null)
        {
            this.grafik.flush();
        }

        BildImBildRegister.zeichnen(zustand, this, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Zeichnet das offene Buendel (auch vor Vanillas GuiGraphics.flush, GuiGraphicsFlushMixin). */
    public void flush()
    {
        if (this.buendel == null)
        {
            return;
        }

        BufferBuilder fertig = this.buendel;

        this.buendel = null;

        MeshData netz = fertig.build();

        if (netz != null)
        {
            GuiZeichner.zeichnen(this.pipeline, this.texturen, this.schere, netz);
        }
    }

    public void nextStratum() {}
    public void blurBeforeThisStratum() {}
    public void up() {}
    public void reset() {}
}
