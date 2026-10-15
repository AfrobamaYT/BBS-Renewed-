package mchorse.bbs_mod.compat.alt;

import net.minecraft.resources.ResourceLocation;

/** ResourceLocation-Fabriken fuer die Nachruestung vor 1.21.5 (vor1205 ersetzt sie durch den Konstruktor). */
public final class Orte
{
    private Orte()
    {}

    public static ResourceLocation von(String namensraum, String pfad)
    {
        return ResourceLocation.fromNamespaceAndPath(namensraum, pfad);
    }

    public static ResourceLocation standard(String pfad)
    {
        return ResourceLocation.withDefaultNamespace(pfad);
    }

    public static ResourceLocation lesen(String text)
    {
        return ResourceLocation.parse(text);
    }
}
