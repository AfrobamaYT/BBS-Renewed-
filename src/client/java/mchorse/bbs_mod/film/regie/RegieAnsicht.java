package mchorse.bbs_mod.film.regie;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Was die Regiehilfe in einem Bild zeigt und wo: Karten, Randpfeile, Wege-Chips und
 * Blick-Fadenkreuze — fertig in Bildschirm-Pixeln. Zeichnet nichts und kennt weder Minecraft
 * noch NanoVG; deshalb laesst sich die Logik ohne Spiel pruefen.
 *
 * <p>Regeln und Zahlen sind dem Design-Export (Regiehilfe · Planen &amp; Spielen) entnommen.
 * Planen = Editor (Karten, Startpunkt, Wege-Chips). Spielen = Aufnahme (Karten mit
 * Handlungstext, Randpfeile, Treffer zaehlen). {@code k} rechnet CSS-Pixel in Bildschirm-Pixel.</p>
 */
public final class RegieAnsicht
{
    public static final int SCHRIFT_TITEL = 0;
    public static final int SCHRIFT_ZIFFER = 1;
    public static final int SCHRIFT_MONO = 2;

    public enum Art { ORT, BLICK, START }

    public enum Phase { BEVOR, AKTIV, ERLEDIGT, RUHE }

    /** Textbreite in CSS-Pixeln (Schriftgroesse und Sperrung ebenso). */
    public interface Messer
    {
        float breite(int schrift, float groesse, float sperrung, String text);
    }

    /** Liegt zwischen Kamera und Punkt feste Welt? */
    public interface Verdeckung
    {
        boolean verdeckt(double x, double y, double z);
    }

    /** In Zeichenreihenfolge: ferne Karten zuerst. */
    public final List<RegieKarte> karten = new ArrayList<>();
    public final List<RegiePfeil> pfeile = new ArrayList<>();
    public final List<RegieWeg> chips = new ArrayList<>();
    public final List<RegieKreuz> kreuze = new ArrayList<>();
    public final RegieTreffer treffer = new RegieTreffer();
    /** Gefundene Wege fuer die Chips (Editor); null = Luftlinie. */
    public RegieWeg.Bekannt wege;

    private final float[] p = new float[3];
    private final Vector3f richtung = new Vector3f();

    /**
     * @param tick    Filmzeit in Ticks (mit Bruchteil)
     * @param spielen Aufnahme statt Editor
     * @param zaehlt  Aufnahme laeuft wirklich (Countdown vorbei) — nur dann zaehlen Treffer
     */
    public void berechnen(Regie regie, float tick, boolean spielen, boolean zaehlt, RegieKamera kamera,
        float breite, float hoehe, float k, Messer messer, Verdeckung verdeckung)
    {
        this.karten.clear();
        this.pfeile.clear();
        this.chips.clear();
        this.kreuze.clear();
        this.treffer.bild(tick / 20F, zaehlt);
        kamera.richtung(this.richtung);

        List<RegieMarker> sortiert = sortiert(regie);
        List<float[]> belegt = new ArrayList<>();
        List<RegieKarte> neu = new ArrayList<>();

        if (!spielen)
        {
            RegieWeg.alle(regie, sortiert, kamera, breite, hoehe, k, this.p, this.wege, this.chips, belegt);
        }

        for (int i = 0; i < sortiert.size(); i++)
        {
            this.marker(sortiert.get(i), String.valueOf(i + 1), tick, spielen, kamera, breite, hoehe, k, verdeckung, neu);
        }

        if (!spielen && regie.startGesetzt.get())
        {
            this.start(regie, kamera, breite, hoehe, k, neu);
        }

        neu.sort(Comparator.comparingDouble((RegieKarte karte) -> karte.prioritaet).reversed());

        for (RegieKarte karte : neu)
        {
            karte.platzieren(spielen, breite, k, messer, belegt);
        }

        neu.sort(Comparator.comparingDouble((RegieKarte karte) -> karte.tiefe).reversed());
        this.karten.addAll(neu);
    }

    private void marker(RegieMarker m, String nummer, float tick, boolean spielen, RegieKamera kamera,
        float breite, float hoehe, float k, Verdeckung verdeckung, List<RegieKarte> neu)
    {
        float n = tick / 20F;
        float start = m.start.get() / 20F;
        float ende = m.ende.get() / 20F;
        Phase phase = tick < m.start.get() ? Phase.BEVOR : tick < m.ende.get() ? Phase.AKTIV : Phase.ERLEDIGT;
        boolean ort = m.istOrt();
        boolean drin = spielen && ort && this.treffer.drin(m, phase == Phase.AKTIV);
        boolean fang = spielen && !ort && phase == Phase.AKTIV && this.treffer.fang(m, kamera, this.richtung);

        if (!ort && (!spielen || phase != Phase.ERLEDIGT || n - ende < 1.5F))
        {
            RegieKreuz kreuz = RegieKreuz.fuer(m, phase, fang, spielen, kamera, breite, hoehe, k, this.p);

            if (kreuz != null)
            {
                this.kreuze.add(kreuz);
            }
        }

        if (spielen && phase == Phase.ERLEDIGT && n - ende > 2.5F)
        {
            return;
        }

        RegieKarte karte = new RegieKarte();
        String rest = zahl(ende - n) + " s";
        double ay = m.y.get() + (ort ? 2.3D : 0.62D);

        karte.art = ort ? Art.ORT : Art.BLICK;
        karte.farbe = m.farbe.get();
        karte.nummer = nummer;
        karte.titel = m.name.get();
        karte.status = phase == Phase.BEVOR ? "in " + zahl(start - n) + " s"
            : phase == Phase.ERLEDIGT ? (spielen && this.treffer.erledigt(m) ? "done" : "missed")
            : !spielen ? "live · " + rest + " left"
            : ort ? (drin ? "hold · " : "go now · ") + rest
            : (fang ? "on target · " : "look now · ") + rest;
        karte.fenster = zahl(start) + "–" + zahl(ende) + " s";
        karte.fortschritt = phase == Phase.BEVOR ? 1F - klemmen((start - n) / 6F, 0F, 1F)
            : phase == Phase.AKTIV ? (ende - n) / Math.max(0.01F, ende - start) : 0F;
        karte.phase = phase;
        karte.ok = drin || fang;
        karte.kompakt = spielen && phase == Phase.BEVOR && start - n > 10F;
        karte.prioritaet = (phase == Phase.AKTIV ? 3F : phase == Phase.BEVOR ? 2F : 1F) - (start - n) * 0.01F;

        if (this.anker(karte, m.x.get(), ay, m.z.get(), spielen, kamera, breite, hoehe, k))
        {
            karte.verdeckt = verdeckung != null && verdeckung.verdeckt(m.x.get(), ay, m.z.get());
            neu.add(karte);
        }

        if (spielen && phase != Phase.ERLEDIGT && !karte.ok && (phase == Phase.AKTIV || start - n < 7F))
        {
            RegiePfeil pfeil = RegiePfeil.fuer(karte.farbe, nummer, phase == Phase.AKTIV, m.x.get(),
                ort ? m.y.get() + 1D : ay, m.z.get(), kamera, breite, hoehe, k, this.p);

            if (pfeil != null)
            {
                /* Nutzer 2026-10-14: der Pfeil zeigt nicht nur wohin, sondern auch wie lange noch. */
                pfeil.zeit = phase == Phase.BEVOR ? "in " + zahl(start - n) + " s" : zahl(ende - n) + " s";
                this.pfeile.add(pfeil);
            }
        }
    }

    /** Startpunkt-Karte, nur im Editor. */
    private void start(Regie regie, RegieKamera kamera, float breite, float hoehe, float k, List<RegieKarte> neu)
    {
        RegieKarte karte = new RegieKarte();

        karte.art = Art.START;
        karte.farbe = 0xFFF2F5FA;
        karte.nummer = "S";
        karte.titel = "Start point";
        karte.status = "Start " + zahl(regie.anfang.get() / 20F) + " s";
        karte.fenster = "Lead-in " + zahl(regie.vorlauf.get() / 20F) + " s";
        karte.phase = Phase.RUHE;
        karte.prioritaet = 0.5F;

        if (this.anker(karte, regie.startX.get(), regie.startY.get() + 2.9D, regie.startZ.get(), false, kamera, breite, hoehe, k))
        {
            neu.add(karte);
        }
    }

    /** Projiziert den Anker; false = Karte ist in diesem Bild nicht zu zeigen. */
    private boolean anker(RegieKarte karte, double ax, double ay, double az, boolean spielen, RegieKamera kamera,
        float breite, float hoehe, float k)
    {
        if (!kamera.projizieren(ax, ay, az, breite, hoehe, this.p))
        {
            return false;
        }

        karte.px = this.p[0];
        karte.py = this.p[1];
        karte.tiefe = this.p[2];

        return spielen
            ? karte.px > -200F * k && karte.px < breite + 200F * k && karte.py > -100F * k && karte.py < hoehe + 300F * k
            : innen(karte.px, karte.py, breite, hoehe, 6F * k);
    }

    public static List<RegieMarker> sortiert(Regie regie)
    {
        List<RegieMarker> liste = new ArrayList<>(regie.marker.getList());

        /* Nur nach Beginn; gleich beginnende bleiben in der Reihenfolge des Anlegens (sort ist
         * stabil). Das Ende als Zweitschluessel schob einen verlaengerten Marker hinter seine
         * Nachbarn -- aus 1, 2, 3 wurde 2, 3, 1 und die Route lief anders (Nutzer 2026-10-13). */
        liste.sort(Comparator.comparingInt((RegieMarker m) -> m.start.get()));

        return liste;
    }

    /** Zeitleisten-Zeile je Marker (nach Beginn sortiert): die erste, deren letzter Balken schon zu Ende ist. */
    public static int[] zeilen(List<RegieMarker> sortiert)
    {
        int[] zeile = new int[sortiert.size()];
        List<Integer> enden = new ArrayList<>();

        for (int i = 0; i < sortiert.size(); i++)
        {
            RegieMarker m = sortiert.get(i);
            int z = 0;

            while (z < enden.size() && enden.get(z) > m.start.get())
            {
                z++;
            }

            if (z == enden.size())
            {
                enden.add(0);
            }

            enden.set(z, m.ende.get());
            zeile[i] = z;
        }

        return zeile;
    }

    /** Ende des Abschnitts in Ticks: bis der letzte Marker endet, mindestens 1 s nach dem Anfang. */
    public static int abschnittEnde(Regie regie)
    {
        int ende = regie.anfang.get() + 20;

        for (RegieMarker m : regie.marker.getList())
        {
            ende = Math.max(ende, m.ende.get());
        }

        return ende;
    }

    static boolean innen(float x, float y, float breite, float hoehe, float rand)
    {
        return x >= rand && x <= breite - rand && y >= rand && y <= hoehe - rand;
    }

    /** Eine Nachkommastelle mit Punkt („5.5") — die Mod spricht Englisch. */
    public static String zahl(float wert)
    {
        return String.format(Locale.ROOT, "%.1f", Math.round(wert * 10F) / 10F).replace("-0.0", "0.0");
    }

    static float klemmen(float wert, float min, float max)
    {
        return wert < min ? min : Math.min(wert, max);
    }
}
