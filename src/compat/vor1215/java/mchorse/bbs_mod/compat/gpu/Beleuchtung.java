package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.platform.Lighting;

/**
 * 1.21.11s Lighting-Objekt (GameRenderer.lighting().setupFor(Entry)) fuer die Schicht vor 1.21.5,
 * wo Lighting nur statische Voreinstellungen kennt. Die Regel Lighting.Entry -> Beleuchtung.Entry
 * (stonecutter-regeln-1214) lenkt die Eintraege hierher.
 */
public final class Beleuchtung
{
    public static final Beleuchtung INSTANZ = new Beleuchtung();
    /** Lighting.UBO_SIZE von 1.21.6+: zwei vec3 nach std140. */
    public static final int UBO_SIZE = 32;

    private Beleuchtung()
    {}

    public void setupFor(Entry eintrag)
    {
        switch (eintrag)
        {
            case LEVEL -> mchorse.bbs_mod.compat.alt.AltApi.weltlicht();
            case ITEMS_FLAT -> Lighting.setupForFlatItems();
            case ITEMS_3D -> Lighting.setupFor3DItems();
            case ENTITY_IN_UI, PLAYER_SKIN -> Lighting.setupForEntityInInventory();
        }
    }

    public enum Entry
    {
        LEVEL, ITEMS_FLAT, ITEMS_3D, ENTITY_IN_UI, PLAYER_SKIN
    }
}
