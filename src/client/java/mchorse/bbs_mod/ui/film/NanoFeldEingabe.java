package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoFlaeche;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Typing a number into a properties field (UIPropertiesNano), as in Blender: a click opens the
 * field, digits replace its value, Enter writes it as a keyframe at the playhead, Escape or a
 * click elsewhere drops it; a drag still scrubs. The fields could only be scrubbed (0.01 per
 * pixel): a ball's height of exactly 4 meant pixel-hunting (speed test T1, 2026-10-16).
 *
 * <p>One field at a time across the editor, so the state is static here and the panel keeps no
 * new fields. The surface's focus decides whether the input still runs: Escape and clicks clear
 * it without telling anyone, and the field then shows its value again.</p>
 */
public final class NanoFeldEingabe
{
    private static String zone = "";
    private static final StringBuilder TEXT = new StringBuilder();

    private NanoFeldEingabe()
    {}

    /** From the field's click zone: start typing into it, empty (typing replaces the value) */
    public static void starten(NanoFlaeche flaeche, String zoneId)
    {
        zone = zoneId;
        TEXT.setLength(0);
        flaeche.setzeFokus(zoneId);
    }

    public static boolean aktiv(NanoFlaeche flaeche, String zoneId)
    {
        return !zone.isEmpty() && zone.equals(zoneId) && zoneId.equals(flaeche.fokus());
    }

    /** What the field shows: the typed text with a caret while typing, else its value */
    public static String anzeige(NanoFlaeche flaeche, String zoneId, String wert)
    {
        return aktiv(flaeche, zoneId) ? TEXT + "|" : wert;
    }

    /** A drag on the field scrubs instead */
    public static void abbrechen(NanoFlaeche flaeche)
    {
        if (!zone.isEmpty() && zone.equals(flaeche.fokus()))
        {
            flaeche.setzeFokus("");
        }

        zone = "";
        TEXT.setLength(0);
    }

    /** A typed character; on Enter the number to write, else null */
    public static Double tippen(String fokusId, char c)
    {
        if (zone.isEmpty() || !zone.equals(fokusId))
        {
            return null;
        }

        if (c == '\b')
        {
            TEXT.setLength(Math.max(0, TEXT.length() - 1));
        }
        else if (c == '\n')
        {
            String s = TEXT.toString().replace(',', '.');

            zone = "";
            TEXT.setLength(0);

            try
            {
                return s.isBlank() ? null : Double.parseDouble(s);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
        else if (Character.isDigit(c) || c == '.' || c == ',' || c == '-')
        {
            TEXT.append(c);
        }

        return null;
    }

    /**
     * Write a typed value as a keyframe at {@code tick}: position and angles into the replay's own
     * channels, rotation (degrees) and scale into the form's transform channel, as scrubbing does.
     * A uniform scale stays uniform.
     */
    public static void schreiben(Replay replay, int tick, String kanal, double wert)
    {
        KeyframeChannel<Double> eigen = switch (kanal)
        {
            case "x" -> replay.keyframes.x;
            case "y" -> replay.keyframes.y;
            case "z" -> replay.keyframes.z;
            case "yaw" -> replay.keyframes.yaw;
            case "pitch" -> replay.keyframes.pitch;
            default -> null;
        };

        if (eigen != null)
        {
            eigen.insert(tick, wert);

            return;
        }

        Form form = replay.form.get();
        KeyframeChannel<Transform> ziel = form == null ? null : replay.properties.getOrCreate(form, "transform");

        if (ziel == null)
        {
            return;
        }

        Transform basis = ziel.interpolate(tick, form.transform.get());
        Transform tf = basis == null ? new Transform() : basis.copy();
        float bogen = (float) Math.toRadians(wert);

        switch (kanal)
        {
            case "rx" -> tf.rotate.x = bogen;
            case "ry" -> tf.rotate.y = bogen;
            case "rz" -> tf.rotate.z = bogen;
            case "sx" ->
            {
                boolean gleich = Math.abs(tf.scale.x - tf.scale.y) < 0.001F && Math.abs(tf.scale.y - tf.scale.z) < 0.001F;
                float s = Math.max(0.01F, (float) wert);

                tf.scale.set(s, gleich ? s : tf.scale.y, gleich ? s : tf.scale.z);
            }
            default -> { return; }
        }

        ziel.insert(tick, tf);
    }
}
