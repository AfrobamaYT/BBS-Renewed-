package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;

/**
 * Erlaubt Erweiterungen, eigene Action-Clips in die Fabrik einzutragen.
 *
 * <p>⛔ IM PORT NACHGEZOGEN (2026-08-04, Bugliste #10). Unsere 1.21.1 hat zehn dieser
 * Ereignisse in {@code events/register/}; der Port hatte nur zwei. Folge: jede Erweiterung,
 * die einen Action-Clip anmeldet, blieb still wirkungslos. Sichtbar wurde es an genau einer
 * Stelle — beim Start meldete das Log zweimal:</p>
 *
 * <pre>
 *   WARN (bbstools/snapshot-clip-factory) RegisterActionClipsEvent not on classpath --
 *   WorldSnapshotClip will NOT appear in editor until the typed override is added.
 * </pre>
 *
 * <p>Kein Absturz, kein sichtbarer Fehler, nur ein fehlender Eintrag im Editor. Genau die
 * Sorte, die {@code docs/FEHLER-DIE-KEIN-BILD-ZEIGT.md} meint.</p>
 *
 * <p>Wortgleich aus {@code cml-fresh} uebernommen, nicht nachgebaut — die Vorlage ist die
 * Autoritaet dafuer, wie es sich verhielt, als es ging.</p>
 */
public class RegisterActionClipsEvent
{
    private final MapFactory<Clip, ClipFactoryData> factory;

    public RegisterActionClipsEvent(MapFactory<Clip, ClipFactoryData> factory)
    {
        this.factory = factory;
    }

    public MapFactory<Clip, ClipFactoryData> getFactory()
    {
        return this.factory;
    }
}
