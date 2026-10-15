package com.afrobama.bbstools.client.hitbox;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;

import com.afrobama.bbstools.hitbox.DamageHitboxClip;

/**
 * Client-side UI editor for {@link DamageHitboxClip}. Inherits the default
 * UIClip presentation (keyframe channel sliders) which is enough for now —
 * NeoForge BBSTools never customised the panel either.
 */
public class UIDamageHitboxClip extends UIClip<DamageHitboxClip>
{
    public UIDamageHitboxClip(DamageHitboxClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

}
