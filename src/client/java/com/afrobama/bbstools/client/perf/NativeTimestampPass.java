package com.afrobama.bbstools.client.perf;

import com.mojang.blaze3d.systems.RenderPass;

import java.lang.reflect.Field;

/** Inspect Vitrail's existing pass without calling real(), which can open a new pass. */
final class NativeTimestampPass
{
    private NativeTimestampPass() {}

    private record ProxyFields(Field real, Field closed) {}

    private static final ClassValue<ProxyFields> FIELDS = new ClassValue<>()
    {
        @Override
        protected ProxyFields computeValue(Class<?> type)
        {
            try
            {
                Field real = type.getDeclaredField("real");
                Field closed = type.getDeclaredField("closed");
                if (real.getType() != RenderPass.class || closed.getType() != boolean.class)
                    throw new IllegalStateException("Vitrail timestamp pass fields changed type");
                real.setAccessible(true);
                closed.setAccessible(true);
                return new ProxyFields(real, closed);
            }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Vitrail timestamp pass contract changed", error); }
        }
    };

    static RenderPass real(RenderPass pass)
    {
        if (!pass.getClass().getName().equals("dev.vitrail.render.LevelPass")) return pass;
        ProxyFields fields = FIELDS.get(pass.getClass());
        try
        {
            if (fields.closed().getBoolean(pass)) throw new IllegalStateException("Vitrail timestamp pass is closed");
            Object real = fields.real().get(pass);
            if (!(real instanceof RenderPass nativePass)) throw new IllegalStateException("Vitrail timestamp pass is suspended");
            return nativePass;
        }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot inspect actual Vitrail timestamp pass", error); }
    }
}
