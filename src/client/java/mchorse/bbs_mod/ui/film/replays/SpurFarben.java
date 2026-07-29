package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

/**
 * Welche Farbe und welches Sinnbild eine Keyframe-Spur bekommt.
 *
 * <p>Aus {@code UIReplaysEditor} herausgelöst (2026-07-29), weil es dort nur lag und die Datei
 * über die 1500-Zeilen-Grenze drückte. Es ist eine abgeschlossene Sache: eine Zuordnung von
 * Kanalnamen auf Farbe und Icon, sonst nichts.</p>
 *
 * <p>Die Farbgruppen folgen den Achsen — X rot, Y grün, Z blau —, damit man eine Spur am
 * Farbstreifen erkennt, ohne den Namen zu lesen.</p>
 */
public final class SpurFarben
{
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();

    static
    {
        setupColors();
        setupIcons();
    }

    private SpurFarben()
    {
    }

    /** Farbe der Spur; ohne Eintrag die neutrale Standardfarbe. */
    public static int farbe(String key)
    {
        return COLORS.getOrDefault(key, Colors.WHITE & Colors.RGB);
    }

    /** Ob für diesen Kanal überhaupt eine Farbe hinterlegt ist. */
    public static boolean hatFarbe(String key)
    {
        return COLORS.containsKey(key);
    }

    public static Icon icon(String key)
    {
        return ICONS.get(key);
    }

    private static void setupColors()
    {
        putColors(Colors.RED, "x", "vX", "stick_lx", "stick_rx", "extra1_x", "extra2_x", "user1", "user5", "frequency", "offset_x");
        putColors(Colors.GREEN, "y", "vY", "stick_ly", "stick_ry", "trigger_l", "trigger_r", "extra1_y", "extra2_y", "user3", "count", "offset_y", "transform");
        putColors(Colors.BLUE, "z", "vZ", "user4", "offset_z");
        putColors(Colors.YELLOW, "yaw", "lighting");
        putColors(Colors.CYAN, "pitch");
        putColors(Colors.MAGENTA, "bodyYaw", "actions", "settings");
        putColors(Colors.ORANGE, "pose_overlay", "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet", "user2", "user6");

        COLORS.put("visible", Colors.WHITE & Colors.RGB);
        COLORS.put("pose", Colors.RED);
        COLORS.put("physics_targets", Colors.MAGENTA);
        COLORS.put("transform_overlay", 0xaaff00);
        COLORS.put("color", Colors.INACTIVE);
        COLORS.put("shape_keys", Colors.PINK);
    }

    private static void putColors(int color, String... keys)
    {
        for (String key : keys) COLORS.put(key, color);
    }

    private static void setupIcons()
    {
        ICONS.put("x", Icons.X);
        ICONS.put("y", Icons.Y);
        ICONS.put("z", Icons.Z);
        ICONS.put("pitch", Icons.VERTICAL);
        ICONS.put("headYaw", Icons.HORIZONTAL);
        ICONS.put("visible", Icons.VISIBLE);
        ICONS.put("texture", Icons.MATERIAL);
        ICONS.put("pose", Icons.POSE);
        ICONS.put("transform", Icons.ALL_DIRECTIONS);
        ICONS.put("color", Icons.BUCKET);
        ICONS.put("lighting", Icons.LIGHT);
        ICONS.put("actions", Icons.CONVERT);
        ICONS.put("shape_keys", Icons.HEART_ALT);
        ICONS.put("text", Icons.FONT);
        ICONS.put("stick_lx", Icons.LEFT_STICK);
        ICONS.put("stick_rx", Icons.RIGHT_STICK);
        ICONS.put("trigger_l", Icons.TRIGGER);
        ICONS.put("extra1_x", Icons.CURVES);
        ICONS.put("extra2_x", Icons.CURVES);
        ICONS.put("item_main_hand", Icons.LIMB);
        ICONS.put("user1", Icons.PARTICLE);
        ICONS.put("paused", Icons.TIME);
        ICONS.put("frequency", Icons.STOPWATCH);
        ICONS.put("count", Icons.BUCKET);
        ICONS.put("settings", Icons.GEAR);
        ICONS.put("physics_targets", Icons.TIME);
    }
}
