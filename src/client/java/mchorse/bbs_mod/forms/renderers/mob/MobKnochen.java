package mchorse.bbs_mod.forms.renderers.mob;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The bones of a vanilla entity model as the mob form sees them, named the way vanilla named them
 * ({@code head}, {@code left_arm}) off the {@link ModelPart} tree instead of the model's Java fields
 * ({@code leftArm}). With field names the pose's left/right table, which matches
 * {@code left_something}, never fired for a mob: mirror editing and flipping a pose did nothing.
 * Kept per model, not per entity class: a slim and a wide player are one class with two models, and
 * a pose went into the other one's arms. A pose saved under a field name still applies, through
 * {@link #wert}. From BBS FS e341fa733 (Wemppy4/bbs-fs, MIT).
 */
public final class MobKnochen
{
    private static final Map<EntityModel<?>, MobKnochen> RIGS = new WeakHashMap<>();

    /** Canonical name to part, depth first from the root */
    public final Map<String, ModelPart> teile = new LinkedHashMap<>();
    /** Canonical name to the field name the same part used to be saved under */
    private final Map<String, String> alt = new LinkedHashMap<>();

    private MobKnochen(EntityModel<?> model)
    {
        for (Map.Entry<String, ModelPart> kind : model.root().children.entrySet()) this.besuchen(kind.getKey(), kind.getValue(), "");

        Map<ModelPart, String> namen = new IdentityHashMap<>();

        this.teile.forEach((name, teil) -> namen.put(teil, name));

        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass())
        {
            for (Field feld : c.getDeclaredFields())
            {
                if (!feld.getType().equals(ModelPart.class)) continue;

                try
                {
                    feld.setAccessible(true);

                    ModelPart teil = (ModelPart) feld.get(model);
                    String name = teil == null ? null : namen.get(teil);

                    if (name == null && teil != null && teil != model.root()) this.teile.putIfAbsent(feld.getName(), teil);
                    else if (name != null && !name.equals(feld.getName())) this.alt.putIfAbsent(name, feld.getName());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private void besuchen(String name, ModelPart teil, String eltern)
    {
        /* Names are unique per parent only; a second "cube" elsewhere is told apart by its parent */
        this.teile.put(this.teile.containsKey(name) ? eltern + "." + name : name, teil);

        for (Map.Entry<String, ModelPart> kind : teil.children.entrySet()) this.besuchen(kind.getKey(), kind.getValue(), name);
    }

    /** The rig of the model this entity is drawn with, or null when it is no living entity */
    public static MobKnochen von(Entity entity)
    {
        if (entity == null || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?, ?> renderer))
        {
            return null;
        }

        return RIGS.computeIfAbsent(renderer.getModel(), MobKnochen::new);
    }

    /** The pose's entry for a bone: under its name, or the field name it was saved under before */
    public <T> T wert(Map<String, T> pose, String name)
    {
        T wert = pose.get(name);

        if (wert == null && this.alt.containsKey(name)) wert = pose.get(this.alt.get(name));

        return wert;
    }
}
