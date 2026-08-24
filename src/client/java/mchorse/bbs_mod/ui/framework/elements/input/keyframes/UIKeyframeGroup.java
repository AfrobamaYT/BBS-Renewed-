package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.ArrayList;
import java.util.List;

public class UIKeyframeGroup extends UIKeyframeElement
{
    public List<UIKeyframeElement> children = new ArrayList<>();
    public boolean collapsed = true;

    /**
     * Läuft nach jedem Auf- oder Zuklappen. Die Spurliste wird bei jedem Replay-Wechsel neu
     * gebaut; ohne diesen Rückkanal wüsste der Erbauer nicht, was der Nutzer zuletzt aufgeklappt
     * hatte, und die Gruppe klappte bei jedem Neuaufbau wieder zu.
     */
    public Runnable onToggle;

    /** Kopfzeile der Struktur (Replay oder Kamera) — dunkelblaue Toenung statt Gruppen-Gruen. */
    public boolean kopf;
    /** Summary-Zeile (Entwurf): aggregiert ALLE Kanaele des Graphen, ohne eigene Kinder. */
    public boolean summary;

    /** Zaehlertext fuer Koepfe ohne geladene Kinder (z. B. „4 channels“ eines fremden Replays). */
    public String zaehler = "";

    /** >= 0: diese Kopfzeile ist die Kamera-Ebene N — die Lane zeichnet dort die Clips. */
    public int kameraEbene = -1;

    /** Sichtbarkeits-Auge des Kopfes (live gelesen, z. B. replay.enabled) — null: keins. */
    public java.util.function.BooleanSupplier auge;

    public UIKeyframeGroup(IKey title)
    {
        super(title, 0);
    }

    public UIKeyframeGroup(IKey title, int color)
    {
        super(title, color);
    }

    public void add(UIKeyframeElement element)
    {
        this.children.add(element);
    }
}
