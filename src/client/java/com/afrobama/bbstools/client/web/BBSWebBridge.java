// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.web;

import com.cinemamod.mcef.MCEF;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.repos.IRepository;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JS&lt;-&gt;Java bridge for the MCEF HTML UIs.
 *
 * The bundled mockups call {@code window.cefQuery({request, onSuccess, onFailure})}; this
 * registers a global CEF message router on the shared MCEF client that answers those queries
 * with real BBS data (film list, player name, ...) and performs real actions (open a film).
 *
 * Requests are JSON: {@code {"action":"films.open","params":{"id":"..."}}}. Responses are JSON
 * strings passed to {@code onSuccess}. Install once, before the first browser loads, so
 * {@code window.cefQuery} exists for the page.
 */
public final class BBSWebBridge
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools-web");
    private static final Gson GSON = new Gson();

    private static boolean installed;

    /** BBS screen that was open underneath when the web screen launched (the dashboard, if any),
     *  captured so actions like "open film" can return to it. Set by {@link MCEFWebCommand}. */
    static Screen bbsParent;

    private BBSWebBridge() {}

    public static void install()
    {
        if (installed || !MCEF.isInitialized())
        {
            return;
        }

        try
        {
            CefMessageRouter router = CefMessageRouter.create();

            router.addHandler(new CefMessageRouterHandlerAdapter()
            {
                @Override
                public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback)
                {
                    return BBSWebBridge.handle(request, callback);
                }
            }, true);

            MCEF.getClient().getHandle().addMessageRouter(router);
            installed = true;
            LOG.info("[web] cefQuery bridge installed");
        }
        catch (Throwable t)
        {
            LOG.error("[web] bridge install failed", t);
        }
    }

    private static boolean handle(String request, CefQueryCallback callback)
    {
        try
        {
            JsonObject obj = JsonParser.parseString(request).getAsJsonObject();
            String action = obj.get("action").getAsString();
            JsonObject params = obj.has("params") && obj.get("params").isJsonObject()
                ? obj.getAsJsonObject("params")
                : new JsonObject();

            switch (action)
            {
                case "films.list":
                    listFilms(callback);

                    return true;
                case "films.open":
                    openFilm(params.has("id") ? params.get("id").getAsString() : null, callback);

                    return true;
                case "films.new":
                    newFilm(str(params, "name"), callback);

                    return true;
                case "films.duplicate":
                    duplicateFilm(str(params, "id"), str(params, "name"), callback);

                    return true;
                case "films.rename":
                    renameFilm(str(params, "id"), str(params, "name"), callback);

                    return true;
                case "films.delete":
                    deleteFilm(str(params, "id"), callback);

                    return true;
                case "player.name":
                    Minecraft mc = Minecraft.getInstance();
                    String name = mc.player != null ? mc.player.getGameProfile().getName() : "Player";

                    callback.success(GSON.toJson(name));

                    return true;
                default:
                    callback.failure(-1, "unknown action: " + action);

                    return true;
            }
        }
        catch (Throwable t)
        {
            callback.failure(-2, String.valueOf(t.getMessage()));

            return true;
        }
    }

    private static void listFilms(CefQueryCallback callback)
    {
        try
        {
            ContentType.FILMS.getRepository().requestKeys(keys ->
            {
                List<String> sorted = new ArrayList<>(keys);

                sorted.sort(String.CASE_INSENSITIVE_ORDER);

                List<Map<String, Object>> films = new ArrayList<>();

                for (String key : sorted)
                {
                    Map<String, Object> film = new HashMap<>();

                    film.put("id", key);
                    film.put("name", new DataPath(key).getLast());

                    String thumb = thumbnailDataUrl(key);

                    if (thumb != null)
                    {
                        film.put("thumbnail", thumb);
                    }

                    films.add(film);
                }

                callback.success(GSON.toJson(films));
            });
        }
        catch (Throwable t)
        {
            callback.failure(-3, String.valueOf(t.getMessage()));
        }
    }

    /** Read BBS's on-disk film thumbnail (config/bbs/thumbnails/films/&lt;id&gt;.png) as a base64
     *  data URL so the HTML can show the real film preview. Null if missing/too large. */
    private static String thumbnailDataUrl(String id)
    {
        try
        {
            Path file = FabricLoader.getInstance().getGameDir().resolve("config/bbs/thumbnails/films").resolve(id + ".png");

            if (!Files.isRegularFile(file))
            {
                return null;
            }

            byte[] bytes = Files.readAllBytes(file);

            if (bytes.length > 600000)
            {
                return null;
            }

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static void openFilm(String id, CefQueryCallback callback)
    {
        if (id == null || id.isEmpty())
        {
            callback.failure(-4, "no film id");

            return;
        }

        Minecraft.getInstance().execute(() -> openInDashboard(id));
        callback.success(GSON.toJson("ok"));
    }

    /** Return to the captured BBS dashboard and open/activate the film there. Render thread only. */
    private static void openInDashboard(String id)
    {
        Screen parent = bbsParent;

        if (parent instanceof UIScreen screen && screen.getMenu() instanceof UIDashboard dashboard)
        {
            Minecraft.getInstance().gui.setScreen(parent);
                /* AUTONOMIE 2026-07-27: fs hat keine dokument-tabs (documentTabsBar/ContentType
                 * sind CML). Aequivalent im fs-kern: das film-panel aktivieren. Ein tab-streifen
                 * mit mehreren offenen filmen existiert dort nicht — es ist immer genau einer offen. */
                dashboard.setPanel(dashboard.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class));
        }
        else
        {
            /* No BBS dashboard underneath (launched from the world) — just close the browser. */
            Minecraft.getInstance().gui.setScreen(null);
            LOG.warn("[web] open: no BBS dashboard parent; open BBS first to load '{}'", id);
        }
    }

    @SuppressWarnings("unchecked")
    private static IRepository<ValueGroup> filmsRepo()
    {
        return (IRepository<ValueGroup>) ContentType.FILMS.getRepository();
    }

    private static void newFilm(String name, CefQueryCallback callback)
    {
        if (blank(name))
        {
            callback.failure(-5, "empty name");

            return;
        }

        Minecraft.getInstance().execute(() ->
        {
            try
            {
                IRepository<ValueGroup> repo = filmsRepo();
                ValueGroup created = repo.create(name);

                if (created != null)
                {
                    repo.save(name, created.toData().asMap());
                }

                openInDashboard(name);
            }
            catch (Throwable t)
            {
                LOG.error("[web] films.new", t);
            }
        });

        callback.success(GSON.toJson("ok"));
    }

    private static void duplicateFilm(String id, String name, CefQueryCallback callback)
    {
        if (blank(id) || blank(name))
        {
            callback.failure(-6, "missing id/name");

            return;
        }

        Minecraft.getInstance().execute(() ->
        {
            try
            {
                IRepository<ValueGroup> repo = filmsRepo();

                repo.load(id, (data) ->
                {
                    if (data != null)
                    {
                        repo.save(name, data.toData().asMap());
                    }
                });
            }
            catch (Throwable t)
            {
                LOG.error("[web] films.duplicate", t);
            }
        });

        callback.success(GSON.toJson("ok"));
    }

    private static void renameFilm(String id, String name, CefQueryCallback callback)
    {
        if (blank(id) || blank(name))
        {
            callback.failure(-7, "missing id/name");

            return;
        }

        Minecraft.getInstance().execute(() ->
        {
            try
            {
                filmsRepo().rename(id, name);
            }
            catch (Throwable t)
            {
                LOG.error("[web] films.rename", t);
            }
        });

        callback.success(GSON.toJson("ok"));
    }

    private static void deleteFilm(String id, CefQueryCallback callback)
    {
        if (blank(id))
        {
            callback.failure(-8, "no id");

            return;
        }

        Minecraft.getInstance().execute(() ->
        {
            try
            {
                filmsRepo().delete(id);
                Files.deleteIfExists(FabricLoader.getInstance().getGameDir().resolve("config/bbs/thumbnails/films").resolve(id + ".png"));
            }
            catch (Throwable t)
            {
                LOG.error("[web] films.delete", t);
            }
        });

        callback.success(GSON.toJson("ok"));
    }

    private static String str(JsonObject params, String key)
    {
        return params.has(key) && !params.get(key).isJsonNull() ? params.get(key).getAsString() : null;
    }

    private static boolean blank(String s)
    {
        return s == null || s.trim().isEmpty();
    }

}
