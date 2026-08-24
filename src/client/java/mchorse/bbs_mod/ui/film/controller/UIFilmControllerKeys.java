package mchorse.bbs_mod.ui.film.controller;

import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;

import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIQuickReplayMenu;
import mchorse.bbs_mod.ui.film.replays.overlays.UIQuickReplayOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * Every keybind the film controller listens to, in one place.
 *
 * <p>Sat in {@link UIFilmController}'s constructor before — eighty lines of
 * registrations in a file that had grown past 1700, so a look at "which key does
 * what here" meant reading past the whole render and picking machinery. Nothing
 * about the behaviour changed with the move.</p>
 */
public class UIFilmControllerKeys
{
    public static void register(UIFilmController c)
    {
        IKey category = UIKeys.FILM_CONTROLLER_KEYS_CATEGORY;

        Supplier<Boolean> hasActor = () -> c.getCurrentEntity() != null;
        Supplier<Boolean> hasTwoOrMoreReplays = () -> c.panel.getData() != null && c.panel.getData().replays.getList().size() >= 2;

        c.keys().register(Keys.FILM_CONTROLLER_START_RECORDING, c::pickRecording).active(hasActor).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_INSERT_FRAME, () ->
        {
            c.insertFrame();
            UIUtils.playClick();
        }).active(hasActor).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_CONTROL, c::toggleControl).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ORBIT_MODE, c::toggleOrbitMode).category(category);
        registerCameraModeKey(c, Keys.FILM_CONTROLLER_CAMERA_MODE_CAMERA, UIFilmController.CAMERA_MODE_CAMERA, category);
        registerCameraModeKey(c, Keys.FILM_CONTROLLER_CAMERA_MODE_FREE, UIFilmController.CAMERA_MODE_FREE, category);
        registerCameraModeKey(c, Keys.FILM_CONTROLLER_CAMERA_MODE_ORBIT, UIFilmController.CAMERA_MODE_ORBIT, category);
        registerCameraModeKey(c, Keys.FILM_CONTROLLER_CAMERA_MODE_FIRST_PERSON, UIFilmController.CAMERA_MODE_FIRST_PERSON, category);
        registerCameraModeKey(c, Keys.FILM_CONTROLLER_CAMERA_MODE_THIRD_PERSON_BACK, UIFilmController.CAMERA_MODE_THIRD_PERSON_BACK, category);
        registerCameraModeKey(c, Keys.FILM_CONTROLLER_CAMERA_MODE_THIRD_PERSON_FRONT, UIFilmController.CAMERA_MODE_THIRD_PERSON_FRONT, category);
        c.keys().register(Keys.FILM_CONTROLLER_TELEPORT_ORBIT, c::teleportOrbitPivotToReplay).strict().active(() -> c.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_ATTACH_ORBIT, () ->
        {
            c.toggleOrbitAttachment();
            UIUtils.playClick();
        }).strict().active(() -> c.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ORTHO, () ->
        {
            c.orbit.toggleOrtho();
            UIUtils.playClick();
        }).strict().active(() -> c.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_REPLAY_MENU, c::toggleReplayMenu).category(category);
        /* Quick search: eigenes overlay, kein context-menue — es braucht eine tippbare
         * such-zeile, die ein UISimpleContextMenu nicht hat. */
        c.keys().register(Keys.FILM_CONTROLLER_QUICK_REPLAY, () -> UIQuickReplayMenu.open(c.panel)).category(category);
        /* ⚠ AEHNLICHER NAME, GEGENTEILIGE WIRKUNG — die Zeile darueber ERZEUGT ein Replay zu
         * einer Form, diese WAEHLT unter den vorhandenen aus. Die Taste war definiert und tot:
         * ihr Empfaenger (ein Mixin des alten Forks) ging beim Fork-Wechsel verloren. */
        c.keys().register(BBSToolsKeys.QUICK_REPLAY_PICKER, () -> UIQuickReplayOverlayPanel.imEditor(c.panel, c.getContext())).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_MOVE_REPLAY_TO_CURSOR, () ->
        {
            Area area = c.panel.preview.getViewport();
            UIContext context = c.getContext();
            Level world = Minecraft.getInstance().level;
            Camera camera = c.panel.getCamera();

            Vector3f rayOffset = new Vector3f();
            Vector3f rayDirection = camera.getMouseRay(context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, rayOffset);

            HitResult result = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(new Vector3d(camera.position).add(rayOffset.x, rayOffset.y, rayOffset.z)),
                RayTracing.fromVector3f(rayDirection),
                512F
            );

            if (result.getType() == HitResult.Type.BLOCK)
            {
                c.panel.replayEditor.moveReplay(result.getLocation().x, result.getLocation().y, result.getLocation().z);
            }
        }).active(hasActor).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_RESTART_ACTIONS, () ->
        {
            c.panel.notifyServer(ActionState.RESTART);
            c.createEntities();
        }).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ONION_SKIN, () ->
        {
            c.getOnionSkin().enabled.toggle();

            UIUtils.playClick();
        }).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_MOTION_PATH, () ->
        {
            c.getMotionPath().enabled.toggle();

            UIUtils.playClick();
        }).strict().active(() -> !c.panel.hasSelectedClip()).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_TOGGLE_MOTION_PATH_PIN, () ->
        {
            c.toggleMotionPathPin();

            UIUtils.playClick();
        }).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_OPEN_REPLAYS, () ->
        {
            /* Alte Timeline abgeschafft — der Editor-Blick traegt die Replays. */
            c.panel.showPanel(c.panel.cameraEditor);
        }).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_PREV_REPLAY, () -> c.switchReplay(-1)).active(hasTwoOrMoreReplays).category(category);
        c.keys().register(Keys.FILM_CONTROLLER_NEXT_REPLAY, () -> c.switchReplay(1)).active(hasTwoOrMoreReplays).category(category);
    }

    /**
     * A keybind that jumps straight into one camera mode, without the picker
     * {@link UIFilmController#toggleOrbitMode()} opens. Nothing happens when that
     * mode is already active, so holding the key doesn't re-enter it every repeat.
     */
    private static void registerCameraModeKey(UIFilmController c, KeyCombo combo, int mode, IKey category)
    {
        c.keys().register(combo, () ->
        {
            if (c.getPovMode() == mode)
            {
                return;
            }

            c.setPov(mode);
            UIUtils.playClick();
        }).category(category);
    }
}
