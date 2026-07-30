// Coded by Afrobama/Claude — Placeable blinding light source event.
// Per user spec (2026-05-14): "richtig helle lichtquelle die man
// platziert auf der welt und wenn man näher kommt ist man komplett
// geblendet vom licht bis man weiter geht".
//
// World position + dual-radius blinding falloff:
//   - inner radius: fully blinded inside this distance
//   - outer radius: smooth fade to scene
//   - intensity: master strength 0..1
//
// Lifecycle: persistent until user disables (no auto-deactivate).
// Multiple light sources not supported yet (single LIGHT_POS uniform);
// future iter: array of lights via instanced rendering or single shader pass.
package com.afrobama.bbstools.client.veil;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MASLightSourceEvent
{
    private static final Logger LOG = LoggerFactory.getLogger("mas/lightsource");

    /** Default radii in blocks. Inner = full blind, outer = back to normal. */
    private static final float DEFAULT_INNER_RADIUS = 4.0F;
    private static final float DEFAULT_OUTER_RADIUS = 14.0F;
    private static final float DEFAULT_INTENSITY = 1.0F;

    private static volatile Vec3 position = Vec3.ZERO;
    private static volatile float innerRadius = DEFAULT_INNER_RADIUS;
    private static volatile float outerRadius = DEFAULT_OUTER_RADIUS;
    private static volatile float intensity = DEFAULT_INTENSITY;
    private static volatile boolean active = false;

    private MASLightSourceEvent() {}

    public static void trigger(Vec3 pos)
    {
        position = pos;
        active = true;
        LOG.info("Light source placed at ({}, {}, {}) — inner {}b / outer {}b / intensity {}",
            pos.x, pos.y, pos.z, innerRadius, outerRadius, intensity);
    }

    public static Vec3 triggerAtPlayerTarget()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        /* Place 5 blocks ahead in player look direction, slightly up. */
        Vec3 look = mc.player.getLookAngle();
        Vec3 target = new Vec3(
            mc.player.getX() + look.x * 5.0,
            mc.player.getY() + 1.5 + look.y * 5.0,
            mc.player.getZ() + look.z * 5.0);
        trigger(target);
        return target;
    }

    public static void deactivate()
    {
        active = false;
        LOG.info("Light source deactivated");
    }

    public static boolean isActive() { return active; }
    public static Vec3 getPosition() { return position; }
    public static float getInnerRadius() { return innerRadius; }
    public static float getOuterRadius() { return outerRadius; }
    public static float getIntensity() { return intensity; }

    public static void setIntensity(float i)
    {
        intensity = Math.max(0.0F, Math.min(1.0F, i));
        LOG.info("Light intensity set to {}", intensity);
    }

    public static void setRadii(float inner, float outer)
    {
        innerRadius = Math.max(0.5F, inner);
        outerRadius = Math.max(innerRadius + 1.0F, outer);
        LOG.info("Light radii set: inner={} outer={}", innerRadius, outerRadius);
    }

}
