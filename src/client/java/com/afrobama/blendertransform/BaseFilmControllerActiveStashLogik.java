package com.afrobama.blendertransform;

/**
 * Logik des Active-Stash-Handlers — im Kernel-Paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code BaseFilmControllerActiveStashMixin} (2026-07-29, stehende
 * User-Order #56: „alles nachruesten damit alle mixins jetzt und in der zukunft hotswapbar
 * werden").</p>
 *
 * <p>Was der Handler tut: {@code BaseFilmController.update()} läuft jeden Tick, solange ein
 * Film geladen ist — im Leerlauf des Editors genauso wie beim Abspielen. Der laufende
 * Controller wird hier hinterlegt, damit der {@code BackgroundBaker} weiß, wessen Entities
 * er backen soll. Der Render-Loop-Bypass allein reichte dafür nicht: der lief nur während
 * des Abspielens und konnte das Backen im Leerlauf deshalb nicht antreiben.</p>
 *
 * <p>⚠ WARUM DAS ÜBERHAUPT UMGEZOGEN IST: ein Handler, der seine Logik selbst trägt, ist
 * nicht hotswapbar — die Zielklasse wird beim Laden gewoben und nie wieder getauscht, jede
 * Änderung daran kostet einen Boot. Als Einzeiler, der hierher delegiert, bleibt der
 * gewobene Teil unverändert und diese Klasse ist beliebig oft tauschbar.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. {@code zielObjekt} kommt als {@code Object}
 * herein und wird erst hier drinnen gedeutet.</p>
 */
public final class BaseFilmControllerActiveStashLogik
{
    private BaseFilmControllerActiveStashLogik() {}

    public static void stashActive(Object zielObjekt)
    {
        if (!(zielObjekt instanceof mchorse.bbs_mod.film.BaseFilmController controller))
        {
            return;
        }

        com.afrobama.bbstools.client.bbs.fastpath.BackgroundBaker.setActive(controller);
    }
}
