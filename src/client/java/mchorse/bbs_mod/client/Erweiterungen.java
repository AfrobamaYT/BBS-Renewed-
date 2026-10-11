package mchorse.bbs_mod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Haken fuer Begleit-Mods: Funktionen, die nicht im oeffentlichen BBS stecken, haengen sich
 * hier ein. Ohne Begleit-Mod bleiben alle Haken leer, und BBS verhaelt sich, als gaebe es sie
 * nicht -- kein Aufruf hier wirft und keiner aendert dann etwas.
 */
public final class Erweiterungen
{
    /** Eine externe Kamera-Vorschau, die das Weltbild in einen eigenen Puffer rendert. */
    public interface Vorschau
    {
        boolean rendert();

        boolean gehoert(UUID id);

        RenderTarget ziel();

        /** @return {@code {x, y, z, gier, neigung}} */
        double[] stellung();

        float blickwinkel();

        float rollen();

        void schliessen();
    }

    /** Ein externes Werkzeug, das Actors halten darf: Eingaben und Undo laufen dann ueber seine Sperre. */
    public interface Sperre
    {
        boolean eingabe(Supplier<Boolean> eingabe);

        void undoPruefen(Film film, IUndo<ValueGroup> undo);

        /** Steuert das Werkzeug gerade die Kamera? */
        boolean faehrtKamera();
    }

    /** Ein externer Render-Weg im Render-Reiter, zusaetzlich zu BBS' eigenem Export. */
    public interface Render
    {
        /** Die waehlbaren Engines nach "BBS", z. B. {@code {"Eevee", "Cycles"}}. */
        String[] engines();

        boolean laeuft();

        String status();

        void status(String status);

        /** @param engine Index in {@link #engines()} */
        void starten(UIFilmPanel panel, int engine);
    }

    public static volatile Vorschau vorschau;
    public static volatile Sperre sperre;
    public static volatile Render render;
    public static volatile Consumer<UIContext> konsole;

    public static boolean vorschauRendert()
    {
        Vorschau v = vorschau;

        return v != null && v.rendert();
    }

    public static boolean vorschauGehoert(UUID id)
    {
        Vorschau v = vorschau;

        return v != null && v.gehoert(id);
    }

    /** Eine UI-Eingabe ausfuehren -- ohne Sperre direkt. */
    public static boolean eingabe(Supplier<Boolean> eingabe)
    {
        Sperre s = sperre;

        return s == null ? eingabe.get() : s.eingabe(eingabe);
    }

    public static void undoPruefen(Film film, IUndo<ValueGroup> undo)
    {
        Sperre s = sperre;

        if (s != null)
        {
            s.undoPruefen(film, undo);
        }
    }

    public static boolean kameraExtern()
    {
        Sperre s = sperre;

        return s != null && s.faehrtKamera();
    }

    private Erweiterungen()
    {}
}
