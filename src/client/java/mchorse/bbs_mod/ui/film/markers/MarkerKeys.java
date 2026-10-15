package mchorse.bbs_mod.ui.film.markers;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * Beschriftungen und Kürzel der Merkstellen — bewusst NICHT in {@code UIKeys}/{@code Keys}.
 *
 * <p>Grund ist der Hotswap: ein neues statisches Endfeld in einer bereits geladenen Klasse
 * existiert nach dem Tausch zwar, bleibt aber {@code null}, weil der statische Initialisierer
 * nicht noch einmal läuft — und ein statisches Endfeld lässt sich auch per Reflexion nicht
 * nachträglich setzen. Am 2026-07-29 hat genau das die Instanz gerissen (UIKeys getauscht,
 * Felder null, nächster Frame tot). Eine NEUE Klasse lädt beim ersten Zugriff frisch, ihr
 * Initialisierer läuft, die Felder stehen. Deshalb wohnen sie hier.</p>
 *
 * <p>Strg+M, weil M allein dreifach vergeben ist: Clip-Dauer, Bewegungspfad, Pixel-Auswahl.</p>
 */
public final class MarkerKeys
{
    public static final IKey TITLE = L10n.lang("bbs.ui.film.markers.title");
    public static final IKey LABEL = L10n.lang("bbs.ui.film.markers.label");
    public static final IKey COLOR = L10n.lang("bbs.ui.film.markers.color");
    public static final IKey TICK = L10n.lang("bbs.ui.film.markers.tick");
    public static final IKey REMOVE = L10n.lang("bbs.ui.film.markers.remove");
    public static final IKey KEYS_ADD = L10n.lang("bbs.ui.film.markers.keys.add");

    public static final KeyCombo MARKER_ADD =
        new KeyCombo("marker_add", KEYS_ADD, InputConstants.KEY_M, InputConstants.KEY_LCONTROL).categoryKey("camera");

    private MarkerKeys()
    {
    }
}
