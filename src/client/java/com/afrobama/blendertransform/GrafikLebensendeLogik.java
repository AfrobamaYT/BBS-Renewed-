package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.TextureManager;

/** Close BBS-owned GPU objects after Minecraft's last disconnect frame, before its device. */
public final class GrafikLebensendeLogik
{
    private GrafikLebensendeLogik() {}

    public static void schliessen()
    {
        Runnable[] actions = {
            () -> { var models = BBSModClient.getModels(); if (models != null) models.close(); },
            () -> { var targets = BBSModClient.getFramebuffers(); if (targets != null) targets.delete(); },
            com.mcstudio.nano.NanoRessourcen::schliessen,
            mchorse.bbs_mod.ui.utils.UIChalkboard::closeGpuResources,
            mchorse.bbs_mod.ui.framework.elements.utils.schrift.VektorSchrift::schliessen,
            () -> { TextureManager textures = BBSModClient.getTextures(); if (textures != null) textures.delete(); },
            BBSRendering::closeGpuResources
        };
        Throwable failure = null;
        for (Runnable action : actions)
        {
            try { action.run(); }
            catch (Throwable error)
            {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new IllegalStateException("BBS GPU shutdown failed", failure);
    }
}
