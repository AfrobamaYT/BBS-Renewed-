package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.Timer;

/**
 * Writes the live camera into the camera clip that is currently being edited.
 *
 * <p>Pulled out of {@code UIFilmPanel.updateLogic} — the panel only calls
 * {@link #update()} once per frame now. Both ways of writing live here because
 * they share the same throttle and the same "what was the camera last time"
 * memory; splitting them would mean two copies of that state that can drift.</p>
 *
 * <p>There are two ways in, and they are mutually exclusive by construction:</p>
 *
 * <ul>
 * <li><b>Flight</b> — {@link UIFilmPanel#isFlying()}. The playhead stands still
 * while flying, because {@code RunnerCameraController.update()} only advances
 * ticks when no manual position is set, and flight sets one. So the camera is
 * written over and over at one frozen cursor tick.</li>
 * <li><b>Take</b> — playback is running (so flight is off: {@code setFlight}
 * refuses to arm while running). The playhead moves, the user steers the camera
 * by hand, and every tick the playhead passes is written. That is a recorded
 * camera take rather than a single pose.</li>
 * </ul>
 */
public class FlightClipRecorder
{
    /** Nothing has been written yet at any tick. Ticks are never negative. */
    private static final int NO_TICK = -1;

    private final UIFilmPanel panel;

    /**
     * The panel's flight position, which doubles as {@code runner.manual} — the
     * orbit camera is poured into it while flying, and the runner then applies it
     * to the world camera. Handed in rather than fetched, so this class cannot
     * accidentally swap it for a copy and write the flight into a dead object.
     */
    private final Position flightPosition;

    private final Position lastPosition = new Position(0, 0, 0, 0, 0);
    private final Timer editTime = new Timer(100);

    /**
     * Cursor tick the take last wrote at, so a tick is written once even though
     * several frames fall into it. {@link #NO_TICK} while no take is running.
     */
    private int lastTakeTick = NO_TICK;

    public FlightClipRecorder(UIFilmPanel panel, Position flightPosition)
    {
        this.panel = panel;
        this.flightPosition = flightPosition;
    }

    /** Arm the throttle, so the first flight edit is not swallowed. */
    public void mark()
    {
        this.editTime.mark();
    }

    /**
     * Forget the last camera, so the next edit counts as a change even if the
     * camera happens to sit exactly where it did before.
     */
    public void forgetLastPosition()
    {
        this.lastPosition.set(Position.ZERO);
    }

    /** The playhead was moved by hand: re-arm and forget, and drop the take. */
    public void rewind()
    {
        this.editTime.mark();
        this.lastPosition.set(Position.ZERO);
        this.lastTakeTick = NO_TICK;
    }

    public void update()
    {
        if (this.panel.isFlying())
        {
            this.lastTakeTick = NO_TICK;

            this.updateFlight();
        }
        else
        {
            /* Keep the flight camera parked where the view actually is, so
             * entering flight does not jump. Reads FROM the camera INTO the
             * orbit, not the other way round. */
            this.panel.dashboard.orbit.setup(this.panel.getCamera());

            this.updateTake();
        }
    }

    private void updateFlight()
    {
        this.panel.dashboard.orbit.apply(this.flightPosition);

        Position current = new Position(this.panel.getCamera());
        boolean check = this.editTime.check();

        /* ⛔ NUR im Camera-Mode schreibt der Flug in den Clip (Nutzer-Meldung 2026-08-12,
         * Akte 06 Symptom 1): "das soll nur dann passieren wenn der camera mode an ist
         * nicht bei free flight first person oder so". Die alte Grenze != FREE schrieb den
         * Flug auch aus Orbit, First- und Third-Person in den fuehrenden Kamera-Clip. */
        if (this.writesCamera() && this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_CAMERA)
        {
            if (!this.lastPosition.equals(current) && check)
            {
                this.panel.cameraEditor.editClip(current);
            }
        }

        if (check)
        {
            this.lastPosition.set(current);
        }
    }

    /**
     * Record the camera into the clip while the film plays.
     *
     * <p>Off by default: with it on, the clip under the playhead is rewritten as
     * the playhead passes over it — that is the point, but it is destructive, so
     * it is not something to walk into.</p>
     */
    private void updateTake()
    {
        if (!BBSSettings.editorRecordCameraDuringPlayback.get() || !this.panel.isRunning())
        {
            this.lastTakeTick = NO_TICK;

            return;
        }

        /* CAMERA (0) and FREE (1) do not drive the camera: in CAMERA the clip
         * itself is what you see, so recording would feed the clip its own
         * output and shred a smooth curve into per-tick keyframes for nothing;
         * in FREE the editor does not own the camera at all. Everything above
         * FREE — orbit, first and third person — puts a camera on screen that
         * the user steers, and that is what is worth recording.
         * Same boundary as UIFilmController.orbit.enabled (getPovMode() > 1). */
        if (this.panel.getController().getPovMode() <= UIFilmController.CAMERA_MODE_FREE)
        {
            return;
        }

        int tick = this.panel.getCursor();

        if (tick == this.lastTakeTick || !this.writesCamera())
        {
            return;
        }

        this.lastTakeTick = tick;

        /* One frame behind: the panel's camera is refreshed by
         * UIFilmPreview.render(), which runs inside the super.render() call
         * AFTER updateLogic. At 20 ticks against 60+ frames that is well under
         * one tick, so the take lands on the right keyframe.
         *
         * FALLE: the FOV in here is the editor FOV, not the clip's — outside
         * flight UIFilmController.handleCamera stamps BBSSettings.getFov() onto
         * the camera every frame. A take therefore flattens an animated FOV to
         * that one value. That is consistent (the take records what was on
         * screen), but it is the one channel the user cannot steer while
         * recording, so it is called out in the setting's description. */
        Position current = new Position(this.panel.getCamera());

        this.panel.cameraEditor.editClip(current);
        this.lastPosition.set(current);
    }

    /**
     * Whether there is a clip that would take the camera right now. Asking this
     * can SELECT a clip ({@code pickActiveClip}), which is why it is asked last
     * — after the cheap tick and mode checks have already said no.
     */
    private boolean writesCamera()
    {
        return this.panel.cameraEditor.acceptsFlightEdit();
    }
}
