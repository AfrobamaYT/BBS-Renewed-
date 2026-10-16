package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelIKRuntime
{
    private ModelIKRuntime()
    {
    }

    public static void clearCache()
    {
        ModelIKCache.clear();
    }

    public static void invalidate(String modelId)
    {
        clearCache();
    }

    /* ⛔ apply() ist ENTFERNT. BBS loest keine eigenen IK-Ketten mehr: die IK des
     * importierten Rigs laeuft ueber RigMechanik/RigIKKetten mit dem portierten
     * Blender-Solver, in Blenders Auswertungsreihenfolge. Was hier bleibt, sind
     * reine ABFRAGEN fuer die Oberflaeche. */

    public static boolean isCyclicTarget(IModel model, String tip, int chainLength, String target)
    {
        if (model == null || tip == null || tip.isEmpty() || target == null || target.isEmpty())
        {
            return false;
        }

        return ModelIKCache.chainIdsFor(model, tip, chainLength).contains(target);
    }

    /**
     * Opens the IK solve dump for the duration of one transform gesture, so the
     * log holds exactly the drag being investigated — the transform editor calls
     * this when a drag begins and ends. A no-op unless the applier's debug flag
     * is compiled on, so the call sites cost nothing in a normal build.
     */
    public static void logGesture(boolean active)
    {
    }

    /**
     * Whether the bone's ROTATION is owned by an enabled IK chain: the render
     * follows the solved orientation there, so an FK rotation gesture has no
     * honest response to offer — the gizmo refuses those and dims its rings.
     * The FK channels stay editable through the pads (they remain the blend
     * base and the pose IK falls back to when it's off). A chain whose weight
     * is zeroed (config, or the film's per-tick override on the form) doesn't
     * constrain anything.
     *
     * <p>⛔ Nur die SPITZE mit gelöster Spitzendrehung zählt — nicht die
     * übrigen Kettenglieder. Die trugen den Zwang bis 2026-08-15, und das war
     * falsch: der Löser bekommt jedes Segment mit der aktuellen Pose als
     * Startbasis ({@code IKBOBJBruecke}: {@code basis} aus {@code b.mat}),
     * die Drehung eines Kettenglieds wirkt also — sie schwenkt die Kette im
     * Nullraum, genau wie in Blender. Gemessen an
     * {@code getBoneCompositeMatrix}: {@code upper_arm_ik.R} um 0,9 rad
     * gedreht bewegt sich selbst um 0,836 und zieht {@code MCH-forearm_ik.R}
     * (0,835) und {@code DEF-upper_arm.R} (0,127) mit, während Elter und
     * IK-Ziel stehenbleiben. Der Nutzer meldete genau das als Abweichung:
     * {@code upper_arm_ik.L/R} ließen sich nicht mit R drehen.
     */
    public static boolean isRotationConstrained(IModel model, ModelForm form, String bone)
    {
        if (model == null || form == null || bone == null || bone.isEmpty())
        {
            return false;
        }

        if (!(form.ik.get() instanceof MapType map))
        {
            return false;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, map);

        if (compiled == null || compiled.chains() == null)
        {
            return false;
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain == null || chain.weight() <= 0F || chain.chainRootToEffector() == null)
            {
                continue;
            }

            /* A chain the film disabled or zeroed for this tick — its per-tick `ik`
             * track override — no longer owns any rotation, so its bones become
             * FK-editable again. That override lives in ikControlOverrides keyed by
             * the tip (the same one resolveChain reads), NOT in ikTargetWeights,
             * which is only the target's position-fade weight and never reaches 0. */
            IKControl override = form.ikControlOverrides == null ? null : form.ikControlOverrides.get(chain.tip());

            if (override != null && (!override.enabled || override.weight <= 0F))
            {
                continue;
            }

            if (!chain.chainRootToEffector().contains(bone))
            {
                continue;
            }

            if (bone.equals(chain.tip()) && chain.tipRotation())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The target of the enabled chain {@code bone} belongs to, when that target is a bone of the model
     * (a control the pose keys); else null. G on a chain bone grabs it (IKGriff).
     */
    public static String zielFuer(IModel model, ModelForm form, String bone)
    {
        ModelIKCache.Compiled compiled = model == null || form == null || bone == null || !(form.ik.get() instanceof MapType map) ? null : ModelIKCache.getFromData(model, map);

        for (ModelIKCache.CompiledChain chain : compiled == null || compiled.chains() == null ? List.<ModelIKCache.CompiledChain>of() : compiled.chains())
        {
            IKControl override = chain == null || form.ikControlOverrides == null ? null : form.ikControlOverrides.get(chain.tip());
            boolean an = chain != null && chain.weight() > 0F && (override == null || (override.enabled && override.weight > 0F));

            if (an && chain.chainRootToEffector() != null && chain.chainRootToEffector().contains(bone) && model.getAllGroupKeys().contains(chain.target()))
            {
                return chain.target();
            }
        }

        return null;
    }

    public static List<String> getControllers(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return Collections.emptyList();
        }

        IModel model = instance.model;

        ModelIKCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm form && form.ik.get() instanceof MapType map)
        {
            compiled = ModelIKCache.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain != null && chain.target() != null && !chain.target().isEmpty())
            {
                unique.add(chain.target());
            }
        }

        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }

    /** The pole-target bones of all enabled chains that have one — the film keys a pole anchor sheet off each. */
    public static List<String> getPoleControllers(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return Collections.emptyList();
        }

        IModel model = instance.model;

        ModelIKCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm form && form.ik.get() instanceof MapType map)
        {
            compiled = ModelIKCache.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            if (chain != null && chain.pole() && chain.poleTarget() != null && !chain.poleTarget().isEmpty())
            {
                unique.add(chain.poleTarget());
            }
        }

        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }
}
