package mchorse.bbs_mod.utils;

import java.lang.reflect.Field;

/**
 * Reflexion auf Minecraft-Felder in jeder Version: Dev-Laeufe und 26.x tragen Mojang-Namen,
 * Release-Jars vor 26.1 laufen im intermediary-Namensraum (field_NNNN, ueber Versionen stabil).
 * Ein fester Mojang-Name fand dort nichts (ItemInHandRenderer.mainHandItem, echte 1.21.4-Instanz,
 * 2026-10-14). Geprueft von tools/multiversion/jar-pruefung.py.
 */
public final class LaufzeitFeld
{
    private LaufzeitFeld()
    {}

    public static Field holen(Class<?> besitzer, String mojang, String intermediary) throws NoSuchFieldException
    {
        Field feld;

        try
        {
            feld = besitzer.getDeclaredField(mojang);
        }
        catch (NoSuchFieldException mojangFehlt)
        {
            try
            {
                feld = besitzer.getDeclaredField(intermediary);
            }
            catch (NoSuchFieldException beideFehlen)
            {
                beideFehlen.addSuppressed(mojangFehlt);
                throw beideFehlen;
            }
        }

        feld.setAccessible(true);

        return feld;
    }
}
