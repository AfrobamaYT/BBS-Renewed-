package mchorse.bbs_mod.compat.gpu;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Fabrics RenderStateDataKey/FabricRenderState (ab fabric-rendering-v1 fuer 1.21.9) fuer die
 * Versionen davor: Zusatzdaten an einem Render-Zustand, solange der Zustand lebt. Die Regeln
 * in stonecutter-regeln-1214 lenken create/setData/getData hierher.
 */
public final class ZustandsSchluessel<T>
{
    private static final Map<Object, Map<ZustandsSchluessel<?>, Object>> DATEN = new WeakHashMap<>();

    private final Supplier<String> name;

    private ZustandsSchluessel(Supplier<String> name)
    {
        this.name = name;
    }

    public static <T> ZustandsSchluessel<T> create(Supplier<String> name)
    {
        return new ZustandsSchluessel<>(name);
    }

    public static <T> void setData(Object zustand, ZustandsSchluessel<T> schluessel, T wert)
    {
        DATEN.computeIfAbsent(zustand, z -> new IdentityHashMap<>()).put(schluessel, wert);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(Object zustand, ZustandsSchluessel<T> schluessel)
    {
        Map<ZustandsSchluessel<?>, Object> daten = DATEN.get(zustand);

        return daten == null ? null : (T) daten.get(schluessel);
    }

    @Override
    public String toString()
    {
        return this.name.get();
    }
}
