package mchorse.bbs_mod.integrations.vitrail;

import dev.vitrail.uniform.UniformCatalog;
import dev.vitrail.uniform.UniformShape;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure uniform reads across every pass: consuming clip values happens once at the engine's frame advance. */
public final class VitrailCurveUniforms
{
    private static volatile Map<String, Float> frame = Map.of();
    private VitrailCurveUniforms() {}
    public static void reset() { frame = Map.of(); }

    public static void setValue(String name, float value)
    {
        if (VitrailCurveSource.requiresReload(name))
            throw new IllegalArgumentException("Shader option is a compile-time constant and requires a pack reload: " + name);
        ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(name);
        if (variable == null) throw new IllegalArgumentException("Shader curve is missing or unsupported in the active pack: " + name);
        validate(variable, value);
        variable.value = value;
    }

    public static void advance()
    {
        Map<String, Float> values = new LinkedHashMap<>();
        for (ShaderCurves.ShaderVariable variable : ShaderCurves.variableMap.values())
        {
            float value = variable.getValue();
            validate(variable, value);
            values.put(variable.uniformName, value);
        }
        frame = Map.copyOf(values);
    }

    public static UniformCatalog layer(UniformCatalog base)
    {
        UniformCatalog.Builder builder = UniformCatalog.builder(base);
        for (ShaderCurves.ShaderVariable variable : ShaderCurves.variableMap.values())
        {
            if (base.names().contains(variable.uniformName)) throw new IllegalArgumentException("Pack uniform collides with " + variable.uniformName);
            builder.add(variable.uniformName, variable.integer ? UniformShape.INT : UniformShape.FLOAT, (world, out) ->
            {
                float value = frame.getOrDefault(variable.uniformName, variable.defaultValue);
                if (variable.integer) out.set((int) value); else out.set(value);
            });
        }
        return builder.build();
    }

    private static void validate(ShaderCurves.ShaderVariable variable, float value)
    {
        if (!Float.isFinite(value) || (variable.integer && (value < Integer.MIN_VALUE || (double) value > Integer.MAX_VALUE)))
            throw new IllegalArgumentException("Shader curve has an invalid " + (variable.integer ? "integer" : "float") + " value: " + variable.name + "=" + value);
    }
}
