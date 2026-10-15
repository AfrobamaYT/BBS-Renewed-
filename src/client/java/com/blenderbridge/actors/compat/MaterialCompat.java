/* Coded by Afrobama/Claude */
package com.blenderbridge.actors.compat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective access to the BBS material channels that only newer BBS-CML builds carry.
 *
 * WHY THIS EXISTS: BBSTools-Fabric compiles against a BBS-CML jar (compileOnly) that predates
 * the glow / paint / illusion material features. Measured against
 * bbs-cml-edition-2.0-pre-alpha-2: {@code forms.forms.utils.GlowSettings}, {@code PaintSettings}
 * and {@code Illusion} do not exist, {@code PoseTransform} has only {@code color} (no
 * {@code paintColor} / {@code glowingColor} / {@code glowIntensity}), and {@code Form} has no
 * {@code illusion} field.
 *
 * The exporters that read those channels came from the cml-fresh tree, where they compile
 * directly. Deleting them here would silently drop the channels from the Blender material sync
 * and they would have to be rewritten when the BBS base is updated. Instead every access goes
 * through this class: it resolves the class/field once, caches the handle, and reports
 * "channel absent" when the running BBS has no such feature. The exporters already treat an
 * absent channel as "emit nothing", so the payload stays byte-identical for actors without it —
 * exactly what happens today, and the full channel comes back for free once the base carries it.
 *
 * Everything here is null-safe by contract: a missing class, a missing field, or a value of the
 * wrong shape all resolve to {@code null} / {@code 0F}, never an exception.
 */
public final class MaterialCompat
{
    /** Fully-qualified names of the material value types, resolved lazily. */
    private static final String GLOW_CLASS = "mchorse.bbs_mod.forms.forms.utils.GlowSettings";
    private static final String PAINT_CLASS = "mchorse.bbs_mod.forms.forms.utils.PaintSettings";
    private static final String ILLUSION_CLASS = "mchorse.bbs_mod.forms.forms.utils.Illusion";

    /** name -> resolved class (or null when this BBS build lacks it). Populated once per name. */
    private static final Map<String, Object> CLASSES = new ConcurrentHashMap<>();
    /** "owner#field" -> resolved Field (or null). Populated once per key. */
    private static final Map<String, Object> FIELDS = new ConcurrentHashMap<>();

    /** Sentinel stored in the caches for "looked up, does not exist" (maps reject null values). */
    private static final Object ABSENT = new Object();

    private MaterialCompat() {}

    /* ---------------------------------------------------------------- type checks */

    /** True when {@code value} is a GlowSettings of the running BBS build. */
    public static boolean isGlow(Object value)
    {
        return isInstance(value, GLOW_CLASS);
    }

    /** True when {@code value} is a PaintSettings of the running BBS build. */
    public static boolean isPaint(Object value)
    {
        return isInstance(value, PAINT_CLASS);
    }

    /** True when {@code value} is an Illusion of the running BBS build. */
    public static boolean isIllusion(Object value)
    {
        return isInstance(value, ILLUSION_CLASS);
    }

    private static boolean isInstance(Object value, String className)
    {
        if (value == null)
        {
            return false;
        }

        Class<?> type = lookupClass(className);

        return type != null && type.isInstance(value);
    }

    /* ------------------------------------------------------------- field readers */

    /**
     * Reads a float field off any object, returning 0F when the field does not exist.
     *
     * Used for the scalar sub-curves the bridge ships (glow r/g/b/intensity, paint r/g/b/a,
     * per-bone glowIntensity). 0F is the same value the addon side treats as "no contribution",
     * so an absent channel reads as neutral rather than as a broken curve.
     */
    public static float readFloat(Object owner, String fieldName)
    {
        Object value = readObject(owner, fieldName);

        return value instanceof Number number ? number.floatValue() : 0F;
    }

    /** Reads an int field, returning 0 when the field does not exist. */
    public static int readInt(Object owner, String fieldName)
    {
        Object value = readObject(owner, fieldName);

        return value instanceof Number number ? number.intValue() : 0;
    }

    /** Reads a boolean field, returning false when the field does not exist. */
    public static boolean readBoolean(Object owner, String fieldName)
    {
        Object value = readObject(owner, fieldName);

        return value instanceof Boolean flag && flag;
    }

    /**
     * Calls {@code get()} on a BBS value wrapper (ValueIllusion and friends), returning null when
     * the object is null or carries no such method.
     */
    public static Object invokeGet(Object wrapper)
    {
        if (wrapper == null)
        {
            return null;
        }

        try
        {
            return wrapper.getClass().getMethod("get").invoke(wrapper);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Reads an object field off any object, returning null when the field does not exist.
     *
     * Used for the composite fields ({@code PoseTransform.paintColor} / {@code glowingColor},
     * which are Color instances, and {@code Form.illusion}, which is a value wrapper).
     */
    public static Object readObject(Object owner, String fieldName)
    {
        if (owner == null)
        {
            return null;
        }

        Field field = lookupField(owner.getClass(), fieldName);

        if (field == null)
        {
            return null;
        }

        try
        {
            return field.get(owner);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * True when {@code owner} actually has {@code fieldName}.
     *
     * Callers use this to skip a whole channel block instead of emitting a curve of zeroes:
     * "no paintColor on PoseTransform" must produce no paint sub-curves at all.
     */
    public static boolean hasField(Object owner, String fieldName)
    {
        return owner != null && lookupField(owner.getClass(), fieldName) != null;
    }

    /* ------------------------------------------------------------------- lookups */

    private static Class<?> lookupClass(String className)
    {
        Object cached = CLASSES.computeIfAbsent(className, name ->
        {
            try
            {
                return Class.forName(name, false, MaterialCompat.class.getClassLoader());
            }
            catch (Throwable t)
            {
                return ABSENT;
            }
        });

        return cached == ABSENT ? null : (Class<?>) cached;
    }

    /**
     * Resolves a field by walking the class hierarchy — BBS puts some of these on superclasses,
     * and getDeclaredField only sees the exact class.
     */
    private static Field lookupField(Class<?> owner, String fieldName)
    {
        Object cached = FIELDS.computeIfAbsent(owner.getName() + "#" + fieldName, key ->
        {
            for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass())
            {
                try
                {
                    Field field = type.getDeclaredField(fieldName);

                    field.setAccessible(true);

                    return field;
                }
                catch (NoSuchFieldException ignored)
                {
                    /* keep walking up */
                }
                catch (Exception e)
                {
                    return ABSENT;
                }
            }

            return ABSENT;
        });

        return cached == ABSENT ? null : (Field) cached;
    }
}
