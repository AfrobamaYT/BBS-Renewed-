package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * LightForm — ein licht als actor-form.
 *
 * <h2>WARUM DAS EINE FORM IST (und kein eigenes objekt)</h2>
 * <p>Ein licht braucht position, rotation, eine zeitachse und undo. Genau das liefert ein
 * {@code Replay}: die x/y/z- und rotations-kanaele bewegen den actor, und die form haengt daran.
 * Ein eigenes objekt haette all das nachbauen muessen. Deshalb ist ein licht hier eine form —
 * die position steht NICHT in dieser klasse, sie kommt vom replay/actor.</p>
 *
 * <h2>⛔ DIE FALLE, DIE HIER SCHON EINMAL ZUGESCHLAGEN HAT (registry)</h2>
 * <p>Diese klasse MUSS in {@code BBSMod}s form-registry stehen
 * ({@code forms.register(Link.bbs("light"), LightForm.class, null)}). Ohne den eintrag wirft
 * {@code MapFactory.getType} ein {@code IllegalStateException("... is not part of this factory!")},
 * und zwar in JEDEM pfad der eine form serialisiert oder kopiert:
 * {@code Film.toData()} (speichern, backup), {@code FormUtils.copy} (actor-entities erzeugen),
 * und jeder import, der LightForm-replays in den offenen film schreibt.
 * Das war ein sicherer crash, sobald lichter importiert wurden.
 * Zweite haelfte derselben registrierung: ein renderer in {@code FormUtilsClient} — BBS ruft an
 * mehreren stellen {@code FormUtilsClient.getRenderer(form).collectMatrices(...)} OHNE null-check
 * (z.b. die gizmo-achsen eines ausgewaehlten actors), eine form ohne renderer ist dort eine NPE.</p>
 *
 * <h2>ZWEI BELEUCHTUNGS-EBENEN, BEWUSST NEBENEINANDER</h2>
 * <ul>
 *   <li>{@link #level} — vanilla block-licht 0..15. Grob, farblos, richtungslos, aber es wirkt in
 *       der echten welt-beleuchtung (dynamic-light-integration) und im vanilla-render.</li>
 *   <li>{@link #type}/{@link #color}/{@link #intensity}/{@link #radius}/{@link #coneAngle}/
 *       {@link #coneSoftness} — das physikalische lichtmodell fuer einen echten renderer-pass.
 *       Diese werte tut HEUTE noch kein renderer etwas an (siehe unten); sie sind die datenbasis,
 *       gegen die der spotlight-pass gebaut wird, und sie ueberleben speichern/laden/undo.</li>
 * </ul>
 *
 * <h2>STAND — was diese werte HEUTE bewirken (Regel 0: nichts beschoenigen)</h2>
 * <p>{@link #enabled} und {@link #level} lassen sich exportieren und importieren.
 * Die uebrigen felder werden gespeichert,
 * geladen, sind ueber die form-property-kanaele animierbar und lassen sich von
 * {@code com.mcstudio.light.LightSnapshot} in die GPU-form uebersetzen — aber es gibt noch
 * KEINEN render-pass, der sie im bild sichtbar macht. Das ist der naechste schritt
 * ("echte spotlights"), nicht dieser.</p>
 *
 * <h2>EINHEITEN (verwechslung = falsches licht)</h2>
 * <ul>
 *   <li>{@link #coneAngle} ist der HALBE oeffnungswinkel in GRAD (Blender spot_size ist der VOLLE
 *       winkel in radian — der importer muss halbieren und umrechnen).</li>
 *   <li>{@link #radius} ist die reichweite in BLOECKEN.</li>
 *   <li>{@link #coneSoftness} 0..1 ist der anteil des kegels, der weichzeichnet: 0 = harte kante,
 *       1 = der kegel blendet vom zentrum bis zum rand aus. Entspricht Blenders spot_blend.</li>
 * </ul>
 */
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
