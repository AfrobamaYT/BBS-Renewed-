package mchorse.bbs_mod.utils.interps;

import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public enum Interps implements IInterp
{
    LINEAR("linear", GLFW.GLFW_KEY_L)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.lerp(context.a, context.b, context.x);
        }
    },
    CONST("constant", GLFW.GLFW_KEY_T)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return context.a;
        }
    },
    STEP("step", GLFW.GLFW_KEY_P)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double steps = Math.floor(Math.min(1, context.v1));

            if (steps <= 1)
            {
                return context.a;
            }

            double x = Math.floor(context.x * steps) / steps;

            return Lerps.lerp(context.a, context.b, x);
        }
    },
    QUAD_IN("quad_in", GLFW.GLFW_KEY_Q)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return context.a + (context.b - context.a) * context.x * context.x;
        }
    },
    QUAD_OUT("quad_out", GLFW.GLFW_KEY_Q)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return context.a - (context.b - context.a) * context.x * (context.x - 2);
        }
    },
    QUAD_INOUT("quad_inout", GLFW.GLFW_KEY_Q)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            x *= 2;

            if (x < 1F) return context.a + (context.b - context.a) / 2 * x * x;

            x -= 1;

            return context.a - (context.b - context.a) / 2 * (x * (x - 2) - 1);
        }
    },
    CUBIC_IN("cubic_in", GLFW.GLFW_KEY_C)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            return context.a + (context.b - context.a) * x * x * x;
        }
    },
    CUBIC_OUT("cubic_out", GLFW.GLFW_KEY_C)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            x -= 1;

            return context.a + (context.b - context.a) * (x * x * x + 1);
        }
    },
    CUBIC_INOUT("cubic_inout", GLFW.GLFW_KEY_C)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            x *= 2;

            if (x < 1F) return context.a + (context.b - context.a) / 2 * x * x * x;

            x -= 2;

            return context.a + (context.b - context.a) / 2 * (x * x * x + 2);
        }
    },
    EXP_IN("exp_in", GLFW.GLFW_KEY_E)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            return context.a + (context.b - context.a) * Math.pow(2, 10 * (x - 1));
        }
    },
    EXP_OUT("exp_out", GLFW.GLFW_KEY_E)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            return context.a + (context.b - context.a) * remapExp(-Math.pow(2, -10 * x) + 1);
        }
    },
    EXP_INOUT("exp_inout", GLFW.GLFW_KEY_E)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            if (x == 0) return context.a;
            if (x == 1) return context.b;

            x *= 2;

            if (x < 1F) return context.a + (context.b - context.a) / 2 * Math.pow(2, 10 * (x - 1));

            x -= 1;

            return context.a + (context.b - context.a) / 2 * (-remapExp(Math.pow(2, -10 * x)) + 2);
        }
    },
    /* Following interpolations below were copied from: https://easings.net/ */
    BACK_IN("back_in", GLFW.GLFW_KEY_B)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double c1 = 1.70158D;
            final double c3 = c1 + 1;

            return Lerps.lerp(context.a, context.b, c3 * x * x * x - c1 * x * x);
        }
    },
    BACK_OUT("back_out", GLFW.GLFW_KEY_B)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double c1 = 1.70158D;
            final double c3 = c1 + 1;

            return Lerps.lerp(context.a, context.b, 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
        }
    },
    BACK_INOUT("back_inout", GLFW.GLFW_KEY_B)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double c1 = 1.70158D;
            final double c2 = c1 * 1.525D;

            double factor = x < 0.5
                ? (Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2)) / 2
                : (Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    ELASTIC_IN("elastic_in", GLFW.GLFW_KEY_S)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double c4 = (2 * Math.PI) / 3;

            double factor = x == 0 ? 0 :
                (x == 1 ? 1 : -Math.pow(2, 10 * x - 10) * Math.sin((x * 10 - 10.75) * c4));

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    ELASTIC_OUT("elastic_out", GLFW.GLFW_KEY_S)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double c4 = (2 * Math.PI) / 3;

            double factor = x == 0 ? 0 :
                (x == 1 ? 1 : Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * c4) + 1);

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    ELASTIC_INOUT("elastic_inout", GLFW.GLFW_KEY_S)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double c5 = (2 * Math.PI) / 4.5;

            double sin = Math.sin((20 * x - 11.125) * c5);
            double factor = x == 0 ? 0 : (x == 1 ? 1 :
                (x < 0.5
                    ? -(Math.pow(2, 20 * x - 10) * sin) / 2
                    : (Math.pow(2, -20 * x + 10) * sin) / 2 + 1)
                );

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    BOUNCE_IN("bounce_in", GLFW.GLFW_KEY_O)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;

            return Lerps.lerp(context.a, context.b, 1 - BOUNCE_OUT.interpolate(0, 1, 1 - x));
        }
    },
    BOUNCE_OUT("bounce_out", GLFW.GLFW_KEY_O)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            final double n1 = 7.5625;
            final double d1 = 2.75;
            double factor;

            if (x < 1 / d1)
            {
                factor = n1 * x * x;
            }
            else if (x < 2 / d1)
            {
                factor = n1 * (x -= 1.5 / d1) * x + 0.75;
            }
            else if (x < 2.5 / d1)
            {
                factor = n1 * (x -= 2.25 / d1) * x + 0.9375;
            }
            else
            {
                factor = n1 * (x -= 2.625 / d1) * x + 0.984375;
            }

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    BOUNCE_INOUT("bounce_inout", GLFW.GLFW_KEY_O)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = x < 0.5
                ? (1 - BOUNCE_OUT.interpolate(0, 1, 1 - 2 * x)) / 2
                : (1 + BOUNCE_OUT.interpolate(0, 1, 2 * x - 1)) / 2;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    SINE_IN("sine_in", GLFW.GLFW_KEY_I)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = 1 - Math.cos((x * Math.PI) / 2);

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    SINE_OUT("sine_out", GLFW.GLFW_KEY_I)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = Math.sin((x * Math.PI) / 2);

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    SINE_INOUT("sine_inout", GLFW.GLFW_KEY_I)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = -(Math.cos(Math.PI * x) - 1) / 2;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    QUART_IN("quart_in", GLFW.GLFW_KEY_U)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = x * x * x * x;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    QUART_OUT("quart_out", GLFW.GLFW_KEY_U)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double factor = 1 - Math.pow(1 - context.x, 4);

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    QUART_INOUT("quart_inout", GLFW.GLFW_KEY_U)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    QUINT_IN("quint_in", GLFW.GLFW_KEY_N)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = x * x * x * x * x;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    QUINT_OUT("quint_out", GLFW.GLFW_KEY_N)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double factor = 1 - Math.pow(1 - context.x, 5);

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    QUINT_INOUT("quint_inout", GLFW.GLFW_KEY_N)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = context.x;
            double factor = x < 0.5 ? 16 * x * x * x * x * x : 1 - Math.pow(-2 * x + 2, 5) / 2;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    CIRCLE_IN("circle_in", GLFW.GLFW_KEY_R)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = MathUtils.clamp(context.x, 0, 1);

            double factor = 1 - (float) Math.sqrt(1 - Math.pow(x, 2));

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    CIRCLE_OUT("circle_out", GLFW.GLFW_KEY_R)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = MathUtils.clamp(context.x, 0, 1);
            double factor = Math.sqrt(1 - Math.pow(x - 1, 2));

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    CIRCLE_INOUT("circle_inout", GLFW.GLFW_KEY_R)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double x = MathUtils.clamp(context.x, 0, 1);
            double factor = x < 0.5
                ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2
                : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2;

            return Lerps.lerp(context.a, context.b, factor);
        }
    },
    CIRCULAR("circle", GLFW.GLFW_KEY_P)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.cubicHermite(context.a, context.a, context.b, context.b, context.x);
        }
    },
    CUBIC("cubic", GLFW.GLFW_KEY_K)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.cubic(context.v1, context.a, context.b, context.v2, context.x);
        }
    },
    HERMITE("hermite", GLFW.GLFW_KEY_H)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            return Lerps.cubicHermite(context.v1, context.a, context.b, context.v2, context.x);
        }
    },
    BEZIER("bezier", GLFW.GLFW_KEY_Z)
    {
        @Override
        public double interpolate(InterpContext context)
        {
            double a = context.a;
            double b = context.b;
            double x = context.x;

            return Lerps.cubicHermite(a, a, b, b, x);
        }
    };

    public static final Map<String, IInterp> MAP = new HashMap<>();

    public final String key;
    public final int keybind;

    static
    {
        for (Interps value : Interps.values())
        {
            MAP.put(((IInterp) value).getKey(), value);
        }
    }

    public static IInterp get(String name)
    {
        return MAP.getOrDefault(name, LINEAR);
    }

    private static double remapExp(double factor)
    {
        return (factor - 0.001D) * (1D / 0.999D);
    }

    private Interps(String key, int keybind)
    {
        this.key = key;
        this.keybind = keybind;
    }

    @Override
    public String getKey()
    {
        return this.key;
    }

    @Override
    public int getKeyCode()
    {
        return this.keybind;
    }
}