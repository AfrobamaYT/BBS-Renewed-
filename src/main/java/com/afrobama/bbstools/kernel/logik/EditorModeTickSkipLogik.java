package com.afrobama.bbstools.kernel.logik;

import com.afrobama.bbstools.editormode.EditorMode;
import net.minecraft.world.entity.Mob;

/**
 * Logik hinter {@code EditorModeMobTickSkipMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Im Editor-Modus tickt ein Mob gar nicht erst — Gegenstueck zur Renderseite
 * ({@code EditorModeMobRenderSkipMixin} → EditorModeMobRenderSkipLogik, client-seitig).</p>
 *
 * <p>⚠️ LAEUFT JE MOB UND JE TICK. In einer Welt mit vielen Mobs ist das der teuerste Pfad,
 * an dem eine Bruecke haengt. Genau deshalb steht der Aufruf hier und nicht im gewobenen
 * Handler: was hier waechst, laesst sich messen und aendern, ohne zu booten.</p>
 */
public final class EditorModeTickSkipLogik
{
    private EditorModeTickSkipLogik() {}

    /**
     * @param mob der tickende Mob; kommt als Argument, weil der Cast {@code (Mob) (Object)
     *            this} den gewobenen {@code this} braucht
     * @return {@code true}, wenn der Tick uebersprungen werden soll
     */
    public static boolean ueberspringen(Object mob)
    {
        return mob instanceof Mob m && EditorMode.shouldBypass(m);
    }
}
