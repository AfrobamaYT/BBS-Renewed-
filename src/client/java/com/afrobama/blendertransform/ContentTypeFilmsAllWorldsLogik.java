package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.utils.repos.FolderManagerRepository;
import mchorse.bbs_mod.utils.repos.IRepository;
import net.minecraft.client.Minecraft;
import java.io.File;

/**
 * Logik des "film editor for every world"-mixins — herausgelöst aus
 * {@code ContentTypeFilmsAllWorldsMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>Beim beitritt zu einem nicht-BBS-server routet BBS von haus aus das films-repository
 * nach {@code <gameDir>/data/films}, was meist leer ist und den editor kaputt aussehen lässt.
 * Dieser code leitet im multiplayer/nicht-BBS-server-fall auf einen globalen ordner um
 * ({@code <gameDir>/bbs/films-global/}), damit filme aus jeder welt heraus bearbeitet werden
 * können — singleplayer, vanilla-server, BBS-server gleichermassen. Hier erstellte filme
 * überleben welt-wechsel.</p>
 *
 * <p>Hinweis: das entsperrt nur AUTORING/BEARBEITEN. Server-seitiges abspielen (action-clips,
 * die fake-spieler erzeugen) braucht weiterhin BBS auf dem server — das ist eine
 * netzwerk-protokoll-grenze, kein UI-gate.</p>
 */
public final class ContentTypeFilmsAllWorldsLogik
{
    private ContentTypeFilmsAllWorldsLogik() {}

    private static IRepository<Film> globalRepo;

    /**
     * @return das umgeleitete repository, oder {@code null} wenn vanilla weiterlaufen soll
     *         (SP, oder MP auf einem echten BBS-server)
     */
    public static IRepository<Film> forceGlobalEditor()
    {
        Minecraft mc = Minecraft.getInstance();

        /* SP — vanilla übernimmt (welt-lokale filme). */
        if (mc.hasSingleplayerServer())
        {
            return null;
        }

        /* MP auf einem echten BBS-server — network-repo behalten, damit filme vom server laden. */
        if (mchorse.bbs_mod.network.ClientNetwork.isIsBBSModOnServer())
        {
            return null;
        }

        /* Vanilla-server (oder getrennt) — auf den globalen ordner umleiten. */
        return getOrCreateGlobalRepo();
    }

    private static IRepository<Film> getOrCreateGlobalRepo()
    {
        if (globalRepo == null)
        {
            File root = BBSMod.getAssetsFolder().getParentFile();
            File globalFilms = new File(root, "films-global");

            if (!globalFilms.exists())
            {
                globalFilms.mkdirs();
            }

            globalRepo = new FolderManagerRepository<>(
                new FilmManager(() -> globalFilms)
            );
        }

        return globalRepo;
    }
}
