package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.graphics.Messpunkt;
import mchorse.bbs_mod.graphics.Zeichenziel;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.lang.reflect.Field;

/**
 * Zeichnet alle Formvorschauen eines Bildes in eine gemeinsame, bildschirmgrosse Textur.
 *
 * <p>Siehe {@link MiniaturSammelZustand} fuer den Grund. Kurz: die GUI von 26.2 zeichnet in
 * zwei Phasen, ein Sofortzug landet deshalb UNTER dem Panel-Hintergrund — und eine
 * bildschirmgrosse Textur ist der einzige Bezugsrahmen, in dem {@code renderInUI} ohne
 * Umrechnung weiterrechnen kann.</p>
 */
public class MiniaturSammelRenderer extends PictureInPictureRenderer<MiniaturSammelZustand>
{
    private static Field farbFeld;
    private static Field tiefenFeld;

    @Override
    public Class<MiniaturSammelZustand> getRenderStateClass()
    {
        return MiniaturSammelZustand.class;
    }

    @Override
    protected String getTextureLabel()
    {
        return "bbs miniaturen";
    }

    @Override
    protected void renderToTexture(MiniaturSammelZustand zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        GpuTextureView farbe = feld(true);
        GpuTextureView tiefe = feld(false);

        Messpunkt.zaehle("pip.miniaturen",
            zustand.eintraege().size() + " stueck ziel=" + (farbe != null));

        if (farbe == null || zustand.eintraege().isEmpty())
        {
            return;
        }

        Zeichenziel.setzen(farbe, tiefe);

        try
        {
            for (MiniaturZustand e : zustand.eintraege())
            {
                if (e.zeichner() == null || e.kontext() == null)
                {
                    continue;
                }

                /* ⭐ EIGENE GRUPPE JE KACHEL IM FRAME-DEBUGGER (2026-08-04, Bugliste #4).
                 * Ohne sie steht jede Vorschau als namenloser Zeichenaufruf in einer flachen
                 * Liste -- beim Umriss-Versatz waren das 1776 Stueck, und den richtigen zu
                 * finden kostete zwei Tage. Ein GL-Aufruf je Kachel, kein Schalter davor
                 * (siehe ApiTraceBridge#pushGroup: genau daran hing es vorher). */
                com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.pushGroup(
                    "bbs-miniatur " + e.zeichner().getClass().getSimpleName()
                        + " @" + e.x0() + "," + e.y0());

                /* ⛔ JEDE VORSCHAU EINZELN ABGESICHERT. Ohne das reisst EINE kaputte Form
                 * alle uebrigen mit -- und weil hier kein Aufrufer mehr wartet, der die
                 * Ausnahme auffaengt, danach das ganze Spiel. Eine unsichtbare Miniatur ist
                 * ein Schoenheitsfehler, ein Absturz kostet dem Nutzer seine Szene.
                 *
                 * Der eigene Sammler ist ebenfalls Pflicht: Formen reichen ihre Geometrie
                 * bei einem SubmitNodeCollector ein, und der des UI-Durchlaufs ist hier
                 * laengst geschlossen (FormSammler.pflicht wirft sonst "Kein Sammler
                 * angemeldet", in JEDEM Bild). */
                /* ⛔ DIE MAUS MUSS IN DENSELBEN RAUM WIE DIE KACHEL (2026-08-04, Bugliste #13).
                 *
                 * Miniaturschlange legt die Kachel in BILDPUNKTEN ab (px1 = x1 * guiScale),
                 * weil die Sammeltextur in Bildpunkten liegt. {@code context.mouseX} bleibt
                 * aber in GUI-Einheiten -- und ModelFormRenderer#getUIMatrix rechnet beides
                 * gegeneinander:
                 *
                 *     angle = toRad(context.mouseX - (x1 + x2) / 2) + PI
                 *
                 * Bei GUI-Skalierung 2 ist die Kachelmitte doppelt so gross wie die Maus, die
                 * Vorschau dreht sich also um die halbe Kachelmitte zu weit. Gemessen an der
                 * laufenden Instanz (Silhouettenbreite, Kachel GUI 45..85 = Punkte 90..170):
                 *
                 *     Maus GUI  65 (GUI-Mitte)   -> 20 px breit  (Modell steht seitlich)
                 *     Maus GUI 130 (Punkt-Mitte) -> 30 px breit  (Modell steht frontal)
                 *
                 * Frontal wird es an der PUNKT-Mitte statt an der GUI-Mitte -- genau der
                 * Faktor guiScale. Hier umgerechnet und im finally zurueckgesetzt: es ist die
                 * eine Stelle, an der beide Raeume aufeinandertreffen. */
                mchorse.bbs_mod.ui.framework.UIContext kontext = e.kontext();
                int mausX = kontext.mouseX;
                int mausY = kontext.mouseY;
                float skalierung = mchorse.bbs_mod.BBSModClient.getGUIScale();

                kontext.mouseX = Math.round(mausX * skalierung);
                kontext.mouseY = Math.round(mausY * skalierung);

                try
                {
                    mchorse.bbs_mod.forms.FormSammler.mitEigenemSammler(() ->
                        e.zeichner().zeichneMiniatur(e.kontext(),
                            e.x0(), e.y0(), e.x1(), e.y1()));
                }
                catch (Throwable t)
                {
                    Messpunkt.stoerung("miniatur.zeichnen",
                        e.zeichner().getClass().getSimpleName() + ": " + t);
                }
                finally
                {
                    /* Zurueck in GUI-Einheiten -- der naechste Leser ist wieder die
                     * Oberflaeche selbst, und die rechnet dort. */
                    kontext.mouseX = mausX;
                    kontext.mouseY = mausY;

                    /* Paarig zum pushGroup oben: ein Push ohne Pop verschachtelt den Event
                     * Browser falsch, und das ist schlimmer als gar kein Marker. */
                    com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.popGroup();
                }
            }
        }
        finally
        {
            /* ⛔ IMMER freigeben. Bliebe das Ziel stehen, zeichnete alles Folgende -- Welt
             * eingeschlossen -- in diese Vorschautextur (die Falle von 06fbc62e0). */
            Zeichenziel.freigeben();
        }
    }

    /**
     * Das Farb- oder Tiefenziel dieses Zeichners, per Reflexion.
     *
     * <p>⛔ SCHEITERT LAUT, nicht still — ein {@code catch}, das nur {@code null} liefert,
     * verwandelt einen weggebrochenen Feldnamen in unsichtbare Miniaturen ohne jede Spur.</p>
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
            Messpunkt.stoerung("miniaturen.reflexion",
                e.getClass().getSimpleName() + ": " + e.getMessage());

            return null;
        }
    }
}
