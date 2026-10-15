package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;

/**
 * Blenders Achsenzug auf der mittleren Maustaste: waehrend ein G/S/R schon laeuft, druecken,
 * in eine Richtung ziehen, loslassen — die Bearbeitung haengt danach an der Achse, die der
 * Zug am staerksten getroffen hat. Nur die GESTE; die Rechnung "welche Achse" steht in
 * {@link AchsenNaehe}. (Vorlage: {@code transform.cc} TFM_MODAL_AUTOCONSTRAINT +
 * {@code transform_constraints.cc}; Recherche in werkbank/features/mmb-achsenzug/.)
 *
 * <p>⛔ WAS DIESE GESTE NICHT ANFASST: R/G/S bleiben unberuehrt. Der Mittelklick ist ein
 * AUSWAEHLER, kein Zug — er ruft {@code setEditingAxis}, dieselbe Methode wie die Tasten
 * X/Y/Z. Ob die eingeschraenkte Bearbeitung danach ueber den 3D-Strahl oder den
 * Bildschirm-Zug laeuft, entscheidet unveraendert {@link DragStrategyFactory}. Diese Klasse
 * kennt keine Strategie, baut keine, und aendert weder Bezugsrahmen noch Platzierung des
 * Gizmos.
 *
 * <p>Semantik der Vorlage: Druecken oeffnet die Auswahl, Bewegen waehlt fortlaufend neu,
 * Loslassen friert ein; ein Druck WAEHREND der Auswahl bestaetigt, ein spaeterer oeffnet
 * erneut. Shift+Mitteltaste sperrt die getroffene Achse und gibt die EBENE der beiden
 * anderen frei. Gemessen wird ab dem BEGINN der Bearbeitung ({@code t->mouse.imval}) — wer
 * schon gezogen hat und dann antippt, bekommt die Achse sofort.
 *
 * <p>⚠️ KEIN Umschalten global↔lokal beim zweiten Druck — so ist die Vorlage:
 * {@code initSelectConstraint} nimmt den SZENEN-Rahmen ({@code O_SCENE}); der bekannte
 * Rahmen-Wechsel ("XX" = lokales X) lebt im TASTEN-Pfad. BBS-Zwilling von O_SCENE ist der
 * Rahmen-Waehler (Q, {@code BBSSettings.transformSpace}) — die Geste waehlt IM aktuellen
 * Rahmen und ueberschreibt ihn nie; sie ruehrt weder {@code getSpace()} noch
 * {@code isLocal()} an und macht damit weder Gizmo-Platzierung noch den Schnappschuss der
 * laufenden Strahl-Geste kaputt.
 */
public class MittelklickAchse
{
    /** Mittlere Maustaste, wie sie {@code UIContext.mouseButton} meldet. */
    public static final int MITTELTASTE = 2;

    /** Was die Geste vom Wirt braucht — absichtlich klein, damit sie den Wirt nicht kennt. */
    public interface Wirt
    {
        /** Ob gerade eine Bearbeitung laeuft. Ausserhalb einer solchen tut die Geste nichts. */
        boolean istBearbeitung();

        /**
         * Schnappschuss fuer die ACHSENWAHL — einer, der auch dann da ist, wenn die
         * Bearbeitung selbst ohne Zug laeuft.
         *
         * <p>⛔ NICHT dasselbe wie der Zug der laufenden Geste: im Film liefert der
         * Zug-Lieferant absichtlich {@code null}, sobald das Gizmo als Bezugspunkt unbrauchbar
         * ist ({@code FilmGizmoDrag.configureFilmHotkeyDrag}), damit die Bearbeitung auf den
         * Bildschirm-Zug faellt. Genau dort — First Person — soll der Mittelklick trotzdem
         * waehlen koennen: Kamera, Ansicht und Achsen des Gizmos sind gueltig, bloss sein
         * Ursprung ist nicht projizierbar, und damit kommt {@link AchsenNaehe} zurecht.
         * Der Wirt reicht hier deshalb den UNGEFILTERTEN Schnappschuss durch.
         *
         * <p>Wird EINMAL JE TASTENDRUCK gerufen, nicht je Bild: der Aufbau eines Zugs ist im
         * Film teuer (Keyframes anwenden, numerische Ableitungen), und waehrend der Geste
         * steht die Kamera ohnehin still ({@code FilmGizmoDrag.isModalTransformActive}).
         *
         * @return {@code null}, wenn ueberhaupt kein Gizmo gezeichnet wurde — dann schweigt
         *         die Geste.
         */
        GizmoDrag achsenwahlZug();

        /** Der Bezugsrahmen, in dem gerade gearbeitet wird. Wird gelesen, nie gesetzt. */
        TransformSpace raum();

        /**
         * Die Bearbeitung auf eine Achse zwingen — derselbe Eingang wie die Tasten X/Y/Z.
         *
         * @param ebene {@code true} = die Achse sperren und die Ebene der beiden anderen
         *              freigeben (Shift-Bedeutung).
         */
        void aufAchseZwingen(Axis achse, boolean ebene);
    }

    private final Wirt wirt;

    /** Taste haelt: die Achse folgt dem Zeiger, bis losgelassen wird. */
    private boolean waehlend;

    /**
     * Shift lag beim Druck an: Ebene statt Achse. Beim Druck EINGEFROREN — waehrend der
     * Voransicht darf das Loslassen von Shift die Bedeutung der Geste nicht mehr aendern.
     */
    private boolean ebene;

    /** Zug fuer die Wahl, beim Tastendruck EINMAL geholt (siehe {@link Wirt#achsenwahlZug}). */
    private GizmoDrag zug;

    /** Zuletzt gezwungene Achse; {@code null} = noch keine. Verhindert Zwingen ohne Aenderung. */
    private Axis letzteAchse;

    private boolean letzteEbene;

    /*
     * Woher der Zug gemessen wird: der Zeiger beim Beginn der Bearbeitung. Wandert mit, wenn
     * der Zeiger am Bildschirmrand umgesetzt wird — sonst waere der Zug nach einem Umsprung
     * eine halbe Bildbreite gross und die Achse spraenge.
     */
    private int ursprungX;
    private int ursprungY;
    private boolean hatUrsprung;

    public MittelklickAchse(Wirt wirt)
    {
        this.wirt = wirt;
    }

    /** Ob die Voransicht laeuft (Taste haelt) — fuer Anzeigen, die das kennzeichnen wollen. */
    public boolean istWaehlend()
    {
        return this.waehlend;
    }

    /** Der Zeiger beim Beginn der Bearbeitung (Blenders {@code imval}), wrap-mitgefuehrt.
     *  {@code -1}, solange die Bearbeitung noch keinen Anker hat. */
    public int ursprungX()
    {
        return this.hatUrsprung ? this.ursprungX : -1;
    }

    public int ursprungY()
    {
        return this.hatUrsprung ? this.ursprungY : -1;
    }

    /** Shift-Bedeutung der Achsentasten und des Mittelklicks: die getroffene Achse sperren
     *  heisst die EBENE der beiden anderen freigeben. */
    public static Axis[] ebenenPaar(Axis achse)
    {
        switch (achse)
        {
            case X: return new Axis[] {Axis.Y, Axis.Z};
            case Y: return new Axis[] {Axis.Z, Axis.X};
            default: return new Axis[] {Axis.X, Axis.Y};
        }
    }

    /** Beginn einer Bearbeitung: Ursprung setzen, alles andere zuruecksetzen. */
    public void bearbeitungBegonnen(int mausX, int mausY)
    {
        this.zuruecksetzen();
        this.ursprungVersetzen(mausX, mausY);
    }

    /**
     * Ursprung neu setzen, ohne die Geste zu beenden. Zwei Anlaesse, beide im Wirt schon
     * vorhanden: der Zeiger springt am Bildschirmrand auf die andere Seite, und eine
     * Bearbeitung, die ohne Zeiger begonnen hat (-1/-1), bekommt auf dem ersten Bild mit
     * Zeiger ihren echten Ankerpunkt.
     */
    public void ursprungVersetzen(int mausX, int mausY)
    {
        this.ursprungX = mausX;
        this.ursprungY = mausY;
        this.hatUrsprung = mausX >= 0 && mausY >= 0;
    }

    /** Ende der Bearbeitung: nichts darf in die naechste hineinlecken. */
    public void bearbeitungBeendet()
    {
        this.zuruecksetzen();
    }

    /**
     * Tastendruck. Gibt {@code true} zurueck, wenn die Geste ihn verbraucht hat — dann darf
     * ihn nichts darunter mehr sehen.
     *
     * <p>Erster Druck oeffnet die Auswahl, ein Druck WAEHREND der Auswahl bestaetigt (Blenders
     * "Confirm"-Zweig), ein spaeterer Druck oeffnet sie erneut.
     */
    public boolean maustasteGedrueckt(int taste, int mausX, int mausY)
    {
        if (taste != MITTELTASTE || !this.istScharf())
        {
            return false;
        }

        if (this.waehlend)
        {
            this.waehlend = false;
            this.zug = null;

            return true;
        }

        this.waehlend = true;
        this.ebene = Window.isShiftPressed();
        this.zug = this.wirt.achsenwahlZug();

        this.neuWaehlen(mausX, mausY);

        return true;
    }

    /** Loslassen friert die Voransicht ein; die Achse bleibt, die Bearbeitung laeuft weiter. */
    public boolean maustasteLosgelassen(int taste)
    {
        if (taste != MITTELTASTE || !this.waehlend)
        {
            return false;
        }

        this.waehlend = false;
        this.zug = null;

        return true;
    }

    /** Ein Bild weiter: solange die Taste haelt, folgt die Achse dem Zeiger. */
    public void bildAktualisieren(int mausX, int mausY)
    {
        if (this.waehlend && this.istScharf())
        {
            this.neuWaehlen(mausX, mausY);
        }
    }

    /**
     * Achse aus dem Zug bestimmen und — NUR bei echter Aenderung — den Wirt zwingen.
     *
     * <p>⚠️ Die Bedingung ist kein Feinschliff. {@code setEditingAxis} spult die Bearbeitung
     * auf ihre Startwerte zurueck und baut die Geste neu auf; jedes Bild ungefragt zu rufen,
     * hielte die Bearbeitung fest, solange die Taste haelt.
     */
    private void neuWaehlen(int mausX, int mausY)
    {
        if (!this.hatUrsprung || mausX < 0 || mausY < 0)
        {
            return;
        }

        Axis achse = AchsenNaehe.naechsteAchse(this.zug, this.wirt.raum(), mausX - this.ursprungX, mausY - this.ursprungY);

        if (achse == null)
        {
            /* Zug noch zu kurz, kein Gizmo, oder alle Achsen zeigen in die Kamera. Die
             * Auswahl bleibt offen: bewegt der Nutzer weiter, greift sie im naechsten Bild. */
            return;
        }

        if (achse == this.letzteAchse && this.ebene == this.letzteEbene)
        {
            return;
        }

        this.letzteAchse = achse;
        this.letzteEbene = this.ebene;

        this.wirt.aufAchseZwingen(achse, this.ebene);
    }

    /** Abschaltbar per Einstellung, und nur waehrend einer laufenden Bearbeitung scharf. */
    private boolean istScharf()
    {
        return BBSSettings.transformMiddleClickAxis.get() && this.wirt.istBearbeitung();
    }

    private void zuruecksetzen()
    {
        this.waehlend = false;
        this.ebene = false;
        this.zug = null;
        this.letzteAchse = null;
        this.letzteEbene = false;
        this.hatUrsprung = false;
    }
}
