package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;


public class LightForm extends Form
{
    /** {@link #type}: rundum-licht, {@link #coneAngle}/{@link #coneSoftness} werden ignoriert. */
    public static final int TYPE_POINT = 0;

    /** {@link #type}: kegel-licht entlang der blickrichtung des actors. */
    public static final int TYPE_SPOT = 1;

    /** Schaltet die emission ab, ohne den actor zu entfernen (animierbar). */
    public final ValueBoolean enabled = new ValueBoolean("enabled", true);

    /** Vanilla block-lichtstufe 0..15. Geclampt, weil ein wert ausserhalb im lightmap-index kippt. */
    public final ValueInt level = new ValueInt("level", 15, 0, 15);

    /** {@link #TYPE_POINT} oder {@link #TYPE_SPOT}. Als int, damit der wert keyframebar bleibt. */
    public final ValueInt type = new ValueInt("type", TYPE_POINT, TYPE_POINT, TYPE_SPOT);

    /** Lichtfarbe (linear, alpha ungenutzt). Kein vanilla-aequivalent — nur fuer den echten pass. */
    public final ValueColor color = new ValueColor("color", Color.white());

    /** Helligkeits-multiplikator. Obergrenze grosszuegig, damit HDR-ueberstrahlung moeglich bleibt. */
    public final ValueFloat intensity = new ValueFloat("intensity", 1F, 0F, 1000F);

    /** Reichweite in bloecken, ab der das licht 0 ist. */
    public final ValueFloat radius = new ValueFloat("radius", 8F, 0F, 256F);

    /** HALBER kegel-oeffnungswinkel in grad. 90 = halbraum; darueber waere es kein kegel mehr. */
    public final ValueFloat coneAngle = new ValueFloat("cone_angle", 30F, 0F, 90F);

    /** Weiche kegelkante 0..1 (Blenders spot_blend). 0 = harte kante. */
    public final ValueFloat coneSoftness = new ValueFloat("cone_softness", 0.15F, 0F, 1F);

    /** Abfall-exponent ueber die distanz. 2 = physikalisch (inverse square), 1 = linear/weicher. */
    public final ValueFloat falloff = new ValueFloat("falloff", 2F, 0F, 8F);

    public LightForm()
    {
        super();

        this.add(this.enabled);
        this.add(this.level);
        this.add(this.type);
        this.add(this.color);
        this.add(this.intensity);
        this.add(this.radius);
        this.add(this.coneAngle);
        this.add(this.coneSoftness);
        this.add(this.falloff);
    }

    /** true wenn die kegel-felder ueberhaupt eine bedeutung haben (sonst ist es ein rundum-licht). */
    public boolean isSpot()
    {
        return this.type.get() == TYPE_SPOT;
    }
}
