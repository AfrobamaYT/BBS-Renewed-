package mchorse.bbs_mod.film;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Replay actors are absent from Vitrail's world-entity walk; the light needs its own producer. */
public final class NativeReplayShadows
{
    private record Caster(BaseFilmController controller, Replay replay, IEntity entity, float transition) {}
    private static final ArrayList<Caster> CASTERS = new ArrayList<>();

    private NativeReplayShadows() {}

    public static int gather(float transition, LevelRenderState lightState)
    {
        clear();
        Set<BaseFilmController> controllers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Entity> worldOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        lightState.entityRenderStates.forEach(state -> worldOwners.add(MorphRenderer.entityVon(state)));
        Films films = BBSModClient.getFilms();
        controllers.addAll(films.getControllers());
        if (films.getRecorder() != null) controllers.add(films.getRecorder());
        /* Only the panel whose renderInWorld runs, not a cached closed editor. */
        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard
            && dashboard.panels.panel instanceof UIFilmPanel panel
            && panel.getController().editorController != null)
            controllers.add(panel.getController().editorController);
        for (BaseFilmController controller : controllers) gather(controller, transition, worldOwners);
        return CASTERS.size();
    }

    private static void gather(BaseFilmController controller, float transition, Set<Entity> worldOwners)
    {
        for (Map.Entry<Integer, IEntity> entry : controller.entities.entrySet())
        {
            int index = entry.getKey();
            if (index < 0 || index >= controller.film.replays.getList().size()) continue;
            Replay replay = controller.film.replays.getList().get(index);
            /* Real ActorEntities already enter the light's world walk. */
            if (replay.actor.get() || !controller.canUpdate(index, replay, entry.getValue(), BaseFilmController.UpdateMode.RENDER)) continue;
            /* A controlled editor replay may temporarily be the real morphed player. */
            if (entry.getValue() instanceof MCEntity real && worldOwners.contains(real.getMcEntity())) continue;
            CASTERS.add(new Caster(controller, replay, entry.getValue(), controller.getTransition(entry.getValue(), transition)));
        }
    }

    public static void submit(CameraRenderState lightCamera, SubmitNodeStorage storage)
    {
        boolean previous = FilmControllerContext.sammelDurchgang;
        FilmControllerContext.sammelDurchgang = true;
        try
        {
            FormSammler.mitSammler(storage, () ->
            {
                for (Caster caster : CASTERS)
                {
                    /* Conservative light-frustum production: submit all eligible replay geometry.
                     * The actual light projection clips it. Picking hitboxes cannot bound a scaled,
                     * animated or anchored rig and would incorrectly drop off-camera casters. */
                    FilmControllerContext context = new FilmControllerContext().setup(caster.controller().entities,
                        caster.entity(), caster.replay(), lightCamera, new PoseStack(), storage, caster.transition())
                        .relative(caster.replay().relative.get());
                    BaseFilmController.renderEntity(context);
                }
            });
        }
        finally { FilmControllerContext.sammelDurchgang = previous; }
    }

    public static void clear() { CASTERS.clear(); }
}
