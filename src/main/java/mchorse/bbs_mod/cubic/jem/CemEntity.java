package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.fox.Fox;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a CEM pack asks about an entity beyond what {@link IEntity} carries: read off the game's entity
 * where a morph rides one, and the settled defaults of a living, idle creature for a film's stand-in.
 * A name the program does not know reads as zero, and zero is a state of its own: an iron golem written
 * around {@code if(health<=15, ...)} posed as dying, a cat could never sit. From BBS FS 2e57dad4f,
 * f5a344a38, e793af689, 8581ea26b (Wemppy4/bbs-fs, MIT), where these are {@code IEntity} methods;
 * here they stay out of the entity classes, which are near their size limit.
 */
public final class CemEntity
{
    public static final float FULL_HEALTH = 20F;

    /** The {@code var.*} store of each entity: OptiFine scopes them to the entity, not the model (see {@link CemVariables}) */
    private static final Map<IEntity, CemVariables> VARIABLES = Collections.synchronizedMap(new WeakHashMap<>());

    private CemEntity()
    {}

    public static CemVariables variables(IEntity entity)
    {
        return VARIABLES.computeIfAbsent(entity, (e) -> new CemVariables());
    }

    /** A film's actor or a preview's stand-in, as opposed to an entity the game spawned */
    public static boolean standIn(IEntity entity)
    {
        return entity instanceof StubEntity;
    }

    private static Entity mc(IEntity entity)
    {
        return entity instanceof MCEntity mc ? mc.getMcEntity() : null;
    }

    private static LivingEntity living(IEntity entity)
    {
        return mc(entity) instanceof LivingEntity living ? living : null;
    }

    public static int id(IEntity entity)
    {
        Entity mc = mc(entity);

        return mc != null ? mc.getId() : System.identityHashCode(entity) & 0xFFFF;
    }

    public static int deathTime(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null ? living.deathTime : entity.getDeathTime();
    }

    public static boolean riding(IEntity entity)
    {
        Entity mc = mc(entity);

        return mc != null && mc.isPassenger();
    }

    public static boolean ridden(IEntity entity)
    {
        Entity mc = mc(entity);

        return mc != null && mc.isVehicle();
    }

    public static boolean child(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null && living.isBaby();
    }

    public static float health(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null ? living.getHealth() : FULL_HEALTH;
    }

    /** An attribute can read zero before the server sent it, and packs divide by it */
    public static float maxHealth(IEntity entity)
    {
        LivingEntity living = living(entity);
        float max = living != null ? living.getMaxHealth() : 0F;

        return max > 0F ? max : FULL_HEALTH;
    }

    public static boolean burning(IEntity entity)
    {
        Entity mc = mc(entity);

        return mc != null && mc.isOnFire();
    }

    public static boolean inLava(IEntity entity)
    {
        Entity mc = mc(entity);

        return mc != null && mc.isInLava();
    }

    public static boolean climbing(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null && living.onClimbable();
    }

    public static boolean crawling(IEntity entity)
    {
        Entity mc = mc(entity);

        return mc != null && mc.isVisuallyCrawling();
    }

    public static boolean sitting(IEntity entity)
    {
        Entity mc = mc(entity);

        if (mc instanceof TamableAnimal tamable) return tamable.isInSittingPose();
        if (mc instanceof Fox fox) return fox.isSitting();
        if (mc instanceof Bat bat) return bat.isResting();

        return mc == null && entity.isSitting();
    }

    public static boolean tamed(IEntity entity)
    {
        return mc(entity) instanceof TamableAnimal tamable && tamable.isTame();
    }

    public static boolean aggressive(IEntity entity)
    {
        return mc(entity) instanceof Mob mob && mob.isAggressive();
    }

    /** Vanilla thinks in a main hand and remembers which arm it is; a pack thinks in arms */
    public static boolean rightHanded(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living == null || living.getMainArm() == HumanoidArm.RIGHT;
    }

    public static boolean blocking(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null && living.isBlocking();
    }

    public static boolean swinging(IEntity entity, float transition)
    {
        LivingEntity living = living(entity);

        return living != null ? living.isSwinging() : entity.getHandSwingProgress(transition) > 0F;
    }

    public static boolean swingingOffHand(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null && living.getCurrentSwing() != null && living.getCurrentSwing().hand() == InteractionHand.OFF_HAND;
    }

    public static float forward(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null ? living.zza : 0F;
    }

    public static float sideways(IEntity entity)
    {
        LivingEntity living = living(entity);

        return living != null ? living.xxa : 0F;
    }
}
