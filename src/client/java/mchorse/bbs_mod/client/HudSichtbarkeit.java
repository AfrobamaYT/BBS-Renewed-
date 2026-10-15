package mchorse.bbs_mod.client;

import net.minecraft.client.Minecraft;

/**
 * Blendet das Vanilla-HUD aus und wieder ein.
 *
 * <h2>Warum es diese Klasse gibt</h2>
 *
 * <p>Bis 1.21.1 gab es den Schalter direkt auf {@code Minecraft.gui}, und BBS hat ihn an acht
 * Stellen benutzt: beim Aufnehmen, in der Filmvorschau und im Tracker-Clip. In 26.2 ist
 * die alte {@code Gui} in zwei Klassen zerfallen — {@code Gui} verwaltet nur noch
 * Bildschirme und Overlays, das eigentliche HUD steckt in {@code Gui.hud}. Und der
 * Schalter dort ist nicht mehr setzbar: {@code Hud} bietet {@code isHidden()} zum Lesen
 * und {@code toggle()} zum Umschalten, aber kein {@code setHidden}.
 *
 * <p>Auf einen Zielwert umzuschalten heisst darum: erst lesen, dann nur umschalten, wenn
 * der Wert abweicht. Das ist genau das, was BBS braucht (jede Aufnahme merkt sich den
 * vorherigen Zustand und stellt ihn danach wieder her) — aber es ist auch genau die Art
 * Zweizeiler, die an acht Stellen achtmal leicht anders geschrieben wird. Darum eine
 * Stelle.
 *
 * <p>⚠️ Kein Mixin auf das private Feld. Ein Accessor waere kuerzer, wuerde aber an
 * {@code toggle()} vorbei schreiben; sollte Mojang dort spaeter etwas anhaengen
 * (Animation, Ereignis, Zuruecksetzen von Titeln), liefe BBS still daran vorbei.
 */
public final class HudSichtbarkeit
{
    public static boolean istVersteckt()
    {
        return Minecraft.getInstance().gui.hud.isHidden();
    }

    public static void verstecken(boolean versteckt)
    {
        if (istVersteckt() != versteckt)
        {
            Minecraft.getInstance().gui.hud.toggle();
        }
    }

    private HudSichtbarkeit()
    {}
}
