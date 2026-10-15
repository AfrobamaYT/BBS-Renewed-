package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.Objects;
//? if <26.3 {
import java.util.Optional;
import java.util.OptionalDouble;
//?}

/**
 * Buendelt aufeinanderfolgende {@link GehaltenesNetz}-Zuege mit demselben Ziel in EINEM
 * RenderPass.
 *
 * <h2>Warum</h2>
 *
 * <p>Jeder Zug oeffnete bisher einen eigenen Durchgang. In 26.2 bindet das Oeffnen den
 * Framebuffer des Ziels und das Schliessen wieder Framebuffer 0 -- getrennt fuer Lesen und
 * Schreiben, also bis zu vier {@code glBindFramebuffer} je Zug. Gemessen am Filmeditor mit
 * ausgewaehltem Knochen (27.09.2026): 31,5 Durchgaenge je Bild, 16 davon aus dem Gizmo, und
 * {@code glBindFramebuffer} trug 25,8 % der Renderthread-Samples. Ein Durchgang darf mehrere
 * Zuege mit wechselnder Pipeline, Uniforms und Puffern enthalten; Vanilla zeichnet die ganze
 * Oberflaeche so.
 *
 * <h2>Grenzen</h2>
 *
 * <ul>
 *   <li>Nur innerhalb von {@link #beginnen()}/{@link #beenden()}. Ausserhalb verhaelt sich
 *       {@link GehaltenesNetz} unveraendert.</li>
 *   <li>Waehrend der Durchgang offen ist, verbietet Blaze3D jeden anderen Encoder-Befehl
 *       (neuer Durchgang, Loeschen, Kopieren) und wirft laut. Der Bereich gehoert darum nur
 *       um Code, der ausschliesslich ueber GehaltenesNetz/SofortZeichner zeichnet.</li>
 *   <li>Anderes Ziel, anderes Tiefenziel oder anderer Ausschnitt schliesst den offenen
 *       Durchgang und oeffnet einen neuen -- die Reihenfolge der Zuege bleibt erhalten.</li>
 *   <li>Das GL-Backend fuehrt jeden Zug sofort aus; rohe GL-Zustaende zwischen zwei Zuegen
 *       (etwa {@code glDepthRange} im Gizmo) wirken darum wie vorher.</li>
 * </ul>
 */
public final class Sammelpass
{
    private static int tiefe;
    private static RenderPass offen;
    private static GpuTextureView offenFarbe;
    private static GpuTextureView offenTiefe;
    private static RenderPass.RenderArea offenBereich;
    private static Framebuffer.GlPassBinding offenGl;

    public static void beginnen()
    {
        RenderSystem.assertOnRenderThread();

        tiefe += 1;
    }

    /** Immer in einem {@code finally} rufen: ein offener Durchgang sperrt sonst jeden Encoder. */
    public static void beenden()
    {
        if (tiefe <= 0)
        {
            throw new IllegalStateException("Sammelpass.beenden() ohne beginnen()");
        }

        tiefe -= 1;

        if (tiefe == 0)
        {
            schliessen();
        }
    }

    /**
     * @return den offenen Durchgang fuer genau dieses Ziel (bei Bedarf neu geoeffnet), oder
     *         {@code null} ausserhalb eines Sammelbereichs -- dann oeffnet der Aufrufer selbst.
     */
    static RenderPass holen(GpuTextureView farbZiel, GpuTextureView tiefenZiel, RenderPass.RenderArea bereich)
    {
        if (tiefe == 0)
        {
            return null;
        }

        RenderPass.RenderArea schluessel = bereich;

        if (offen != null && offenFarbe == farbZiel && offenTiefe == tiefenZiel
            && Objects.equals(offenBereich, schluessel))
        {
            return offen;
        }

        schliessen();

        //? if >=26.3 {
        /*RenderPassDescriptor descriptor = Framebuffer.descriptorFor("bbs-sammelpass", farbZiel, tiefenZiel, bereich);
        if (descriptor == null)
        {
            var builder = RenderPassDescriptor.create(() -> "bbs-sammelpass").withColorAttachment(farbZiel);
            if (tiefenZiel != null) builder.withDepthAttachment(tiefenZiel);
            if (bereich != null) builder.withRenderArea(bereich);
            descriptor = builder.build();
        }
        offenGl = Framebuffer.glPassBindingFor(farbZiel, tiefenZiel);
        try
        {
            offen = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor);
            offenGl.bind();
        }
        catch (RuntimeException | Error failure)
        {
            offenGl.restore(); offenGl = null;
            throw failure;
        }
        *///?} else {
        offen = tiefenZiel == null
            ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "bbs-sammelpass", farbZiel, Optional.empty())
            : schluessel == null
                ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "bbs-sammelpass", farbZiel, Optional.empty(), tiefenZiel, OptionalDouble.empty())
                : RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "bbs-sammelpass", farbZiel, Optional.empty(), tiefenZiel, OptionalDouble.empty(), schluessel);
        //?}
        offenFarbe = farbZiel;
        offenTiefe = tiefenZiel;
        offenBereich = schluessel;

        return offen;
    }

    private static void schliessen()
    {
        RenderPass durchgang = offen;
        Framebuffer.GlPassBinding gl = offenGl;

        offen = null;
        offenFarbe = null;
        offenTiefe = null;
        offenBereich = null;
        offenGl = null;

        if (durchgang != null)
        {
            try { durchgang.close(); }
            finally { if (gl != null) gl.restore(); }
        }
    }

    private Sammelpass()
    {}
}
