package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;

import java.util.Collections;
import java.util.List;

/**
 * The animator stage of an OptiFine CEM model: the model's {@link CemAnimation} program, with its own
 * {@link CemState}. It sits where the procedural animator does in the channels pipeline (rest &rarr;
 * animator &rarr; form pose), so the form's pose and the film's keyframes layer on top of the live
 * animation, as on any other model. An animator lives per form renderer, which makes the clock per
 * instance; the program's {@code var.*}/{@code varb.*} belong to the entity (see {@link CemVariables}).
 *
 * <p>The vanilla frame comes from an {@link ICemVanillaStage}: the game's own model of the entity,
 * posed by the game's own code. From BBS FS dc150bb3e, 057858695, 6d7aa0fd1 (Wemppy4/bbs-fs, MIT). There are no named actions: a .jem
 * carries no keyframe animations.</p>
 */
public class CemAnimator implements IAnimator
{
    private final CemAnimation program;

    /** The vanilla stage, or null for a program run with no game under it */
    private final ICemVanillaStage stage;

    /** The store for an instance with no entity to share one with — a UI preview. */
    private final CemVariables own = new CemVariables();

    /** The states the form sets by hand, refilled by the renderer each frame; per animator, as the program is shared by every form of the model */
    public final CemStatus status = new CemStatus();

    private CemState state;
    private CemVariables bound;

    /**
     * The entity a preview stands on. A form editor or a palette icon renders without one, and a pack
     * asked about an entity that is not there reads every parameter as zero: not on the ground, not
     * alive, at the world origin; Fresh Animations' player poses that as falling. This one stands still,
     * alive, on the ground, and its clock follows the preview's own frames so the idle animation breathes.
     */
    private final StubEntity preview = new StubEntity();

    /** The preview clock, in ticks, off wall time — a preview has no entity age to follow. */
    private double previewTicks;
    private long previewNanos;

    /** The form whose CEM states are read each frame, or null */
    private final mchorse.bbs_mod.forms.forms.ModelForm form;

    public CemAnimator(CemAnimation program, ICemVanillaStage stage, mchorse.bbs_mod.forms.forms.ModelForm form)
    {
        this.form = form;
        this.program = program;
        this.stage = stage;
        this.preview.setOnGround(true);
    }

    @Override
    public List<String> getActions()
    {
        return Collections.emptyList();
    }

    @Override
    public void setup(IModelInstance model, ActionsConfig actionsConfig, boolean fade)
    {}

    @Override
    public void applyActions(IEntity entity, IModelInstance cubicModel, float transition)
    {
        boolean inGui = entity == null;

        if (inGui)
        {
            /* The preview's own clock, and the frame's place in it: the game's partial tick belongs to
             * the game's ticks, which the preview's are not in step with. */
            entity = this.preview();
            transition = (float) (this.previewTicks - Math.floor(this.previewTicks));
        }

        CemVanillaSeed seed = this.stage == null ? null : this.stage.seed(entity, transition);

        /* Read here rather than kept in sync, so a keyframe on a CEM state lands the frame it changes */
        if (this.form != null) this.status.read(this.form);

        this.program.apply(this.state(entity), entity, transition, inGui, this.status, seed);
    }

    /** The stand-in entity, its clock stepped to now */
    private IEntity preview()
    {
        long now = System.nanoTime();

        /* Capped like the animation clock: a preview off screen for a minute resumes, not fast-forwards */
        if (this.previewNanos != 0) this.previewTicks += Math.min(CemState.MAX_FRAME_TIME, (now - this.previewNanos) / 1.0e9D) * 20D;

        this.previewNanos = now;
        int tick = (int) this.previewTicks;

        /* A preview has no tick of its own, so the stage is stepped once per tick of its clock */
        if (this.stage != null && tick != this.preview.getAge()) this.stage.tick(this.preview);

        this.preview.setAge(tick);

        return this.preview;
    }

    /**
     * This model's clock, bound to the store it draws its variables from: the entity's, or this
     * animator's own. A new store means a different entity, so the state starts over rather than
     * carrying a stranger's frame stamp.
     */
    private CemState state(IEntity entity)
    {
        CemVariables variables = entity == this.preview ? this.own : CemEntity.variables(entity);

        if (this.state == null || this.bound != variables)
        {
            this.state = new CemState(variables);
            this.bound = variables;
        }

        return this.state;
    }

    @Override
    public void playAnimation(String name)
    {}

    @Override
    public void update(IEntity entity)
    {
        if (this.stage != null) this.stage.tick(entity);
    }
}
