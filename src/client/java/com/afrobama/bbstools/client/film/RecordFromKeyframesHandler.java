// Coded by Afrobama/Claude — F6 "Record replay from current keyframes" hotkey
package com.afrobama.bbstools.client.film;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hotkey loop that mirrors the in-editor "Record replay (all
 * keyframes)" mode, but works <i>outside</i> the editor.
 *
 * <p>On press it:</p>
 * <ol>
 *   <li>Reads the current replay's position channels
 *       ({@code x}, {@code y}, {@code z}, {@code yaw}, {@code pitch},
 *       {@code headYaw}, {@code bodyYaw}) and interpolates them at
 *       the film cursor's current tick;</li>
 *   <li>Teleports the real client player to that pose so the user
 *       physically starts where the existing keyframes leave them
 *       off at the cursor;</li>
 *   <li>Closes the editor screen if it's open and starts a Films
 *       recording from the cursor tick — same as F7
 *       (RECORD_FROM_CURSOR), so any movement from now on is
 *       captured into the keyframe channels and applied on stop.</li>
 * </ol>
 *
 * <p>Toggle: pressing F6 again stops + applies keyframes (identical
 * stop path to F7 / vanilla {@code keyRecordReplay}). The teleport
 * step is skipped on stop.</p>
 *
 * <p>If a position channel has no keyframes (fresh replay or empty
 * channel), the teleport is skipped for that axis — recording still
 * starts from the cursor at the player's current world position,
 * so the hotkey degrades gracefully to F7 behaviour.</p>
 */
public final class RecordFromKeyframesHandler
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/record-from-keyframes");

    private static boolean wasPressed;

    private RecordFromKeyframesHandler() {}

    public static void register()
    {
        BBSToolsKeys.touch();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LOG.info("Record-from-keyframes hotkey registered (default F6 — rebindable in BBS keybind editor).");
    }

    private static void tick()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            boolean nowPressed = comboCurrentlyHeld(mc.getWindow().handle(), BBSToolsKeys.RECORD_FROM_KEYFRAMES);

            if (nowPressed && !wasPressed)
            {
                fire();
            }

            wasPressed = nowPressed;
        }
        catch (Throwable t)
        {
            LOG.debug("record-from-keyframes tick failed: {}", t.toString());
        }
    }

    private static boolean comboCurrentlyHeld(long handle, KeyCombo combo)
    {
        if (combo == null || combo.keys == null || combo.keys.isEmpty()) return false;
        for (Integer k : combo.keys)
        {
            if (k == null) return false;
            if (!InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), k)) return false;
        }
        return true;
    }

    /** Consume a conflicting native video-key pulse as the replay key, once until release. */
    public static boolean consumeWorldKey(KeyMapping mapping)
    {
        Minecraft mc = Minecraft.getInstance();
        KeyCombo combo = BBSToolsKeys.RECORD_FROM_KEYFRAMES;

        if (mc.gui.screen() != null || combo.keys.isEmpty()
            || !mapping.matches(new KeyEvent(combo.getMainKey(), 0, 0)))
        {
            return false;
        }

        for (int i = 1; i < combo.keys.size(); i++)
        {
            if (!InputConstants.isKeyDown(mc.getWindow(), combo.keys.get(i)))
            {
                return false;
            }
        }

        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null || (BBSModClient.getFilms().getRecorder() == null
            && !panel.getData().replays.getList().contains(panel.replayEditor.getReplay())))
        {
            return false;
        }

        if (!wasPressed)
        {
            fireFromViewport();
        }

        return true;
    }

    private static void fire()
    {
        /* Global polling must not record while a screen or text field is open. */
        if (Minecraft.getInstance().gui.screen() == null)
        {
            fireFromViewport();
        }
    }

    /** Called by the viewport's focus-aware keybind manager, or the global world key. */
    public static void fireFromViewport()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();

            /* Closing the viewport must not let the still-held key toggle again
             * from the global poll on the next client tick. Release re-arms it. */
            wasPressed = true;

            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
            if (dashboard == null)
            {
                LOG.info("F6 pressed but dashboard is null — open the editor at least once first.");
                return;
            }

            UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
            if (panel == null || panel.getData() == null)
            {
                LOG.info("F6 pressed but no film panel / film loaded.");
                return;
            }

            Films films = BBSModClient.getFilms();
            Recorder recorder = films.getRecorder();

            if (recorder != null)
            {
                /* Toggle stop — identical to F7 / vanilla. No teleport
                 *  on stop, otherwise the player would jump back to
                 *  the start tick when the user just wants to stop. */
                Recorder stopped = films.stopRecording();
                if (stopped == null || stopped.hasNotStarted())
                {
                    LOG.info("Recording stopped before any keyframes produced — nothing to apply.");
                    return;
                }
                panel.applyRecordedKeyframes(stopped, panel.getData());
                LOG.info("Stopped F6 recording at tick {} and applied keyframes.", stopped.getTick());
                return;
            }

            Replay replay = panel.replayEditor.getReplay();
            if (replay == null)
            {
                LOG.info("F6 pressed but no current replay selected.");
                return;
            }

            int index = panel.getData().replays.getList().indexOf(replay);
            if (index < 0)
            {
                LOG.info("F6 pressed but current replay is not in the film's replay list.");
                return;
            }

            int cursor = panel.getCursor();

            /* Teleport the player to the keyframed pose BEFORE we kick
             *  off the recording. The recorder is then armed at the
             *  player's freshly-snapped position, so the first sample
             *  it captures matches the existing keyframe values at
             *  the cursor tick — seamless continuation. */
            LocalPlayer player = mc.player;
            int teleported = teleportPlayerToKeyframes(player, replay.keyframes, cursor);

            /* Close editor screen so we're in-world for the recording —
             *  same behaviour as the in-editor "outside" group does. */
            if (mc.gui.screen() != null)
            {
                mc.gui.setScreen(null);
            }

            films.startRecording(panel.getData(), index, cursor);
            LOG.info("Started F6 recording on replay #{} ('{}') from cursor tick {} (teleported {} channels).",
                index, safeName(replay), cursor, teleported);
        }
        catch (Throwable t)
        {
            LOG.warn("F6 record-from-keyframes failed: {}", t.toString(), t);
        }
    }

    /**
     * Snap the client player to the interpolated keyframe pose at the
     * given tick. Per-axis fallback: if a channel has no keyframes,
     * leave that axis at the player's current value (so a partially-
     * recorded replay still teleports correctly on the axes that
     * have data).
     *
     * @return number of channels that contributed a teleport value
     */
    private static int teleportPlayerToKeyframes(LocalPlayer player, ReplayKeyframes k, int tick)
    {
        if (player == null || k == null) return 0;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float headYaw = player.yHeadRot;
        float bodyYaw = player.yBodyRot;

        int hits = 0;

        Double xv = safeInterpolate(k.x, tick);
        Double yv = safeInterpolate(k.y, tick);
        Double zv = safeInterpolate(k.z, tick);
        Double yawV = safeInterpolate(k.yaw, tick);
        Double pitchV = safeInterpolate(k.pitch, tick);
        Double headYawV = safeInterpolate(k.headYaw, tick);
        Double bodyYawV = safeInterpolate(k.bodyYaw, tick);

        if (xv != null) { x = xv; hits++; }
        if (yv != null) { y = yv; hits++; }
        if (zv != null) { z = zv; hits++; }
        if (yawV != null) { yaw = yawV.floatValue(); hits++; }
        if (pitchV != null) { pitch = pitchV.floatValue(); hits++; }
        if (headYawV != null) { headYaw = headYawV.floatValue(); hits++; }
        if (bodyYawV != null) { bodyYaw = bodyYawV.floatValue(); hits++; }

        if (hits == 0)
        {
            return 0;
        }

        /* refreshPositionAndAngles updates head/body yaw to the
         *  passed yaw — override those afterwards so independent
         *  head/body channels survive. */
        /* ⚠️ Entity.moveTo(x,y,z,yaw,pitch) heisst in 26.2 snapTo(...) — reine Umbenennung
         * (Position und Blickwinkel hart setzen, ohne Interpolation). */
        player.snapTo(x, y, z, yaw, pitch);
        player.yHeadRot = headYaw;
        player.yBodyRot = bodyYaw;
        player.yHeadRotO = headYaw;
        player.yBodyRotO = bodyYaw;
        player.yRotO = yaw;
        player.xRotO = pitch;

        return hits;
    }

    private static Double safeInterpolate(mchorse.bbs_mod.utils.keyframes.KeyframeChannel<Double> ch, int tick)
    {
        try
        {
            if (ch == null || ch.getKeyframes() == null || ch.getKeyframes().isEmpty())
            {
                return null;
            }
            return ch.interpolate((float) tick);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String safeName(Replay r)
    {
        try { return r.getName(); } catch (Throwable t) { return "?"; }
    }

}
