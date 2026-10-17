package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIShapeKeysKeyframeFactory;
import mchorse.bbs_mod.ui.utils.shapes.ShapeControllerOverlay;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;

/**
 * The shape key controllers' handles in the film viewport, while a shape key keyframe is open: they
 * sit at their bones on the replay and drag height and tilt there. From BBS FS 7a049804a
 * (Wemppy4/bbs-fs, MIT); kept out of UIFilmController, which may not grow.
 */
public final class FormSchluesselGriffe
{
    private static final ShapeControllerOverlay OVERLAY = new ShapeControllerOverlay();

    private FormSchluesselGriffe()
    {}

    private static UIShapeKeysKeyframeFactory offen(UIFilmController controller)
    {
        var editor = controller.panel.replayEditor.keyframeEditor;

        return controller.isRecording() || controller.panel.isFlying() || editor == null || !(editor.editor instanceof UIShapeKeysKeyframeFactory keys) ? null : keys;
    }

    public static boolean klick(UIFilmController controller, UIContext context)
    {
        return offen(controller) != null && OVERLAY.click(context)
            || mchorse.bbs_mod.client.renderer.item.GehaltenerBlockPicker.klick(controller.panel, context);
    }

    public static boolean loslassen()
    {
        return OVERLAY.release();
    }

    public static void zeichnen(UIFilmController controller, UIContext context)
    {
        UIShapeKeysKeyframeFactory keys = offen(controller);
        IEntity entity = controller.getCurrentEntity();
        Replay replay = controller.panel.replayEditor.getReplay();

        if (keys == null || entity == null || replay == null)
        {
            OVERLAY.clear();

            return;
        }

        Camera camera = controller.panel.getCamera();
        float transition = controller.isPlaying() ? context.getTransition() : 0F;

        OVERLAY.draw(context, keys.controls, controller.panel.preview.getViewport(), camera.projection, (c) ->
        {
            String path = StringUtils.combinePaths(FormUtils.getPath(keys.form), c.bone.get());
            Matrix4f matrix = BaseFilmController.getBoneCompositeMatrix(controller.getEntities(), entity, replay,
                camera.position.x, camera.position.y, camera.position.z, transition, path, true);

            return matrix == null ? null : new Matrix4f(camera.view).mul(matrix);
        }, keys.controls::endGesture, keys.controls::endGesture);
    }
}
