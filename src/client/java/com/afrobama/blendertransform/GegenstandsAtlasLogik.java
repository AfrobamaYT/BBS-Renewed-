package com.afrobama.blendertransform;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.graphics.Zeichenbereich;
import mchorse.bbs_mod.graphics.Zeichenziel;

/**
 * {@code GuiItemAtlas.drawToSlot} um den Sonderzeichner-Aufruf herum: BBS' Sofortzeichnung
 * geht in den Atlas-Slot statt ins Hauptziel. Der Ausschnitt rechnet wie Vanillas Loeschen
 * und Schere im selben Rumpf: Ursprung unten links, also {@code groesse - zeile*slot - slot}.
 */
public final class GegenstandsAtlasLogik
{
    private static GpuTextureView alteFarbe;
    private static GpuTextureView alteTiefe;
    private static mchorse.bbs_mod.compat.alt.RenderArea alterBereich;

    public static void vorDemSlot(GpuTextureView farbe, GpuTextureView tiefe, int spalte, int zeile, int slot, int groesse)
    {
        alteFarbe = Zeichenziel.farbe();
        alteTiefe = Zeichenziel.tiefe();
        alterBereich = Zeichenbereich.aktiv();
        Zeichenziel.setzen(farbe, tiefe);
        Zeichenbereich.setzen(spalte * slot, groesse - zeile * slot - slot, slot, slot);
    }

    public static void nachDemSlot()
    {
        Zeichenziel.setzen(alteFarbe, alteTiefe);

        if (alterBereich == null)
        {
            Zeichenbereich.freigeben();
        }
        else
        {
            Zeichenbereich.setzen(alterBereich.x(), alterBereich.y(), alterBereich.width(), alterBereich.height());
        }

        alteFarbe = null;
        alteTiefe = null;
        alterBereich = null;
    }

    private GegenstandsAtlasLogik()
    {}
}
