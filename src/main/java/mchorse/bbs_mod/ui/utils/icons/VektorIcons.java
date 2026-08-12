package mchorse.bbs_mod.ui.utils.icons;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.resources.Link;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * P3: Vektor-gerasterte Icon-Atlanten je GUI-Skala (icons_vek_&lt;s&gt;x.png, erzeugt
 * von tools/icons-rastern.py aus icons/svg/). Weil normalisierte UVs skaleninvariant
 * sind, traegt das Ersatz-Icon dieselben Zellzahlen — nur der Textur-Link wechselt.
 * Nicht neugezeichnete Icons bleiben beim alten Atlas (Manifest entscheidet).
 */
public final class VektorIcons
{
    private static Set<String> manifest;
    private static Map<String, Icon[]> ersatz;

    private VektorIcons()
    {}

    /** Skala 1–4, vom Aufrufer aus der Fenster-GUI-Skala bestimmt. */
    public static Icon ersetze(Icon icon, int skala)
    {
        if (icon == null || icon.id == null
            || BBSSettings.vectorIcons == null || !BBSSettings.vectorIcons.get()
            || !laden().contains(icon.id))
        {
            return icon;
        }

        if (ersatz == null)
        {
            ersatz = new HashMap<>();
        }

        int s = Math.max(1, Math.min(4, skala));
        Icon[] je = ersatz.computeIfAbsent(icon.id, (id) -> new Icon[4]);

        if (je[s - 1] == null)
        {
            je[s - 1] = new Icon(Link.assets("textures/icons_vek_" + s + "x.png"),
                icon.id, icon.x, icon.y, icon.w, icon.h, icon.textureW, icon.textureH);
        }

        return je[s - 1];
    }

    /** Manifest lazy und fehlertolerant: ohne Datei bleibt einfach alles beim Alten. */
    private static Set<String> laden()
    {
        if (manifest == null)
        {
            Set<String> namen = new HashSet<>();

            try (BufferedReader leser = new BufferedReader(new InputStreamReader(
                BBSMod.getProvider().getAsset(Link.assets("textures/icons_vek.txt")), StandardCharsets.UTF_8)))
            {
                for (String zeile = leser.readLine(); zeile != null; zeile = leser.readLine())
                {
                    if (!zeile.isBlank())
                    {
                        namen.add(zeile.trim());
                    }
                }
            }
            catch (Exception e)
            {
                System.err.println("[vektoricons] Manifest fehlt/unlesbar — Icons bleiben Bitmap: " + e);
            }

            manifest = namen;
        }

        return manifest;
    }
}
