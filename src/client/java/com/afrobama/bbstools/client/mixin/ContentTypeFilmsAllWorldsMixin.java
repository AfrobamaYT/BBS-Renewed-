/*
 * Coded by Afrobama/Claude — BBSTools-Fabric
 */
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.repos.IRepository;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Film editor for every world" — when joining a non-BBS server, vanilla
 * BBS routes the films repository to {@code <gameDir>/data/films} which is
 * usually empty, making the editor look broken.
 *
 * <p>This mixin intercepts {@code ContentType#getFilmsRepository} and, in
 * the multiplayer/non-BBS-server branch, redirects to a single global
 * folder ({@code <gameDir>/bbs/films-global/}) so the user can author /
 * edit films from any world — singleplayer, vanilla server, BBS server
 * alike. Films created here persist across worlds.</p>
 *
 * <p>Note: this only unblocks <b>editing/authoring</b>. Server-side
 * playback (action clips spawning fake players, etc.) still requires
 * BBS on the server — that's a network protocol limit, not a UI gate.</p>
 *
 * <p>TODO: gate behind a settings toggle (BBSSettings.editorAllWorlds)
 * so users can opt back into the original behavior if needed.</p>
 */
@Mixin(targets = "mchorse.bbs_mod.ui.ContentType", remap = false)
public class ContentTypeFilmsAllWorldsMixin
{
    @Inject(
        method = "getFilmsRepository",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void bbstools$forceGlobalEditor(CallbackInfoReturnable<IRepository<? extends ValueGroup>> cir)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.ContentTypeFilmsAllWorldsLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. Rückgabewert ist IRepository<Film> (BBS-typ,
         * stabiler loader) oder null, wenn vanilla weiterlaufen soll. */
        @SuppressWarnings("unchecked")
        IRepository<Film> ergebnis = (IRepository<Film>)
            MixinBruecke.rufe("ContentTypeFilmsAllWorldsLogik", "forceGlobalEditor");

        if (ergebnis != null)
        {
            cir.setReturnValue(ergebnis);
        }
    }

}
