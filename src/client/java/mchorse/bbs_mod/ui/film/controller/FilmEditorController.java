package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import java.util.List;
import java.util.Map;

public class FilmEditorController extends BaseFilmController
{
    public UIFilmController controller;

    private int lastTick;

    public FilmEditorController(Film film, UIFilmController controller)
    {
        super(film);

        this.controller = controller;
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return this.controller.getActors();
    }

    @Override
    public int getTick()
    {
        return this.controller.panel.getCursor();
    }

    @Override
    protected void updateEntities(int ticks)
    {
        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        super.updateEntities(ticks);

        this.lastTick = ticks;
    }

    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (isPlaying && isActor)
        {
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        List<String> groups = this.controller.getRecordingGroups();
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (entity != this.controller.getControlled() || (this.controller.isRecording() && this.controller.getRecordingCountdown() <= 0 && groups != null))
        {
            replay.keyframes.apply(ticks, entity, entity == this.controller.getControlled() ? groups : null);
            replay.applyClientActions(ticks, entity, this.film);
        }

        if (entity == this.controller.getControlled() && this.controller.isRecording() && this.controller.panel.getRunner().isRunning())
        {
            replay.keyframes.record(this.controller.panel.getCursor(), entity, groups);
        }

        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        /* Special pausing logic */
        if (!isPlaying && isActor)
        {
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());

            int diff = Math.abs(this.lastTick - ticks);

            while (diff > 0)
            {
                entity.update();

                if (entity.getForm() != null)
                {
                    entity.getForm().update(entity);
                }

                diff -= 1;
            }
        }
    }

    @Override
    public void startRenderFrame(float transition)
    {
        super.startRenderFrame(transition);
        mchorse.bbs_mod.film.markers.TimerCuePreview.apply(this.film, this.entities, this.getTick(),
            this.controller.isRecording() ? this.controller.getRecordingCountdown() : 0);
    }

    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        boolean current = this.isCurrent(entity) && this.controller.isControlling();
        /* Pausiert 0F — ausser eine Sub-Tick-Fahrt erzwingt den Rest t−⌊t⌋ (siehe
         * com.mcstudio.see.SubTick): dann werden die Actor-Eigenschaften (Pose) zum selben
         * Zwischen-Tick ausgewertet wie die Kamera. Ohne Fahrt bleibt es die alte Null. */
        boolean playing = this.controller.panel.isRunning() && this.controller.isPlaying();
        float delta = !playing && !current ? com.mcstudio.see.SubTick.oder(0F) : transition;

        return delta;
    }

    @Override
    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        if (com.afrobama.blendertransform.NurVorschauLogik.ueberspringen(replay, updateMode == UpdateMode.RENDER))
        {
            return false;
        }

        return super.canUpdate(i, replay, entity, updateMode)
            || this.controller.getPovMode() != UIFilmController.CAMERA_MODE_FIRST_PERSON
            || !this.isCurrent(entity)
            || !this.controller.orbit.enabled;
    }

    @Override
    protected void renderEntity(LevelRenderContext context, Replay replay, IEntity entity)
    {
        boolean current = this.isCurrent(entity);

        /* Always run the pass, even for the body first person hides — hidesBody() turns it into a gizmo-only
         * pass over in getFilmControllerContext. This pass is what PLACES the bone gizmo: it captures
         * the frame the visual and the drag are both rebuilt from (Gizmo#captureVisual). Skipping it outright,
         * as this did before, left that frame at its last capture: in first person the gizmo stood frozen
         * wherever it had been when the mode was entered, and dragging it edited from that stale frame. */
        super.renderEntity(context, replay, entity);

        boolean isPlaying = this.controller.isPlaying();
        int ticks = replay.getTick(this.getTick());
        ValueOnionSkin onionSkin = this.controller.getOnionSkin();
        BaseValue value = replay.properties.get(onionSkin.group.get());

        if (value == null)
        {
            value = replay.properties.get("pose");
        }

        if (value instanceof KeyframeChannel<?> pose && entity instanceof StubEntity)
        {
            boolean canRender = onionSkin.enabled.get();

            if (!onionSkin.all.get())
            {
                canRender = canRender && current;
            }

            if (canRender)
            {
                KeyframeSegment<?> segment = pose.findSegment(ticks);

                if (segment != null)
                {
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.a), -1, pose, onionSkin.preColor.get(), onionSkin.preFrames.get(), context, isPlaying, entity);
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.b), 1, pose, onionSkin.postColor.get(), onionSkin.postFrames.get(), context, isPlaying, entity);

                    replay.keyframes.apply(ticks, entity);
                    float tick = ticks + this.getTransition(entity, net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
                    Form form = entity.getForm();
                    replay.properties.applyProperties(form, tick);

                    if (!isPlaying)
                    {
                        entity.setPrevX(entity.getX());
                        entity.setPrevY(entity.getY());
                        entity.setPrevZ(entity.getZ());
                        entity.setPrevYaw(entity.getYaw());
                        entity.setPrevHeadYaw(entity.getHeadYaw());
                        entity.setPrevBodyYaw(entity.getBodyYaw());
                        entity.setPrevPitch(entity.getPitch());
                    }
                }
            }
        }
    }

    private void renderOnion(Replay replay, int index, int direction, KeyframeChannel<?> pose, int color, int frames, LevelRenderContext context, boolean isPlaying, IEntity entity)
    {
        List<? extends Keyframe<?>> keyframes = pose.getKeyframes();
        float alpha = Colors.getA(color);

        for (; CollectionUtils.inRange(keyframes, index) && frames > 0; index += direction)
        {
            Keyframe<?> keyframe = keyframes.get(index);

            if (keyframe.getTick() == this.getTick())
            {
                continue;
            }

            int tick1 = (int) keyframe.getTick();
            replay.keyframes.apply(tick1, entity);
            float tick = (int) keyframe.getTick();
            Form form = entity.getForm();
            replay.properties.applyProperties(form, tick);

            BaseFilmController.renderEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, context)
                .color(Colors.setA(color, alpha))
                .transition(0F));

            frames -= 1;
            alpha *= alpha;
        }
    }

    @Override
    protected FilmControllerContext getFilmControllerContext(LevelRenderContext context, Replay replay, IEntity entity)
    {
        Pair<String, Boolean> bone = this.isCurrent(entity) && !this.controller.panel.recorder.isRecording() ? this.controller.getBone() : null;
        String aBone = bone == null ? null : bone.a;
        boolean local = bone != null && bone.b;
        String aBone2 = null;
        boolean local2 = false;

        if (replay.axesPreview.get())
        {
            aBone2 = replay.axesPreviewBone.get();
            local2 = true;
        }

        if (this.controller.panel.recorder.isRecording())
        {
            aBone = null;
            local = false;
            aBone2 = null;
            local2 = false;
        }

        boolean anchorGizmo = this.isCurrent(entity)
            && !this.controller.panel.recorder.isRecording()
            && this.controller.isAnchorGizmo();

        return super.getFilmControllerContext(context, replay, entity)
            .transition(this.getTransition(entity, net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)))
            .bone(aBone, local)
            .gizmoSpace(this.controller.getBoneSpace(), this.controller.getGizmoView())
            .bone2(aBone2, local2)
            .anchorGizmo(anchorGizmo, this.controller.getAnchorLocal())
            .gizmoOnly(this.hidesBody(entity));
    }

    /**
     * One entity's pick pass, for the film viewport's stencil picker
     * ({@code UIFilmController#renderStencil}, which composes its own context by
     * hand instead of going through {@link #getFilmControllerContext}).
     *
     * <p>The same {@link #hidesBody} rule applies: a body hidden by first person
     * must not go into the pick buffer either — seen from the inside it covers the
     * whole viewport and would swallow every click. Its gizmo handles DO stay in
     * the buffer; that bone is exactly what the user is editing. The caller used
     * to bail out of the whole pick pass instead, which kept the body out but
     * took the gizmo with it, so nothing at all was pickable in first person.
     */
    public void renderStencilEntity(FilmControllerContext context, IEntity entity)
    {
        BaseFilmController.renderEntity(context.gizmoOnly(this.hidesBody(entity)));
    }

    /**
     * Whether first person hides this entity's body — the camera sits inside the
     * current actor, so drawing its model would fill the screen from the inside.
     * The entity is still rendered, as a gizmo-only pass, because that pass is
     * what places the bone gizmo.
     *
     * <p>Reads {@code getPovMode()}, not the raw pov field: cycling the mode
     * (UIFilmController's orbit icon) counts past 5 and only the wrapped value is
     * the actual mode.
     */
    private boolean hidesBody(IEntity entity)
    {
        return this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && this.isCurrent(entity);
    }

    private boolean isCurrent(IEntity entity)
    {
        return entity == this.controller.getCurrentEntity();
    }
}
