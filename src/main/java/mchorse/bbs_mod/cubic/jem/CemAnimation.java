package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A live, procedural OptiFine CEM animation program: an ordered list of {@code variable = expression}
 * statements evaluated every frame and written into the model's bones. The program (parser,
 * statements, bone bindings) is shared by every instance of the model; what persists between frames
 * for one instance lives in a {@link CemState}, which the owning {@link CemAnimator} keeps.
 *
 * <p>Each frame {@link #apply} advances the state's clock (which may re-seed it), loads the entity's
 * {@code var.*}/{@code varb.*} into the shared variables, feeds the render parameters in
 * ({@link CemParameters}), seeds every bone's model variables from the vanilla frame or its current
 * pose ({@link CemBinding}), evaluates the statements in order (later statements see earlier results,
 * cross-bone references included), writes the bones back and stores the entity variables again.</p>
 *
 * <p>A bone's kind is its place in the <em>file</em>, not in the bone tree: a part, a part's direct
 * submodel, or anything deeper. A part the vanilla rig hung on another part goes parent-relative,
 * while its direct submodels stay direct — Fresh Animations' allay carried its head six pixels too
 * high while they were read as deeper ones.</p>
 *
 * <p>From BBS FS b44e56c87, dc150bb3e, ef4007ec2, 26ca19c49, d3f28b58f, 3b4cc251f, dfaca7a6f,
 * 355b61b40, 6d7aa0fd1 (Wemppy4/bbs-fs, MIT).</p>
 */
public class CemAnimation
{
    /**
     * Ticks of animation run off screen whenever a stand-in's state starts from zero. A pack's
     * {@code var.*} are a running simulation, and zero is not what they hold once an entity has stood
     * around: Fresh Animations' player keeps {@code var.t_land} at 1 when settled, and from 0 that timer
     * sweeps its landing curve — the actor crouched every time a film was scrubbed. Two seconds covers
     * the timers that matter.
     */
    private static final int WARM_UP_TICKS = 40;

    /** Ticks one warm-up pass covers: a settle only has to arrive, not be watched, so coarser than a frame */
    private static final int WARM_UP_STEP = 2;

    public final CemParser parser;

    /** The model file's name without its extension, set by the loader */
    public String jem = "";

    private final List<Statement> statements = new ArrayList<>();
    private final List<CemBinding> bindings = new ArrayList<>();
    private final Map<ModelGroup, CemBinding> byGroup = new HashMap<>();
    private List<ModelGroup> roots = Collections.emptyList();

    /** The {@code var.*}/{@code varb.*} in a fixed order, collected once in {@link #setup} */
    private final List<Variable> entityVariables = new ArrayList<>();

    /** The file's parts — its top-level entries — as opposed to the submodels under them */
    private final Set<ModelGroup> parts = new HashSet<>();

    /** Parts the vanilla rig reparented: the pack positions them against the parent's pivot */
    private final Set<ModelGroup> parentRelative = new HashSet<>();

    public CemAnimation()
    {
        this.parser = new CemParser();

        /* Health is the one pair a pack may not read as zero, and a program can run without an entity */
        this.parser.setValue("health", CemEntity.FULL_HEALTH);
        this.parser.setValue("max_health", CemEntity.FULL_HEALTH);
    }

    public void markPart(ModelGroup group)
    {
        this.parts.add(group);
    }

    public void markParentRelative(ModelGroup group)
    {
        this.parentRelative.add(group);
    }

    public boolean isEmpty()
    {
        return this.statements.isEmpty();
    }

    /** Whether any statement writes a channel of this bone */
    public boolean mentions(String bone)
    {
        for (Statement statement : this.statements)
        {
            if (statement.target.getName().startsWith(bone + ".")) return true;
        }

        return false;
    }

    /** Record a {@code variable = expression} statement, preserving evaluation order. */
    public void addStatement(String target, String expression)
    {
        this.statements.add(new Statement(this.parser.getOrCreateVariable(target), this.parser.parseExpression(expression)));
    }

    /** The {@code "animations"} of one model entry: each key a target, each value an expression */
    public void sammeln(JsonObject entry)
    {
        if (!entry.has("animations")) return;

        for (JsonElement element : entry.getAsJsonArray("animations"))
        {
            if (!element.isJsonObject()) continue;

            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet())
            {
                if (e.getValue().isJsonPrimitive()) this.addStatement(e.getKey(), e.getValue().getAsString());
            }
        }
    }

    /** Build the per-bone bindings and the entity variable list once the hierarchy is known */
    public void setup(Model model)
    {
        this.bindings.clear();
        this.byGroup.clear();
        this.roots = model.topGroups;

        for (ModelGroup group : model.getAllGroups())
        {
            CemBinding binding = new CemBinding(this.parser, group, this.kind(group));

            this.bindings.add(binding);
            this.byGroup.put(group, binding);
        }

        this.entityVariables.clear();

        for (Variable variable : this.parser.variables.values())
        {
            String name = variable.getName();

            if (name.startsWith("var.") || name.startsWith("varb.")) this.entityVariables.add(variable);
        }

        /* A fixed order, so a state's slots mean the same thing however the parser's map iterates */
        this.entityVariables.sort(Comparator.comparing(Variable::getName));
    }

    private int kind(ModelGroup group)
    {
        if (this.parts.contains(group))
        {
            return this.parentRelative.contains(group) ? CemBinding.SUBN : CemBinding.TOP;
        }

        return this.parts.contains(group.parent) ? CemBinding.SUB1 : CemBinding.SUBN;
    }

    /**
     * Evaluate this frame on an instance's state and write it into the bones; {@code inGui} is CEM's
     * is_in_gui, {@code seed} the vanilla frame the program starts from, or null for the rest pose.
     */
    public void apply(CemState state, IEntity target, float transition, boolean inGui, CemStatus status, CemVanillaSeed seed)
    {
        if (this.statements.isEmpty())
        {
            return;
        }

        /* The entity clock is the frame id: constant across the passes of one frame, growing between them */
        double frameTime = state.advance(target == null ? Double.NaN : target.getAge() + transition);

        state.load(this.entityVariables);

        /* Values just started from zero are not what a settled entity holds. Only a stand-in: an entity
         * that really spawned should play its pack's spawn animation */
        if (!state.warmed)
        {
            state.warmed = true;

            if (target != null && CemEntity.standIn(target))
            {
                for (int ticksAgo = WARM_UP_TICKS; ticksAgo > 0; ticksAgo -= WARM_UP_STEP)
                {
                    this.evaluate(state, target, transition, inGui, status, seed, WARM_UP_STEP / 20D, -ticksAgo);
                }
            }
        }

        this.evaluate(state, target, transition, inGui, status, seed, frameTime, 0);
        state.store(this.entityVariables);
    }

    /** One pass: parameters in, bones out; {@code ticksAgo} is negative for a warm-up pass */
    private void evaluate(CemState state, IEntity target, float transition, boolean inGui, CemStatus status, CemVanillaSeed seed, double frameTime, int ticksAgo)
    {
        this.parser.setValue("frame_time", frameTime);
        /* A warm-up pass must not look like a repeat of the frame before, or a pack's own "same frame"
         * guard would hold every drag where it started */
        this.parser.setValue("frame_counter", state.frameCounter + ticksAgo);
        this.parser.setValue("is_in_gui", inGui ? 1 : 0);

        if (target != null) CemParameters.set(this.parser, target, transition, ticksAgo, CemNames.baby(this.jem), status);

        for (CemBinding binding : this.bindings) binding.reset(seed);

        for (Statement statement : this.statements)
        {
            statement.target.set(statement.expression.get().doubleValue());
        }

        for (CemBinding binding : this.bindings) binding.writeback();

        for (ModelGroup root : this.roots) this.show(root, true);
    }

    /**
     * Visibility the way OptiFine reads it: a part written invisible takes its whole subtree with it,
     * and {@code visible_boxes} hides the part's own boxes alone. Fresh Animations' evoker hides
     * {@code arms} while casting, and the crossed arms sit in a submodel of it.
     */
    private void show(ModelGroup group, boolean parentShown)
    {
        CemBinding binding = this.byGroup.get(group);
        boolean shown = parentShown && (binding == null || binding.shown());

        group.visible = shown && (binding == null || binding.boxesShown());

        for (ModelGroup child : group.children) this.show(child, shown);
    }

    private record Statement(Variable target, IExpression expression)
    {}
}
