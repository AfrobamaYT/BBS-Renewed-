// Coded by Afrobama/Claude — die ausgelieferten Linke-Hand-Preset-Layouts
package com.afrobama.bbstools.client.keybinds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die zwei kuratierten Preset-Layouts, die BBSTools ausliefert — reine Vorgaben-Daten,
 * mechanisch aus {@link KeybindOverrides} herausgeloest (die Datei stand ueber der
 * 800-Zeilen-Grenze und darf nur noch schrumpfen). Gerufen wird beides genau einmal,
 * beim Anlegen der Presets in {@code KeybindOverrides.loadFromDisk}.
 */
final class LinkeHandVorgaben
{
    /** Maustasten liegen als NEGATIVE Ids in einer KeyCombo; Mouse4 ist GLFW-Knopf 3. */
    private static final int MOUSE_4 = -3;
    private static final int MOUSE_5 = -4;

    private LinkeHandVorgaben()
    {}

    /**
     * Hardcoded "Left-Hand" preset — the curated optimisation we ship.
     * Codes are GLFW key constants (lowercase letter codes, F-keys,
     * 340/341/342 = LSHIFT/LCTRL/LALT, 32 = SPACE).
     */
    static Map<String, int[]> buildLeftHandLayout()
    {
        Map<String, int[]> m = new LinkedHashMap<>();
        m.put("next", new int[]{69});                          /* E */
        m.put("prev", new int[]{81});                          /* Q */
        m.put("jump_forward", new int[]{84});                  /* T */
        m.put("jump_backward", new int[]{82});                 /* R */
        m.put("next_clip", new int[]{69, 340});                /* Shift+E */
        m.put("prev_clip", new int[]{81, 340});                /* Shift+Q */
        m.put("clip_select_after", new int[]{84, 340});        /* Shift+T */
        m.put("clip_select_before", new int[]{82, 340});       /* Shift+R */
        m.put("clip_duration", new int[]{71});                 /* G */
        m.put("clip_enable", new int[]{66});                   /* B */
        m.put("looping", new int[]{70, 340});                  /* Shift+F */
        m.put("looping_set_max", new int[]{50});               /* 2 */
        m.put("looping_set_min", new int[]{49});               /* 1 */
        m.put("fade_in", new int[]{67, 340});                  /* Shift+C */
        m.put("fade_out", new int[]{86, 340});                 /* Shift+V */
        m.put("redo", new int[]{82, 341});                     /* Ctrl+R */
        m.put("delete", new int[]{88, 342});                   /* Alt+X */
        m.put("confirm", new int[]{32, 341});                  /* Ctrl+Space */
        m.put("keybinds", new int[]{298});                     /* F9 — Tab interferes with focus, F9 is rarely used */
        m.put("keybind_editor", new int[]{298, 340});          /* Shift+F9 */
        return m;
    }

    /**
     * Linke Hand auf der Tastatur, rechte Hand auf der Maus — ohne dass die rechte Hand die
     * Tastatur anfassen muss.
     *
     * <p><b>Warum es dieses zweite Layout gibt.</b> Von den 169 registrierten Keybinds sind
     * 51 mit der linken Hand in WASD-Ruhelage nicht erreichbar (gemessen 2026-07-28), darunter
     * die haeufigsten Handgriffe ueberhaupt: {@code insert_frame} lag auf I, und alle vier
     * Flug-Schwenks lagen auf den Pfeiltasten. Umsortieren allein loest das NICHT — im
     * erreichbaren Kernbereich haben nur zwei Tasten ueberhaupt keine Solo-Belegung. Deshalb
     * arbeitet dieses Layout mit drei Hebeln statt mit Umsortieren:</p>
     *
     * <ol>
     *   <li><b>Die Maus war voellig ungenutzt.</b> Kein einziger der 169 Keybinds lag auf einer
     *       Maustaste. Mouse4/Mouse5 mit Shift und Strg sind sechs Plaetze, die KEINE
     *       Handbewegung kosten — die wertvollste Flaeche in diesem Setup. Nutzbar erst, seit
     *       der Keybind-Editor Maustasten annimmt.</li>
     *   <li><b>Alt + WASD im Flug.</b> Beim Fliegen liegt die Hand ohnehin auf WASD und der
     *       Daumen auf Alt. Die Pfeiltasten dafuer zu verlassen kostet damit gar nichts.</li>
     *   <li><b>Kontext traegt mehr als Tastenzahl.</b> BBS ist modal — dieselbe Taste bedeutet
     *       im Kamera-, Keyframe- und Transform-Editor Verschiedenes. Das ist auch Blenders
     *       Prinzip und der Grund, warum ein kleiner erreichbarer Tastensatz reicht.</li>
     * </ol>
     *
     * <p>Jede Belegung hier wurde vor dem Eintragen live gegen
     * {@code KeybindOverrides.findConflicts} geprueft. Seit 2026-07-29 beruecksichtigt diese
     * Pruefung den Kontext selbst: sie meldet nur noch Ueberschneidungen, die gleichzeitig
     * gelten koennen (gleiche Kategorie, oder eine der beiden ist {@code all}). Frueher verglich
     * sie quer ueber alle Kategorien und markierte auch Belegungen rot, die problemlos
     * nebeneinander laufen.</p>
     */
    static Map<String, int[]> buildLeftHandMouseLayout()
    {
        /* Auf dem bestehenden Left-Hand-Layout aufsetzen: dessen 22 Belegungen sind unveraendert
         * gut, sie decken nur zu wenig ab. */
        Map<String, int[]> m = buildLeftHandLayout();

        /* --- Maus: null Handbewegung, vorher komplett ungenutzt --- */
        m.put("insert_frame", new int[]{MOUSE_4});              /* Mouse4 — haeufigste Aktion beim Animieren, lag auf I */
        m.put("toggle_control", new int[]{MOUSE_5});            /* Mouse5 — lag auf H */
        m.put("toggle_onion_skin", new int[]{MOUSE_4, 340});    /* Shift+Mouse4 — lag auf O */
        m.put("toggle_motion_path", new int[]{MOUSE_5, 340});   /* Shift+Mouse5 — lag auf M */
        m.put("prev_replay", new int[]{MOUSE_4, 341});          /* Ctrl+Mouse4 — lag auf Bild-auf */
        m.put("next_replay", new int[]{MOUSE_5, 341});          /* Ctrl+Mouse5 — lag auf Bild-ab */

        /* --- Flug: lag komplett auf den Pfeiltasten --- */
        m.put("flight_pan_left", new int[]{65, 342});           /* Alt+A */
        m.put("flight_pan_right", new int[]{68, 342});          /* Alt+D */
        m.put("flight_tilt_up", new int[]{87, 342});            /* Alt+W */
        m.put("flight_tilt_down", new int[]{83, 342});          /* Alt+S */

        /* Weicht fuer flight_pan_left auf Alt+A. Die WASD-Symmetrie im Flug ist mehr wert als
         * die kurze Belegung eines einzelnen Kamera-Befehls. */
        m.put("add_at_tick", new int[]{65, 342, 340, 341});     /* Alt+Shift+Strg+A */

        /* --- Keyframe-Arbeit: lag auf [ ] und Pos1 --- */
        m.put("select_prev", new int[]{90, 342});               /* Alt+Z */
        m.put("select_next", new int[]{84, 342});               /* Alt+T */
        m.put("maximize", new int[]{71, 342});                  /* Alt+G */

        /* --- Clip-Ebene: lag auf Alt+Pfeil --- */
        m.put("clip_layer_up", new int[]{86, 342});             /* Alt+V */
        m.put("clip_layer_down", new int[]{70, 342});           /* Alt+F */

        return m;
    }
}
