package com.afrobama.bbstools.client.clips;

import com.afrobama.bbstools.clips.WorldSnapshotClip;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import net.fabricmc.api.ClientModInitializer;

/** Client-only editor registration for the server snapshot action. */
public final class WorldSnapshotClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        UIClip.register(WorldSnapshotClip.class, UIWorldSnapshotClip::new);
    }
}
