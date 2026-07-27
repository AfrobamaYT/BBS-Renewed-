package com.afrobama.bbstools.client.mixin;

import mchorse.bbs_mod.forms.entities.MCEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds real backing storage for {@code showHand} / {@code showHud}
 * to {@link MCEntity}. The shipped BBS-CML jar's {@code MCEntity.class}
 * does NOT override these — it inherits the {@code IEntity} defaults
 * (constant true / no-op setters), which makes
 * {@code show_hand}/{@code show_hud} keyframes silently ineffective:
 * {@code entity.setShowHand(false)} is a no-op so
 * {@code TrackerClientClip.applyClip} always sees
 * {@code entity.getShowHand() == true} and the existing
 * BBS {@code GameRendererMixin}/{@code InGameHudMixin} skip code never
 * triggers.
 *
 * <p><b>What this mixin does:</b> declares concrete public
 * implementations of the 4 methods with two private fields backing the
 * state. JVM method resolution: the concrete methods on the subclass
 * (MCEntity) win over the interface defaults on IEntity, so
 * {@code entity.setShowHand(false)} now actually stores the value and
 * {@code entity.getShowHand()} returns it.</p>
 *
 * <p><b>What this mixin does NOT do:</b> any render-skip work. BBS's
 * own {@code GameRendererMixin.onRenderHand} and
 * {@code InGameHudMixin.render} (already compiled into the BBS jar)
 * gate their cancel on
 * {@code BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController},
 * which means the skip ONLY happens during real film playback — not
 * while scrubbing in the editor. That's the missing safety the
 * previous attempt didn't have, and that's why this approach won't
 * break the editor like before.</p>
 *
 * <p><b>Defaults:</b> both fields default to {@code true}, matching
 * the interface defaults' constant-true return. Behaviour for
 * non-keyframed replays is identical to vanilla BBS.</p>
 */
@Mixin(value = MCEntity.class, remap = false)
public abstract class MCEntityShowHandMixin
{
    @Unique
    private boolean bbstools$showHand = true;

    @Unique
    private boolean bbstools$showHud = true;

    public boolean getShowHand()
    {
        return this.bbstools$showHand;
    }

    public void setShowHand(boolean show)
    {
        this.bbstools$showHand = show;
    }

    public boolean getShowHud()
    {
        return this.bbstools$showHud;
    }

    public void setShowHud(boolean show)
    {
        this.bbstools$showHud = show;
    }

}
