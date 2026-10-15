// Coded by Afrobama/Claude — F7 "Record replay from cursor" hotkey
package com.afrobama.bbstools.client.film;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hotkey loop that mirrors vanilla BBS {@code keyRecordReplay} (Right-
 * Alt → "record current replay from tick 0") but uses the film
 * editor's current cursor tick as the start tick instead.
 *
 * <p>Press once → recording starts at {@code UIFilmPanel.getCursor()}
 * of the most recently opened film. Press again → stops and applies
 * the recorded keyframes onto the current replay (identical to the
 * vanilla stop path). Works both inside and outside the dashboard:
 * if the dashboard is open we still use its cursor; if it's closed
 * we use the cursor from the last time it was open (BBS keeps the
 * panel instance alive between opens).</p>
 *
 * <p>Edge-detection via {@link InputConstants#isKeyDown} per tick so
 * the press fires once per off→on transition — matching vanilla
 * {@code wasPressed()} semantics. Falls back gracefully if no film
 * is loaded.</p>
 */
public final class RecordFromCursorHandler
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/record-from-cursor");

    private static boolean wasPressed;

    private RecordFromCursorHandler() {}

    public static void register()
    {
        BBSToolsKeys.touch();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Record-from-cursor hotkey registered (default F7 — rebindable in BBS keybind editor).");
    }

    private static void tick()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            boolean nowPressed = comboCurrentlyHeld(mc.getWindow().handle(), BBSToolsKeys.RECORD_FROM_CURSOR);

            if (nowPressed && !wasPressed)
            {
                fire();
            }

            wasPressed = nowPressed;
        }
        catch (Throwable t)
        {
            LOG.debug("record-from-cursor tick failed: {}", t.toString());
        }
    }

    private static boolean comboCurrentlyHeld(long handle, KeyCombo combo)
    {
        if (combo == null || combo.keys == null || combo.keys.isEmpty()) return false;
        for (Integer k : combo.keys)
        {
            if (k == null) return false;
            if (!InputConstants.isKeyDown(k)) return false;
        }
        return true;
    }

    private static void fire()
    {
        /* Global polling must not record while a screen or text field is open. */
        if (mc().gui.screen() == null)
        {
            fireFromViewport();
        }
    }

    /** Called by the viewport's focus-aware keybind manager, or the global world key. */
    public static void fireFromViewport()
    {
        try
        {
            /* Closing the viewport must not let the still-held key toggle again
             * from the global poll on the next client tick. Release re-arms it. */
            wasPressed = true;

            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
            if (dashboard == null)
            {
                LOG.info("F7 pressed but dashboard is null — no film loaded yet. Open the editor at least once first.");
                return;
            }

            UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
            if (panel == null || panel.getData() == null)
            {
                LOG.info("F7 pressed but no film panel / film loaded. Open a film in the editor at least once first.");
                return;
            }

            Films films = BBSModClient.getFilms();
            Recorder recorder = films.getRecorder();

            if (recorder != null)
            {
                /* Toggle: stop + apply, identical to vanilla
                 *  BBSModClient.keyRecordReplay() stop path. */
                Recorder stopped = films.stopRecording();
                if (stopped == null || stopped.hasNotStarted())
                {
                    LOG.info("Recording stopped before it produced any keyframes — nothing to apply.");
                    return;
                }
                panel.applyRecordedKeyframes(stopped, panel.getData());
                LOG.info("Stopped F7 recording at tick {} and applied keyframes.", stopped.getTick());
                return;
            }

            Replay replay = panel.replayEditor.getReplay();
            if (replay == null)
            {
                LOG.info("F7 pressed but no current replay selected. Select a replay first.");
                return;
            }

            int index = panel.getData().replays.getList().indexOf(replay);
            if (index < 0)
            {
                LOG.info("F7 pressed but current replay is not in the film's replay list.");
                return;
            }

            int cursor = panel.getCursor();

            /* Close the editor screen if it's currently open — vanilla
             *  "outside" recording mode runs while the screen is null
             *  so the user can move their character. Mirrors what
             *  UIFilmController.startRecording(groups) does for the
             *  "outside" group. */
            if (mc().gui.screen() != null)
            {
                mc().gui.setScreen(null);
            }

            films.startRecording(panel.getData(), index, cursor);
            LOG.info("Started F7 recording on replay #{} ('{}') from cursor tick {}.",
                index, safeName(replay), cursor);
        }
        catch (Throwable t)
        {
            LOG.warn("F7 record-from-cursor failed: {}", t.toString(), t);
        }
    }

    private static Minecraft mc()
    {
        return Minecraft.getInstance();
    }

    private static String safeName(Replay r)
    {
        try { return r.getName(); } catch (Throwable t) { return "?"; }
    }

}
