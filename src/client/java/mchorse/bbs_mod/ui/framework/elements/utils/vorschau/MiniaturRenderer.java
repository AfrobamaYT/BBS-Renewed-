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
public class MiniaturRenderer extends mchorse.bbs_mod.compat.alt.BildImBild<MiniaturZustand>
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
            /* ⛔ KEINE AUSNAHME DARF HIER ENTWEICHEN -- sonst reisst EINE kaputte Form das
             * ganze Spiel.
             *
             * Der Nutzer hat am 2026-08-01 im Morph-Panel geklickt und bekam
             * crash-2026-08-01_17.24.56: "IllegalStateException: Pose stack not empty" aus
             * LevelRenderer.checkPoseStack. Jeder Form-Zeichner klammert seinen Aufbau mit
             * pushPose/popPose -- gezaehlt sind alle ausgeglichen, und ein frueher return
             * zwischen beiden existiert nirgends (beides nachgeprueft). Bleibt der Weg ueber
             * eine AUSNAHME: fliegt sie zwischen push und pop, bleibt der Stapel stehen, und
             * Vanilla bricht beim naechsten Weltdurchgang ab -- weit weg von der Ursache.
             *
             * Vor dem Umbau lief renderInUI inmitten des UI-Durchlaufs; jetzt laeuft es in
             * der Bild-im-Bild-Phase, wo eine Ausnahme niemanden mehr findet, der sie
             * auffaengt. Eine unsichtbare Miniatur ist ein Schoenheitsfehler, ein Absturz
             * kostet dem Nutzer seine Arbeit. */
            try
            {
                mchorse.bbs_mod.forms.FormSammler.mitEigenemSammler(() ->
                    zustand.zeichner().zeichneMiniatur(zustand.kontext(),
                        zustand.x0(), zustand.y0(), zustand.x1(), zustand.y1()));
            }
            catch (Throwable t)
            {
                /* Laut, aber nicht toedlich: der Grund landet im Messpunkt, auch ohne
                 * eingeschaltete Messung. Still verschlucken waere der schlimmere Fehler. */
                Messpunkt.stoerung("miniatur.zeichnen",
                    zustand.zeichner().getClass().getSimpleName() + ": " + t);
            }
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
                Field f = mchorse.bbs_mod.utils.LaufzeitFeld.holen(PictureInPictureRenderer.class, "textureView", "field_60572");
                Field t = mchorse.bbs_mod.utils.LaufzeitFeld.holen(PictureInPictureRenderer.class, "depthTextureView", "field_60573");

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
