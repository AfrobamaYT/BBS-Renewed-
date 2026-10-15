package mchorse.bbs_mod.compat.gpu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Bild-im-Bild-Renderer vor 1.21.6 (dort gibt es Fabrics SpecialGuiElementRegistry nicht);
 * die Regel SpecialGuiElementRegistry.register -> BildImBildRegister.register lenkt hierher.
 */
public final class BildImBildRegister
{
    private static final Map<Class<?>, PictureInPictureRenderer<?>> RENDERER = new HashMap<>();
    private static final java.util.List<Function<Kontext, PictureInPictureRenderer<?>>> FABRIKEN = new java.util.ArrayList<>();

    private BildImBildRegister()
    {}

    public static void register(Function<Kontext, PictureInPictureRenderer<?>> fabrik)
    {
        FABRIKEN.add(fabrik);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void zeichnen(PictureInPictureRenderState zustand, GuiRenderState gui, int guiSkala)
    {
        if (RENDERER.isEmpty())
        {
            Kontext kontext = new Kontext(Minecraft.getInstance().renderBuffers().bufferSource());

            for (Function<Kontext, PictureInPictureRenderer<?>> fabrik : FABRIKEN)
            {
                PictureInPictureRenderer<?> renderer = fabrik.apply(kontext);

                RENDERER.put(renderer.getRenderStateClass(), renderer);
            }
        }

        PictureInPictureRenderer renderer = RENDERER.get(zustand.getClass());

        if (renderer == null)
        {
            throw new IllegalStateException("Kein Bild-im-Bild-Renderer fuer " + zustand.getClass().getName());
        }

        renderer.prepare(zustand, gui, guiSkala);
    }

    /** Wie Fabrics SpecialGuiElementRegistry.Context: liefert den Puffer fuer den Renderer. */
    public record Kontext(MultiBufferSource.BufferSource vertexConsumers)
    {}
}
