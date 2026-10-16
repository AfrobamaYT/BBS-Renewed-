package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.renderpearl.api.textures.GpuTextureView;
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
            zustand.eintraege().size() + " stueck ziel=" + (farbe != null)
                + " tiefe=" + (tiefe != null)
                + " arten=" + zustand.eintraege().stream()
                    .map(e -> e.zeichner() == null ? "null"
                        : e.zeichner().getClass().getSimpleName().replace("FormRenderer", ""))
                    .distinct().sorted().toList());

        if (farbe == null || zustand.eintraege().isEmpty())
        {
            return;
        }

        /* ⚗️ WIDERLEGT 2026-08-05, damit es niemand nochmal bezahlt: hier stand ein leerer
         * Durchgang, der die Tiefe auf 0.0 loescht -- dieselbe Massnahme, die #17a geloest
         * hat. Sie AENDERT HIER NICHTS. Gemessen: `tiefe=true` (das Ziel existiert, die
         * Loeschung lief also wirklich) und die Modell-Miniaturen blieben unsichtbar.
         * Die Ursache von #13 liegt woanders. */

        /* ⛔ DIE ABBILDUNG GEHOERT DIESEM DURCHGANG (2026-08-05, Bugliste #13).
         *
         * Gemessen am Modellzug in dieser Schleife:
         *
         *     proj[m00=0.7505 m11=1.4281 m33=0.0]   <- m33=0 heisst PERSPEKTIVE (die der Welt)
         *     mv[m30=420.0 m31=396.9 m32=64.5]      <- Modellansicht in BILDPUNKTEN
         *
         * Eine Weltperspektive auf pixelraum-Geometrie: die Kachel liegt bei z=+64, also
         * hinter der Kamera, und es entsteht KEIN Fragment. Kein Fehler, keine Logzeile, alle
         * Zaehler voll -- 170 Zuege je Bild, und die Sammeltextur trug 3562 von 1939200
         * Punkten. Genau die Falle, die in Bildfilter beschrieben steht ("1008-mal gezeichnet
         * und nie zu sehen").
         *
         * Es traf nur die Formen, die ueber ModelVAORenderer gehen -- Model, Block, Extruded,
         * Trail. Anker, Label, Gegenstand, Partikel und Billboard zeichnen flach und ohne
         * diese Abbildung, die waren immer da.
         *
         * ⚗️ EINE FLACHE PROJEKTION HIER AENDERT NICHTS -- GEMESSEN UND WIDERLEGT.
         * Naheliegend war, es wie VorschauGitterRenderer zu machen und
         * `Projektion.sichern() / setzenFlach(ortho ueber die Texturgroesse) / zurueck()`
         * um die Schleife zu legen. Ergebnis: bunte Proben in den Modellreihen 186 vorher,
         * 186 nachher. Vermutung fuer den naechsten Anlauf, NICHT gemessen: `Projektion`
         * setzt BBS' gespeicherte Abbildung, der Zug bindet seine Uniforms aber selbst
         * (`RenderSystem.getDynamicUniforms().writeTransform(...)`) -- dann liest auch die
         * Sonde oben nicht den Wert, den der Zeichenaufruf wirklich benutzt (Regel 1j).
         * Der naechste Schritt gehoert also an writeTransform, nicht hierher. */
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
                mchorse.bbs_mod.sonden.Sonden.gpu.gruppeAuf(mchorse.bbs_mod.sonden.Sonden.gpu.marken()
                    ? "bbs-miniatur " + e.zeichner().getClass().getSimpleName()
                        + " @" + e.x0() + "," + e.y0()
                    : null);

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

                /* ⛔ BESCHNITT AUF DIE ZELLE -- er war im Port ersatzlos weg (2026-08-05).
                 *
                 * Im Frame gemessen (RenderDoc-Capture, jede Kachel traegt ihren eigenen
                 * Marker, siehe pushGroup oben):
                 *
                 *     17 von 17 Kacheln:  viewport = 0,0 854x480   scissor = 0,0 854x480
                 *
                 * Also KEINE einzige auf ihre Zelle begrenzt. Darum ragen zu grosse Vorschauen
                 * (Gegenstand, Billboard, Label) in die Nachbarzelle, ueber die Kategorie-
                 * Ueberschrift und ueber die Suchleiste.
                 *
                 * Unsere 1.21.1 klammert JEDE Zelle in {@code context.batcher.clip(cx, cy,
                 * CELL_W, CELL_H)} / {@code unclip} -- sechs solche Paare in UIFormList. Beim
                 * Port sind die Kacheln in diese SPAETE Sammeltextur gewandert und damit aus
                 * allen Clips der Oberflaeche gefallen; der Beschnitt gehoert deshalb hierher.
                 *
                 * ⚠️ y GESPIEGELT. Die Kachelgrenzen liegen in Bildpunkten mit Ursprung OBEN
                 * (so legt Miniaturschlange sie ab), {@link RenderPass.RenderArea} rechnet von
                 * UNTEN. Dieselbe Umrechnung wie {@code UIUtils#viewportArea}. Wer sie
                 * vergisst, beschneidet die gespiegelte Zeile -- die Kachel bliebe leer und
                 * saehe aus wie der Fehler, den das hier behebt. */
                /* ⛔ SELBST ZURECHTSCHNEIDEN, NICHT begrenztAuf DIE ARBEIT GEBEN (2026-08-05,
                 * am toten Spiel gelernt). Die erste Fassung reichte den rohen Zellrahmen
                 * durch. Kacheln UNTERHALB des sichtbaren Bereichs haben y1 > Zielhoehe, also
                 * ein negatives y -- und `begrenztAuf` schneidet die zwar zurecht, MELDET das
                 * aber bei jeder neuen Groessen-Kombination. Je Kachel, jedes Bild:
                 *
                 *     [bbs] Zeichenbereich passt nicht zum Ziel: 0,-120,120,160 -> 854x480
                 *
                 * Ergebnis nach knapp drei Stunden: bbstools-console.log 61,2 MB, Heap
                 * 2201 MB, 10 fps, Instanz tot. Die Meldung ist RICHTIG und soll bleiben --
                 * sie faengt die echte Fehlpaarung (Fenstergroesse gegen Videogroesse,
                 * Bugliste #9). Falsch war, ihr hier absehbaren Muell zu schicken.
                 *
                 * Eine Kachel ganz ausserhalb des Ziels kann nichts Sichtbares zeichnen; sie
                 * wird uebersprungen statt ungeschnitten gezeichnet (ungeschnitten wuerde sie
                 * in den sichtbaren Bereich malen -- genau das, was der Beschnitt verhindert). */
                int zielB = farbe.getWidth(0);
                int zielH = farbe.getHeight(0);

                /* ⛔ DEN SCHNITT DES ZUSTANDS NEHMEN, NICHT DIE ZELLE (2026-08-05, im A/B
                 * gegen 1.21.1 gefunden).
                 *
                 * Hier stand die ZELLE (`e.x0()..e.y1()`). Damit wurde jede Miniatur auf ihre
                 * volle Kachel beschnitten -- also gar nicht. `UIReplayList#renderElementPart`
                 * klemmt seine 40x40-Vorschau aber mit `clip(formX, y, 40, 20)` auf halbe
                 * Hoehe; in 1.21.1 wirkt das (kompaktes Bild am Zeilenrand), im Port lief die
                 * Miniatur ueberlang aus der Zeile. `Miniaturschlange#einreihen` legt den
                 * gueltigen Schnitt seit demselben Tag in `scissorArea` ab -- er muss hier
                 * auch benutzt werden, sonst ist er nur Zierde. */
                net.minecraft.client.gui.navigation.ScreenRectangle sicht = e.scissorArea() != null ? e.scissorArea() : e.bounds();

                int sx = Math.max(0, sicht.left());
                int sy = Math.max(0, zielH - sicht.bottom());
                int sb = Math.min(sicht.right(), zielB) - sx;
                int sh = Math.min(zielH - sicht.top(), zielH) - sy;

                /* Messpunkt Beschnitt: WELCHE Kachel bekommt welchen Schnitt, und wird sie
                 * uebersprungen? Ohne das ist "Icon verschwindet beim Scrollen" nicht von
                 * "Icon laeuft ueber den Bildschirm" zu unterscheiden. */
                Messpunkt.zaehle("beschnitt." + e.zeichner().getClass().getSimpleName(),
                    "zelle " + e.x0() + "," + e.y0() + "-" + e.x1() + "," + e.y1()
                    + "  schnittquelle=" + (e.scissorArea() != null ? "scissorArea" : "bounds")
                    + " " + sicht.left() + "," + sicht.top() + "-" + sicht.right() + "," + sicht.bottom()
                    + "  ziel " + zielB + "x" + zielH
                    + "  -> gl " + sx + "," + sy + " " + sb + "x" + sh
                    + (sb <= 0 || sh <= 0 ? "  UEBERSPRUNGEN" : ""));

                if (sb <= 0 || sh <= 0)
                {
                    /* ⛔ DIE MAUS STEHT HIER SCHON IN BILDPUNKTEN (oben umgerechnet), und das
                     * finally, das sie zuruecksetzt, kommt erst im try darunter. Ein nacktes
                     * `continue` liesse die Oberflaeche mit Punkt-Koordinaten weiterarbeiten --
                     * bei Skalierung 2 zeigt der Zeiger dann auf die doppelte Stelle. Beim
                     * Schreiben dieses Zweigs genau einmal passiert, darum steht es dabei. */
                    kontext.mouseX = mausX;
                    kontext.mouseY = mausY;

                    mchorse.bbs_mod.sonden.Sonden.gpu.gruppeZu();

                    continue;
                }

                /* ⛔ JEDER WEG SETZT SEIN EIGENES LICHT (2026-08-11 gemessen). Die Zuege
                 * laufen AUFGESCHOBEN -- ein Abmelden am Ende des Editor-Renderns kommt zu
                 * frueh, `UiLicht.scheibe()` sah dort immer `null`. Also meldet nicht der
                 * Editor ab, sondern jeder Zeichner meldet an, was er braucht: eine Kachel
                 * hat keine Umlaufkamera, sie nimmt die festen Richtungen. */
                mchorse.bbs_mod.graphics.UiLicht.ansichtLoeschen();

                mchorse.bbs_mod.graphics.Zeichenbereich.setzen(sx, sy, sb, sh);

                /* ⛔ ZEICHENBEREICH ERREICHT NUR BBS-EIGENE DURCHGAENGE (2026-08-11 gemessen).
                 *
                 * Verbraucher von {@code Zeichenbereich.aktiv()} sind ModelVAORenderer und
                 * GehaltenesNetz -- beides BBS. Mobs, Bloecke und Gegenstaende zeichnet aber
                 * Mojangs {@code FeatureRenderDispatcher.renderAllFeatures} in Durchgaengen, die
                 * er selbst oeffnet. Die liefen ungeschnitten: die Mob-Vorschau im Morph-Panel
                 * ragte 57 Bildpunkte unter ihre Zelle und malte die Kategorie-Beschriftung zu.
                 *
                 * Bis 1.21.1 erledigte das der globale {@code RenderSystem.viewport}. Dessen
                 * Nachfolger in 26.2 ist genau dieser Aufruf: {@code RenderType} nimmt den
                 * Zustand ueber {@code getScissorStateForRenderTypeDraws} in jeden vorbereiteten
                 * Zug auf. Mojang benutzt ihn selbst fuer dieselbe Aufgabe -- {@code GuiItemAtlas}
                 * klemmt damit seine Gegenstaende in ihr Feld.
                 *
                 * Gleiche vier Zahlen wie der Zeichenbereich: beide rechnen von UNTEN. */
                com.mojang.blaze3d.systems.RenderSystem.enableScissorForRenderTypeDraws(sx, sy, sb, sh);

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

                    /* ⛔ LAUT, NICHT NUR IM MESSPUNKT. Ein Zeichner, der nach seinem
                     * pushPose wirft, laesst den Stapel stehen -- und das Bild stirbt
                     * spaeter an "Pose stack not empty" in LevelRenderer.checkPoseStack,
                     * mit einem Stapelabzug ohne eine einzige BBS-Zeile. Solange dieser
                     * Fang stumm war, war die Ursache unsichtbar. */
                    System.err.println("[miniatur-wurf] "
                        + e.zeichner().getClass().getSimpleName() + ": " + t);
                    t.printStackTrace();
                }
                finally
                {
                    /* Wer setzen ruft, gibt selbst frei -- Pflicht des Aufrufers, nicht
                     * Eigenschaft der Klasse (Zeichenbereich, Absatz dazu). Ein stehen
                     * gebliebener Ausschnitt beschneidet JEDEN folgenden Netz-Zug. */
                    mchorse.bbs_mod.graphics.Zeichenbereich.freigeben();

                    /* Derselbe Grund, groessere Reichweite: dieser Zustand ist global und
                     * haengt an JEDEM folgenden RenderType-Zug. Stehen gelassen klemmt er die
                     * ganze Welt in eine Zelle. */
                    com.mojang.blaze3d.systems.RenderSystem.disableScissorForRenderTypeDraws();

                    /* Zurueck in GUI-Einheiten -- der naechste Leser ist wieder die
                     * Oberflaeche selbst, und die rechnet dort. */
                    kontext.mouseX = mausX;
                    kontext.mouseY = mausY;

                    /* Paarig zum pushGroup oben: ein Push ohne Pop verschachtelt den Event
                     * Browser falsch, und das ist schlimmer als gar kein Marker. */
                    mchorse.bbs_mod.sonden.Sonden.gpu.gruppeZu();
                }
            }
        }
        finally
        {
            /* ⛔ IMMER freigeben. Bliebe das Ziel stehen, zeichnete alles Folgende -- Welt
             * eingeschlossen -- in diese Vorschautextur (die Falle von 06fbc62e0). */
            Zeichenziel.freigeben();

            /* MESSUNG (2026-08-04): liegt ueberhaupt Inhalt in der Sammeltextur? Die Probe
             * liest nur, wenn sie angefordert wurde -- sonst kostet sie einen Vergleich. */
            if (farbe instanceof com.mojang.renderpearl.backend.opengl.GlTextureView ansicht)
            {
                var tex = ansicht.texture();

                mchorse.bbs_mod.graphics.Pufferprobe.vielleichtFarbeZaehlen(ansicht.glId(),
                    tex.getWidth(0), tex.getHeight(0),
                    255, 255, 255, 40, "sammeltextur ECHTE groesse " + tex.getWidth(0) + "x"
                        + tex.getHeight(0) + " zustand " + (zustand.x1() - zustand.x0()) + "x"
                        + (zustand.y1() - zustand.y0()));
            }
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
