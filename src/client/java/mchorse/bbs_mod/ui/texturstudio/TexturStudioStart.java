package mchorse.bbs_mod.ui.texturstudio;

import net.fabricmc.api.ClientModInitializer;

/**
 * Meldet die Bruecke des Texture Studios an, sobald Rinku startet -- bevor Rinku seinen Vorrat an
 * vorab erzeugten Browsern fuellt. Ein Browser aus diesem Vorrat kennt nur Bruecken, die es bei
 * seiner Entstehung schon gab: angemeldet erst beim ersten Oeffnen, fehlte im ersten Studio nach
 * dem Start {@code window.cefQuery}, und Photopea bekam die Textur nie (gemessen 2026-10-14).
 *
 * <p>Ohne Rinku tut diese Klasse nichts und laedt keine Rinku-Klasse.</p>
 */
public final class TexturStudioStart implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        if (TexturStudio.verfuegbar())
        {
            TexturStudioKanal.frueh();
            mchorse.bbs_mod.ui.hilfe.HilfeKanal.frueh();
        }
    }
}
