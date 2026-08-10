package com.afrobama.blendertransform;

import com.afrobama.bbstools.editormode.EditorMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Logik hinter {@code EditorModeMobRenderSkipMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Im Editor-Modus zeichnet der Vanilla-Mob-Renderer gar nicht erst: Dorfbewohner, Zombies und
 * der Rest kosten dann keinen Draw-Call. BBS-Replays sind nicht betroffen, sie laufen ueber BBS'
 * Form-Pipeline und nicht ueber den Vanilla-Entity-Dispatcher.</p>
 *
 * <p>Gegenstueck auf der Tick-Seite: {@code EditorModeMobTickSkipMixin}.</p>
 */
public final class EditorModeMobRenderSkipLogik
{
    private EditorModeMobRenderSkipLogik() {}

    /**
     * @return {@code true}, wenn diese Entity im Editor-Modus uebersprungen werden soll — der
     *         Handler setzt dann {@code shouldRender} auf {@code false}
     */
    public static boolean ueberspringen(Entity entity)
    {
        return entity instanceof Mob mob && EditorMode.shouldBypass(mob);
    }
}
