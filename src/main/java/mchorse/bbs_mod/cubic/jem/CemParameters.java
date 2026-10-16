package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The entity's render parameters as a CEM program reads them, fed into its parser once per pass. From
 * BBS FS b44e56c87, 2e57dad4f, b4bb9d09b, 687133c56, f5a344a38, e793af689, 8581ea26b
 * (Wemppy4/bbs-fs, MIT).
 */
final class CemParameters
{
    /**
     * Ticks added to a stand-in's age so a pack does not read it as freshly spawned: Fresh Animations'
     * player lands while {@code age < 9}, its cow waits for {@code age > 60} before leaving the ground,
     * and a stand-in starts counting at zero whenever a film loads.
     */
    static final int SPAWN_SETTLED = 100;

    private CemParameters()
    {}

    /** @param babyFile a pack's _baby file is a child by definition, whatever the actor under it says (BBS FS 2f1d64e70) */
    static void set(CemParser parser, IEntity target, float transition, int ticksAgo, boolean babyFile, CemStatus status)
    {
        float headYaw = Mth.rotLerp(transition, target.getPrevHeadYaw(), target.getHeadYaw());
        float bodyYaw = Mth.rotLerp(transition, target.getPrevBodyYaw(), target.getBodyYaw());
        float pitch = Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        float yaw = Lerps.lerp(target.getPrevYaw(), target.getYaw(), transition);
        boolean standIn = CemEntity.standIn(target);
        double age = target.getAge() + transition + ticksAgo + (standIn ? SPAWN_SETTLED : 0);
        boolean child = babyFile || CemEntity.child(target);

        /* Vanilla hands a child's model its limb swing three times over: the young take quicker steps */
        parser.setValue("limb_swing", target.getLimbPos(transition) * (child ? 3F : 1F));
        parser.setValue("limb_speed", target.getLimbSpeed(transition));
        parser.setValue("age", age);
        parser.setValue("time", age);
        /* Head and body may differ by whole turns; packs clamp this relative angle before using it */
        parser.setValue("head_yaw", Mth.wrapDegrees(headYaw - bodyYaw));
        parser.setValue("head_pitch", pitch);
        parser.setValue("swing_progress", target.getHandSwingProgress(transition));

        /* This frame's position, not the tick's: Fresh Animations' cow takes its vertical speed from
         * pos_y, and the tick position stepped twenty times a second, so the jump pose sawtoothed */
        double x = Lerps.lerp(target.getPrevX(), target.getX(), transition);
        double y = Lerps.lerp(target.getPrevY(), target.getY(), transition);
        double z = Lerps.lerp(target.getPrevZ(), target.getZ(), transition);

        parser.setValue("pos_x", x);
        parser.setValue("pos_y", y);
        parser.setValue("pos_z", z);

        /* The entity rotation in RADIANS, unlike head_yaw/head_pitch: packs unwrap rot_y jumps of 2π and
         * todeg() it themselves; in degrees a 2° turn read as 115° */
        parser.setValue("rot_x", Math.toRadians(pitch));
        parser.setValue("rot_y", Math.toRadians(yaw));

        int hurtTime = target.getHurtTimer();
        int deathTime = CemEntity.deathTime(target);

        parser.setValue("id", CemEntity.id(target));
        parser.setValue("hurt_time", hurtTime);
        parser.setValue("is_hurt", hurtTime > 0 ? 1 : 0);
        parser.setValue("death_time", deathTime);
        parser.setValue("is_alive", deathTime == 0 ? 1 : 0);

        parser.setValue("is_sneaking", target.isSneaking() ? 1 : 0);
        parser.setValue("is_sprinting", target.isSprinting() ? 1 : 0);
        parser.setValue("is_on_ground", target.isOnGround() ? 1 : 0);
        parser.setValue("is_in_water", target.isTouchingWater() ? 1 : 0);
        parser.setValue("is_swimming", target.isSwimming() ? 1 : 0);
        parser.setValue("is_gliding", target.isFallFlying() ? 1 : 0);
        parser.setValue("is_riding", CemEntity.riding(target) ? 1 : 0);
        parser.setValue("is_ridden", CemEntity.ridden(target) ? 1 : 0);
        parser.setValue("is_child", child ? 1 : 0);

        /* The form's own states lie over the entity's (see CemStatus); null for a caller with no form */
        parser.setValue("health", CemEntity.health(target) * (status == null ? 1F : status.health));
        parser.setValue("max_health", CemEntity.maxHealth(target));
        parser.setValue("is_burning", CemEntity.burning(target) || status != null && status.burning ? 1 : 0);
        parser.setValue("is_in_lava", CemEntity.inLava(target) || status != null && status.inLava ? 1 : 0);
        parser.setValue("is_climbing", CemEntity.climbing(target) || status != null && status.climbing ? 1 : 0);
        parser.setValue("is_crawling", CemEntity.crawling(target) || status != null && status.crawling ? 1 : 0);
        parser.setValue("is_sitting", CemEntity.sitting(target) || status != null && status.sitting ? 1 : 0);
        parser.setValue("is_tamed", CemEntity.tamed(target) || status != null && status.tamed ? 1 : 0);
        parser.setValue("is_aggressive", CemEntity.aggressive(target) || status != null && status.aggressive ? 1 : 0);
        parser.setValue("is_on_shoulder", status != null && status.onShoulder ? 1 : 0);

        hands(parser, target, transition);

        /* BBS has no CEM rules (.properties), so the matched rule is always the first */
        parser.setValue("rule_index", 0);

        /* OptiFine's "player" is the viewer: the nearest player is exactly that in singleplayer; with
         * no one around the entity looks at itself */
        Level world = target.getWorld();
        Player player = world == null ? null : world.getNearestPlayer(x, y, z, -1D, false);
        Vec3 at = player == null ? new Vec3(x, y, z) : player.getPosition(transition);

        parser.setValue("player_pos_x", at.x);
        parser.setValue("player_pos_y", at.y);
        parser.setValue("player_pos_z", at.z);
        parser.setValue("player_rot_x", Math.toRadians(player == null ? pitch : player.getViewXRot(transition)));
        parser.setValue("player_rot_y", Math.toRadians(player == null ? yaw : player.getViewYRot(transition)));
    }

    /**
     * CEM asks by arm, vanilla answers by hand: the two swap for a left-handed entity, and Fresh
     * Animations' player reads {@code is_right_handed} twenty times to decide which arm is which.
     */
    private static void hands(CemParser parser, IEntity target, float transition)
    {
        boolean right = CemEntity.rightHanded(target);
        boolean swinging = CemEntity.swinging(target, transition);
        boolean offSwing = swinging && CemEntity.swingingOffHand(target);
        boolean mainSwing = swinging && !offSwing;

        parser.setValue("is_right_handed", right ? 1 : 0);
        parser.setValue("is_using_item", target.isUsingItem() ? 1 : 0);
        parser.setValue("is_blocking", CemEntity.blocking(target) ? 1 : 0);
        parser.setValue("is_swinging_right_arm", arm(right, mainSwing, offSwing));
        parser.setValue("is_swinging_left_arm", arm(!right, mainSwing, offSwing));

        boolean main = held(target, EquipmentSlot.MAINHAND);
        boolean off = held(target, EquipmentSlot.OFFHAND);

        parser.setValue("is_holding_item_right", arm(right, main, off));
        parser.setValue("is_holding_item_left", arm(!right, main, off));
        parser.setValue("move_forward", CemEntity.forward(target));
        parser.setValue("move_strafing", CemEntity.sideways(target));
    }

    private static int arm(boolean isMainArm, boolean mainHand, boolean offHand)
    {
        return (isMainArm ? mainHand : offHand) ? 1 : 0;
    }

    private static boolean held(IEntity target, EquipmentSlot slot)
    {
        ItemStack stack = target.getEquipmentStack(slot);

        return stack != null && !stack.isEmpty();
    }
}
