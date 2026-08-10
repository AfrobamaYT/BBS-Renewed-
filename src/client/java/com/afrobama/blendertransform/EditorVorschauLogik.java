package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.keybinds.KeybindOverrides;
import com.afrobama.bbstools.perf.BBSPerfConfig;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Logik hinter drei Mixins der EDITOR-OBERFLAECHE — {@code UIPickableFormRendererLodBypassMixin}
 * (LOD in der Modellvorschau), {@code SimpleOptionMixin} (Helligkeit aus dem Film) und
 * {@code KeyComboMixin} (Tastenkuerzel spiegeln).
 *
 * <p>Alle drei sind Weichen im Editor, keine im Weltdurchgang. Sie stehen zusammen, weil sie
 * dieselbe Frage beantworten: gilt hier der Wert des Spiels oder der des Editors?</p>
 */
public final class EditorVorschauLogik
{
    private EditorVorschauLogik() {}

    /**
     * {@code UIPickableFormRenderer.renderUserModel} @HEAD — LOD fuer die Vorschau abschalten.
     *
     * <p>In der Modellvorschau steht das Modell direkt vor der Kamera; eine Distanzstufe wuerde
     * dort Koerperteile ausblenden, die der Nutzer gerade bearbeitet.</p>
     *
     * @return der VORHERIGE LOD-Stand, den der Handler sich merken und spaeter zurueckgeben
     *         muss — er gehoert in ein Feld je Renderer-Instanz und kann deshalb nicht hier
     *         liegen
     */
    public static Boolean lodAus()
    {
        boolean vorher = BBSPerfConfig.ENABLE_LOD;

        BBSPerfConfig.ENABLE_LOD = false;

        return Boolean.valueOf(vorher);
    }

    /** {@code renderUserModel} @RETURN — den gemerkten Stand wiederherstellen. */
    public static void lodZurueck(boolean vorher)
    {
        BBSPerfConfig.ENABLE_LOD = vorher;
    }

    /**
     * {@code OptionInstance.get} — die Helligkeit aus dem Film statt aus den Spieloptionen.
     *
     * <p>⚠️ NUR DIE GAMMA-OPTION. {@code OptionInstance.get} laeuft fuer JEDE Einstellung; ohne
     * den Vergleich mit {@code options.gamma()} bekaeme auch die Sichtweite oder die Lautstaerke
     * den Helligkeitswert des Films.</p>
     *
     * @param option die aufgerufene Option; kommt als Argument, weil der Cast den gewobenen
     *               {@code this} braucht
     * @return der uebersteuerte Wert, oder {@code null} fuer den des Spiels
     */
    public static Double helligkeit(Object option)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.options == null || !(option instanceof OptionInstance<?>) || option != mc.options.gamma())
        {
            return null;
        }

        return BBSRendering.getBrightness();
    }

    /**
     * {@code KeyCombo.<init>} — jedes neu gebaute Tastenkuerzel bei den Overrides anmelden.
     *
     * <p>Laeuft im Konstruktor, also einmal je Kuerzel beim Aufbau der Oberflaeche.</p>
     */
    public static void kuerzelSpiegeln(Object kombination)
    {
        if (kombination instanceof KeyCombo kombi)
        {
            KeybindOverrides.register(kombi);
        }
    }
}
