package mchorse.bbs_mod.ui.film.regie;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.regie.Regie;
import mchorse.bbs_mod.film.regie.RegieMarker;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

/**
 * Was der Regie-Reiter am Film aendert. Neue Marker und der Startpunkt landen dort, wohin die
 * Editorkamera blickt (erster Block auf dem Blickstrahl) — wie „+ Ort / + Blick / Start" im
 * Design. Jede Bedienung ist ein eigener Undo-Schritt, wie beim alten Timers-Reiter.
 */
final class RegieBedienung
{
    /** Neues Zeitfenster: ab Abspielkopf, 4 s lang (die Beispiele im Design sind 4 s). */
    static final int FENSTER = 80;

    private final UIFilmPanel panel;
    /** UUID des gewaehlten Markers, leer = keiner. */
    String gewaehlt = "";
    /** Letzte abgelehnte Bedienung im Klartext, leer = keine. */
    String fehler = "";
    /** Wird nach jeder Aenderung gerufen (der Reiter baut dann neu auf). */
    Runnable geaendert = () -> {};

    RegieBedienung(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    Regie regie()
    {
        Film film = this.panel.getData();

        return film == null ? null : film.regie;
    }

    RegieMarker gewaehlt()
    {
        Regie regie = this.regie();

        return regie == null ? null : regie.marker.mitUuid(this.gewaehlt);
    }

    /** Erster Block auf dem Blickstrahl der Editorkamera, oder null (dann laut gemeldet). */
    private BlockHitResult ziel()
    {
        Camera kamera = this.panel.getCamera();
        BlockHitResult treffer = RayTracing.rayTrace(Minecraft.getInstance().level, kamera, 256D);

        if (treffer.getType() == HitResult.Type.MISS)
        {
            this.melden("No block in view — aim the camera at the ground (third-person views look straight ahead; use Orbit or Free).");

            return null;
        }

        return treffer;
    }

    /** Ein Blick-Marker auf dem Boden saesse zu tief: auf Augenhoehe heben. */
    static double hoehe(boolean ort, BlockHitResult treffer)
    {
        return !ort && treffer.getDirection() == Direction.UP ? 1.6D : 0D;
    }

    void neu(int art)
    {
        Regie regie = this.regie();
        BlockHitResult treffer = regie == null ? null : this.ziel();

        if (treffer == null)
        {
            return;
        }

        int anfang = this.panel.getCursor();
        String name = (art == RegieMarker.ORT ? "Location " : "Look ") + (regie.marker.getList().size() + 1);

        this.zaun();
        this.gewaehlt = regie.marker.neu(art, name, anfang, anfang + FENSTER, treffer.getLocation().x,
            treffer.getLocation().y + hoehe(art == RegieMarker.ORT, treffer), treffer.getLocation().z).uuid.get();
        this.fertig();
    }

    void startHier()
    {
        Regie regie = this.regie();
        BlockHitResult treffer = regie == null ? null : this.ziel();

        if (treffer == null)
        {
            return;
        }

        float gier = MathUtils.toDeg(this.panel.getCamera().rotation.y);

        this.aendern(regie, (r) ->
        {
            r.startGesetzt.set(true);
            r.startX.set(treffer.getLocation().x);
            r.startY.set(treffer.getLocation().y);
            r.startZ.set(treffer.getLocation().z);
            r.startYaw.set(gier);
        });
    }

    /** Den gewaehlten Marker dorthin, wohin die Kamera blickt. */
    void hierher()
    {
        RegieMarker m = this.gewaehlt();
        BlockHitResult treffer = m == null ? null : this.ziel();

        if (treffer != null)
        {
            this.aendern(m, (w) ->
            {
                w.x.set(treffer.getLocation().x);
                w.y.set(treffer.getLocation().y + hoehe(w.istOrt(), treffer));
                w.z.set(treffer.getLocation().z);
            });
        }
    }

    /** Beginn oder Ende in Sekunden; ein leeres Zeitfenster wird laut abgelehnt. */
    void zeit(boolean ende, double sekunden)
    {
        RegieMarker m = this.gewaehlt();
        long tick = Math.round(sekunden * 20D);

        if (m == null)
        {
            return;
        }

        if (!Double.isFinite(sekunden) || tick < 0 || tick > Integer.MAX_VALUE
            || (ende ? tick <= m.start.get() : tick >= m.ende.get()))
        {
            this.melden("The end must come after the start.");

            return;
        }

        this.aendern(m, (w) -> (ende ? w.ende : w.start).set((int) tick));
    }

    void entfernen()
    {
        RegieMarker m = this.gewaehlt();

        if (m != null)
        {
            this.zaun();
            this.regie().marker.entfernen(m);
            this.gewaehlt = "";
            this.fertig();
        }
    }

    <T extends BaseValue> void aendern(T wert, Consumer<T> aenderung)
    {
        this.zaun();
        BaseValue.edit(wert, aenderung);
        this.fertig();
    }

    private void zaun()
    {
        zaun(this.panel);
    }

    /** Undo-Grenze: was danach kommt, verschmilzt nicht mit dem Schritt davor. */
    static void zaun(UIFilmPanel panel)
    {
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }
    }

    private void fertig()
    {
        this.zaun();
        this.fehler = "";
        this.geaendert.run();
    }

    private void melden(String text)
    {
        this.fehler = text;
        this.geaendert.run();
    }
}
