package mchorse.bbs_mod.ui.film;

import org.joml.Vector2i;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;

/**
 * Der Ausschnitt, der spaeter im Video landet — als Rahmen ueber dem vollflaechigen Bild.
 *
 * <p>⛔ WOFUER: Die Vorschau fuellt die Flaeche (Blenders „User Perspective"), und damit war
 * nicht mehr zu sehen, WELCHER Teil davon exportiert wird. Der einzige Ausweg war der
 * Vorschau-Modus „Export" — der zeichnet das Bild im Exportformat mit Raendern und rendert
 * dafuer in voller Exportaufloesung. Gemessen 2026-09-10 an einem 3840x2160-Film:
 * Textur 3840x2160 fuer eine Anzeigeflaeche von 952x536 Pixeln (16-fache Last), und 30 %
 * der Editorflaeche blieben schwarz. Nutzer: „im dashboard ist nicht der viewport gestreckt
 * wie in blender … die aufloesung ist extra fuer den export da".</p>
 *
 * <p>Mit diesem Rahmen ist der Ausschnitt auch im flaechenfuellenden Modus sichtbar —
 * genau wie Blenders Kamerarahmen samt Passepartout.</p>
 */
public final class KameraRahmen
{
    /** Wie stark der Bereich ausserhalb des Ausschnitts abgedunkelt wird. */
    private static final float SCHLEIER = 0.45F;

    private KameraRahmen()
    {}

    public static void zeichnen(UIFilmPanel panel, UIContext context, Area area)
    {
        /* Im Export-Modus IST das Bild schon der Ausschnitt — ein Rahmen darum waere doppelt. */
        if (BBSSettings.editorPreviewSizeMode.get() == 0)
        {
            return;
        }

        /* ⛔ AN DEN VORHANDENEN SCHALTER, nicht an einen neuen (2026-09-11). `camera_preview`
         * schaltet schon die Kameragrenzen im Editor; der Rahmen ist dieselbe Aussage in der
         * Vorschau. Nutzer nach dem ersten Einbau: „der weisse rand sollte nicht da sein" —
         * er will die gestreckte Flaeche ohne Zier, und das muss ein Schalter sein, kein
         * Ausbau: ohne Rahmen zeigt der flaechenfuellende Modus nicht mehr, was im Video landet. */
        if (!BBSSettings.recordingCameraPreview.get())
        {
            return;
        }

        int videoW = BBSSettings.videoWidth.get();
        int videoH = BBSSettings.videoHeight.get();

        if (videoW <= 0 || videoH <= 0 || area.w <= 0 || area.h <= 0)
        {
            return;
        }

        Vector2i groesse = Vectors.resize(videoW / (float) videoH, area.w, area.h);
        int x = area.mx() - groesse.x / 2;
        int y = area.my() - groesse.y / 2;
        int ex = x + groesse.x;
        int ey = y + groesse.y;

        /* Deckungsgleich (gleiches Seitenverhaeltnis): dann gibt es nichts zu zeigen. */
        if (x <= area.x && y <= area.y)
        {
            return;
        }

        int schleier = Colors.setA(0xFF000000, SCHLEIER);

        if (y > area.y) context.batcher.box(area.x, area.y, area.ex(), y, schleier);
        if (ey < area.ey()) context.batcher.box(area.x, ey, area.ex(), area.ey(), schleier);
        if (x > area.x) context.batcher.box(area.x, y, x, ey, schleier);
        if (ex < area.ex()) context.batcher.box(ex, y, area.ex(), ey, schleier);

        int linie = Colors.setA(Colors.WHITE, 0.5F);

        context.batcher.box(x, y, ex, y + 1, linie);
        context.batcher.box(x, ey - 1, ex, ey, linie);
        context.batcher.box(x, y, x + 1, ey, linie);
        context.batcher.box(ex - 1, y, ex, ey, linie);
    }
}
