package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

import java.util.UUID;

/**
 * Eine Merkstelle auf der Zeitleiste des Films.
 *
 * <p>Wofür: der Nutzer arbeitet an einer Stelle und will sich eine andere merken — „hier
 * springt die Kamera zu früh", „hier muss der Sound rein". Ein Marker ist genau das: ein
 * Tick, ein kurzer Text, eine Farbe zum Sortieren nach Art des Vermerks.</p>
 *
 * <p>Der Marker hängt am FILM, nicht an der Einstellungsdatei. Damit wandert er mit dem
 * Film mit, überlebt einen anderen Rechner und landet im Undo-Stapel wie jeder andere Wert
 * — genau wie Replays und Clips.</p>
 */
public class Marker extends ValueGroup
{
    /** Voreinstellung, aus der bestehenden Marker-Vorarbeit in bbstools übernommen. */
    public static final int DEFAULT_COLOR = 0xFF4EC1FF;

    public final ValueInt tick = new ValueInt("tick", 0);
    public final ValueString label = new ValueString("label", "");
    public final ValueInt color = new ValueInt("color", DEFAULT_COLOR).colorAlpha();
    /** Stable bridge identity. Blender timeline markers cannot carry custom properties, so the
     * add-on keeps this UUID in scene-level metadata while the native marker stays untouched. */
    public final ValueString uuid = new ValueString("uuid", "");

    public Marker(String id)
    {
        super(id);

        this.add(this.tick);
        this.add(this.label);
        this.add(this.color);
        this.add(this.uuid);
        this.uuid.set(UUID.randomUUID().toString());
    }
}
