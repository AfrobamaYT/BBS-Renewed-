package net.minecraft.core.component;

/** vor1205: Data Components kamen mit 1.20.5; hier nur ein Schluessel fuer die NBT-Abbildung. */
public final class DataComponentType<T>
{
    public final String name;

    public DataComponentType(String name)
    {
        this.name = name;
    }
}
