package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.graphics.Messpunkt;
import mchorse.bbs_mod.graphics.Zeichenziel;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.lang.reflect.Field;

/**
 * Zeichnet eine Formvorschau der Oberflaeche in eine eigene Textur.
 *
 * <p>Der Zeichenaufruf selbst bleibt unveraendert — {@code FormRenderer.renderInUI} baut
 * seine Ansicht weiter aus dem uebergebenen Rechteck. Neu ist nur, WOHIN er zeichnet:
 * {@link Zeichenziel} lenkt den Modell-Durchgang in die Textur dieses Zeichners, und der
 * fertige Ausschnitt wird IM GUI-Strom geblittet, also nach dem Panel-Hintergrund statt
 * darunter. Siehe {@link MiniaturZustand} fuer den Befund dahinter.</p>
 *
 * <p>⛔ Das Ziel wird IMMER freigegeben. Bliebe es stehen, zeichnete alles Folgende — Welt
 * eingeschlossen — in diese Vorschautextur. Dieselbe Falle hat bei {@code Zeichenbereich}
 * schon einmal zugeschlagen (06fbc62e0).</p>
 */
public class MiniaturRenderer extends PictureInPictureRenderer<MiniaturZustand>
{
    private static Field farbFeld;
    private static Field tiefenFeld;

    @Override
    public Class<MiniaturZustand> getRenderStateClass()
    {
        return MiniaturZustand.class;
    }

    @Override
    protected String getTextureLabel()
    {
        return "bbs miniatur";
    }

    @Override
    protected void renderToTexture(MiniaturZustand zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        GpuTextureView farbe = feld(true);
        GpuTextureView tiefe = feld(false);

        Messpunkt.zaehle("pip.miniatur", zustand.zeichner() == null
            ? "zeichner=null"
            : zustand.zeichner().getClass().getSimpleName() + " ziel=" + (farbe != null));

        if (zustand.zeichner() == null || zustand.kontext() == null || farbe == null)
        {
            return;
        }

        Zeichenziel.setzen(farbe, tiefe);

        try
        {
            /* ⛔ EIGENER SAMMLER, SONST STIRBT DAS SPIEL. Formen reichen ihre Geometrie bei
             * einem SubmitNodeCollector ein; in einem Weltdurchgang meldet der Weltzeichner
             * ihn an, hier gibt es keinen. Ohne diese Klammer wirft FormSammler.pflicht()
             * "Kein Sammler angemeldet" -- und zwar in JEDEM Bild, was die Instanz sofort
             * reisst (am 2026-08-01 genau so passiert, crash-2026-08-01_17.04.14).
             *
             * mitEigenemSammler macht die drei Schritte, die Vanilla in
             * PictureInPictureRenderer.prepare auch macht: frischen Speicher anlegen,
             * einreichen lassen, und die eingereichten Auftraege ZEICHNEN lassen. Ohne den
             * dritten Schritt sammelt man Auftraege, die niemand ausfuehrt -- die Miniatur
             * bliebe leer, ohne jede Meldung. */
            mchorse.bbs_mod.forms.FormSammler.mitEigenemSammler(() ->
                zustand.zeichner().zeichneMiniatur(zustand.kontext(),
                    zustand.x0(), zustand.y0(), zustand.x1(), zustand.y1()));
        }
        finally
        {
            Zeichenziel.freigeben();
        }
    }

    /**
     * Das Farb- oder Tiefenziel dieses Zeichners, per Reflexion.
     *
     * <p>⛔ SCHEITERT LAUT, nicht still — ein {@code catch}, das nur {@code null} liefert,
     * verwandelt einen weggebrochenen Feldnamen in unsichtbare Miniaturen ohne jede Spur.
     * Genau daran hing Aufgabe 96 wochenlang.</p>
     */
    private GpuTextureView feld(boolean farbe)
    {
        try
        {
            if (farbFeld == null)
            {
                Field f = PictureInPictureRenderer.class.getDeclaredField("textureView");
                Field t = PictureInPictureRenderer.class.getDeclaredField("depthTextureView");

                f.setAccessible(true);
                t.setAccessible(true);

                farbFeld = f;
                tiefenFeld = t;
            }

            return (GpuTextureView) (farbe ? farbFeld : tiefenFeld).get(this);
        }
        catch (Exception e)
        {
            Messpunkt.stoerung("miniatur.reflexion",
                e.getClass().getSimpleName() + ": " + e.getMessage());

            return null;
        }
    }
}
