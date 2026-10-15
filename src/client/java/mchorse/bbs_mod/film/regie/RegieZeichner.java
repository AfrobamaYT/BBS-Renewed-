package mchorse.bbs_mod.film.regie;

import com.mcstudio.nano.NanoEbene;

/**
 * Zeichnet eine {@link RegieAnsicht} mit NanoVG — die Bildschirmschicht der Regiehilfe.
 * Chips und Fadenkreuze liegen unten, Karten darueber, Randpfeile obenauf.
 */
public final class RegieZeichner
{
    private final RegieStift stift = new RegieStift();
    private final RegieKartenZeichner karten = new RegieKartenZeichner(this.stift);
    private final RegieMarkenZeichner marken = new RegieMarkenZeichner(this.stift);

    /** Schriften des Designs (SIL OFL, Lizenzen liegen daneben) im geteilten Kontext anmelden. */
    public static void schriftenAnmelden(long vg)
    {
        NanoEbene.schrift(vg, RegieStift.TITEL, "/assets/bbs/fonts/nano/SairaSemiCondensed-Bold.ttf");
        NanoEbene.schrift(vg, RegieStift.ZIFFER, "/assets/bbs/fonts/nano/SairaSemiCondensed-ExtraBold.ttf");
        NanoEbene.schrift(vg, RegieStift.MONO, "/assets/bbs/fonts/nano/JetBrainsMono-SemiBold.ttf");
    }

    public RegieAnsicht.Messer messer(long vg)
    {
        return (schrift, groesse, sperrung, text) -> this.stift.breite(vg,
            schrift == RegieAnsicht.SCHRIFT_MONO ? RegieStift.MONO : schrift == RegieAnsicht.SCHRIFT_ZIFFER ? RegieStift.ZIFFER : RegieStift.TITEL,
            groesse, sperrung, text);
    }

    /** @param zeit Sekunden, nur fuer die Animationen (Puls, Streifen, Glanz) */
    public void zeichnen(long vg, RegieAnsicht ansicht, float zeit, float k)
    {
        for (RegieWeg chip : ansicht.chips)
        {
            this.marken.chip(vg, chip, k);
        }

        for (RegieKreuz kreuz : ansicht.kreuze)
        {
            this.marken.kreuz(vg, kreuz, zeit, k);
        }

        for (RegieKarte karte : ansicht.karten)
        {
            this.karten.karte(vg, karte, zeit);
        }

        for (RegiePfeil pfeil : ansicht.pfeile)
        {
            this.marken.pfeil(vg, pfeil, zeit, k);
        }
    }
}
