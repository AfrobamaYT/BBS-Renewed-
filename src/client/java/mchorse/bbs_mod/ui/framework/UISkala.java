package mchorse.bbs_mod.ui.framework;

import com.mojang.blaze3d.platform.Window;
import mchorse.bbs_mod.BBSModClient;
import net.minecraft.client.Minecraft;

/**
 * Stufenloser UI-Scale — Zwischenwerte wie 1,5 oder 2,25, wie Blenders Resolution Scale.
 *
 * <p>⛔ WOFUER (Nutzerbefund 2026-10-13): „Kommazahlen werden nicht uebernommen". Seit 1.21.6
 * kennt Minecraft nur ganzzahlige GUI-Skalen ({@code Window.setGuiScale(int)}), und BBS rundete
 * {@code ui_scale} stumm: 1,5 bis 2,4 ergab 2, 1,4 ergab 1.</p>
 *
 * <p>Etablierter Weg (DebugGUIScale fuer 26.2, Jsputer GUI): Minecraft bekommt die naechst-
 * HOEHERE ganze Zahl, die eigene Oberflaeche wird ueber die Zeichenmatrix um den Rest
 * ({@link #rest()}, hoechstens 1) verkleinert. Herunterskaliert bleibt sie schaerfer als
 * hochskaliert. Vanilla rechnet die Matrix selbst in Ausschnitte ein
 * ({@code enableScissor -> transformAxisAligned(pose)}).</p>
 *
 * <p>Drei Groessen: {@link #ganzzahl} ist Minecrafts Faktor, {@link #effektiv()} sind echte
 * Pixel je BBS-Einheit (davon haengt jede Umrechnung in Pixel ab — Puffer, Picking, Vektor-
 * Raster), {@link #rest()} ist der Quotient der beiden.</p>
 */
public final class UISkala
{
    private UISkala()
    {}

    /** Minecrafts ganzzahliger Faktor zum gewuenschten Wert: aufgerundet, in [1, hoechster]. */
    public static int ganzzahl(float gewuenscht, int hoechster)
    {
        /* Der kleine Abzug haelt 2,0 bei 2 — float-Werte aus dem Regler liegen gern knapp darueber. */
        return Math.min(Math.max((int) Math.ceil(gewuenscht - 1E-3F), 1), hoechster);
    }

    /** Echte Pixel je BBS-Einheit. Ohne erzwungenen Wert (Menue zu, ui_scale 0) Minecrafts eigener. */
    public static float effektiv()
    {
        Window fenster = Minecraft.getInstance().getWindow();
        float minecraft = (float) fenster.getGuiScale();
        float eigene = BBSModClient.getCustomGUIScale();

        if (eigene <= 0F)
        {
            return minecraft;
        }

        /* Dieselbe Obergrenze wie Vanilla: mindestens 320x240 Einheiten Oberflaeche. */
        float hoechster = Math.max(1F, Math.min(fenster.getWidth() / 320F, fenster.getHeight() / 240F));

        return Math.min(eigene, Math.max(hoechster, minecraft));
    }

    /** Faktor, um den BBS seine Oberflaeche ueber Minecrafts ganzzahligen hinaus skaliert. */
    public static float rest()
    {
        float minecraft = (float) Minecraft.getInstance().getWindow().getGuiScale();

        return minecraft <= 0F ? 1F : effektiv() / minecraft;
    }

    /** Minecrafts GUI-Koordinate in BBS-Einheiten. */
    public static int nachBbs(double gui)
    {
        return (int) Math.floor(gui / rest());
    }

    /** Minecrafts GUI-Groesse in BBS-Einheiten (aufgerundet, damit nichts abgeschnitten wird). */
    public static int groesse(int gui)
    {
        return (int) Math.ceil(gui / rest() - 1E-3F);
    }
}
