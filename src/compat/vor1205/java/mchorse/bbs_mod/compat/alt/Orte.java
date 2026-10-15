package mchorse.bbs_mod.compat.alt;

import net.minecraft.resources.ResourceLocation;

/** vor1205: ResourceLocation hat noch keine Fabriken, nur Konstruktoren. */
public final class Orte
{
    private Orte()
    {}

    public static ResourceLocation von(String namensraum, String pfad)
    {
        return new ResourceLocation(namensraum, pfad);
    }

    public static ResourceLocation standard(String pfad)
    {
        return new ResourceLocation(pfad);
    }

    public static ResourceLocation lesen(String text)
    {
        return new ResourceLocation(text);
    }
}
