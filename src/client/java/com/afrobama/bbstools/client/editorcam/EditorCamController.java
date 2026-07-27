// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.editorcam;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;

/**
 * BBS camera controller that drives the player's view camera from a
 * chosen Replay's keyframes. Reads {@code replay.keyframes.x/y/z/yaw/pitch}
 * at the current film scrub-tick on every {@code setup} call.
 *
 * <p>Lets the user "look through" any replay's perspective at the
 * current film position WITHOUT actually flying their player there or
 * starting playback. Solves the workflow problem of editing distant
 * actors (Herobrine animation while camera is on the player) — pick
 * a camera-replay positioned near Herobrine and animate from his POV.</p>
 *
 * <p>The replay's keyframes must be authored normally in BBS (manual
 * placement OR tracker-clip-driven). This controller is a passive
 * reader — it never writes to the replay.</p>
 *
 * <p>Active state lives in {@link EditorCamHandler}; this class is
 * pure mechanism, registered/unregistered by the handler.</p>
 */
public class EditorCamController implements ICameraController
{
    private Replay replay;

    public void setReplay(Replay replay)
    {
        this.replay = replay;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    /** Priority 5 — sits BETWEEN the default Player view (0) and the
     *  film's PlayCameraController (10) so:
     *
     *  - Normal play with F8 active → editor cam beats Player view ✓
     *  - User presses Play to render the film → PlayCameraController
     *    (10) wins, F8 view is parked, the actual film camera shows ✓
     *  - OrbitCameraController (100) and immersive controllers always
     *    take precedence (panel/morph editor previews) ✓
     *
     *  Verified against BBS-CML 2.0:
     *    PlayCameraController extends CameraWorkCameraController.getPriority = 10
     *    OrbitCameraController.getPriority = 100
     *    PlayerCameraController = default 0 (interface default)
     */
    @Override
    public int getPriority()
    {
        return 5;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (replay == null || camera == null) return;

        int tick = currentFilmTick();
        try
        {
            double x = replay.keyframes.x.interpolate(tick);
            double y = replay.keyframes.y.interpolate(tick);
            double z = replay.keyframes.z.interpolate(tick);
            double yaw = replay.keyframes.yaw.interpolate(tick);
            double pitch = replay.keyframes.pitch.interpolate(tick);

            camera.position.set(x, y, z);
            camera.rotation.set((float) pitch, (float) yaw, 0F);
        }
        catch (Throwable t)
        {
            /* Replay missing keyframes etc. Fall through silently. */
        }
    }

    /** Read the most recent film controller's tick. Falls back to 0 if
     *  no film is active. */
    private static int currentFilmTick()
    {
        try
        {
            java.util.List<BaseFilmController> ctls = BBSModClient.getFilms().getControllers();
            if (ctls == null || ctls.isEmpty()) return 0;
            return ctls.get(ctls.size() - 1).getTick();
        }
        catch (Throwable t) { return 0; }
    }

}
