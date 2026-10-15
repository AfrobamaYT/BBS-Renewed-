/* Coded by Afrobama/Claude */
package com.blenderbridge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;

import java.lang.reflect.Field;

/**
 * Compatibility shims against the BBS-CML build this fork compiles against.
 *
 * WHY: BBSTools-Fabric is a companion mod — BBS itself is a compileOnly jar we
 * cannot patch. Newer BBS-CML builds carry small helpers that this build lacks;
 * rather than rewriting every call site when a helper is missing, the shim goes
 * here and the call sites stay identical to the cml-fresh tree they came from.
 */
public final class BBSCompat
{
    /** Cached handle on BBSModClient.dashboard; null until the first lookup succeeds. */
    private static Field dashboardField;
    private static boolean dashboardFieldResolved;

    private BBSCompat()
    {}

    /**
     * Returns the dashboard if it has already been created, otherwise null.
     *
     * TRAP: this is NOT BBSModClient.getDashboard(). getDashboard() CONSTRUCTS a
     * UIDashboard on first call — calling it from a bridge/export path would build
     * the whole editor UI as a side effect of merely asking whether it exists.
     * Every caller here wants "peek, don't create", which newer BBS spells
     * peekDashboard(). This build has only the private static field, so we read it.
     */
    public static UIDashboard peekDashboard()
    {
        if (!dashboardFieldResolved)
        {
            dashboardFieldResolved = true;

            try
            {
                Field field = BBSModClient.class.getDeclaredField("dashboard");

                field.setAccessible(true);
                dashboardField = field;
            }
            catch (Exception e)
            {
                /* Field renamed/removed in this BBS build — peek degrades to "no dashboard",
                 * which every caller already handles (they null-check the result). */
            }
        }

        if (dashboardField == null)
        {
            return null;
        }

        try
        {
            return (UIDashboard) dashboardField.get(null);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Respawns the film editor's actor entities.
     *
     * Newer BBS spells this createEntitiesNow(); this build has the public createEntities() that
     * the newer method merely delegates to (verified against both). Same call, same effect.
     */
    public static void createEntitiesNow(mchorse.bbs_mod.ui.film.controller.UIFilmController controller)
    {
        if (controller != null)
        {
            controller.createEntities();
        }
    }

    /**
     * The dashboard panels that actually have a button in the panel bar — i.e. the ones a user can
     * reach. Mirrors newer BBS's UIDashboardPanels.getVisiblePanels(): iterate `panels`, keep the
     * ones present in the private `panelButtonsMap`.
     *
     * Falls back to the full panel list if the map cannot be read, which over-reports rather than
     * returning nothing — the callers only list panels for diagnostics.
     */
    public static java.util.List<mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel> getVisiblePanels(
        mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels panels)
    {
        if (panels == null)
        {
            return java.util.Collections.emptyList();
        }

        java.util.Map<?, ?> buttons = null;

        try
        {
            Field field = panels.getClass().getDeclaredField("panelButtonsMap");

            field.setAccessible(true);
            buttons = (java.util.Map<?, ?>) field.get(panels);
        }
        catch (Exception e)
        {
            /* fall through to the full list */
        }

        if (buttons == null)
        {
            return panels.panels;
        }

        java.util.List<mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel> visible = new java.util.ArrayList<>();

        for (mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel panel : panels.panels)
        {
            if (buttons.containsKey(panel))
            {
                visible.add(panel);
            }
        }

        return visible;
    }

    /**
     * Whether the film panel currently has the given film loaded in a tab.
     *
     * KORREKTUR 2026-07-28: suchte per reflection nach `loadedFilmTabKey` — ein CML-feld, das es
     * hier nicht gibt — und meldete deshalb IMMER false. Der aufrufer verundet das mit seinem
     * daten-check, also war jedes geladene film-dokument als "not loaded" gemeldet (live gemessen:
     * dataId="ffff", replays=1, trotzdem ok:false). fs HAT tabs, nur anders benannt:
     * `UIDataDashboardPanel.tabs` / `currentTab` / `DataTab.dataId`, alle public.
     *
     * Sind tabs aus (currentTab == -1), gibt es keine tab-haelfte der antwort — dann zaehlt der
     * daten-stand, statt pauschal false zu melden.
     */
    public static boolean isFilmTabLoaded(mchorse.bbs_mod.ui.film.UIFilmPanel panel, String tabId)
    {
        if (panel == null || tabId == null)
        {
            return false;
        }

        if (panel.currentTab < 0 || panel.currentTab >= panel.tabs.size())
        {
            return panel.getData() != null && tabId.equals(panel.getData().getId());
        }

        return tabId.equals(panel.tabs.get(panel.currentTab).dataId);
    }

    /**
     * Opens the dashboard on the film panel and activates the given film's document tab.
     *
     * Mirrors newer BBS's FilmLaunchHelper.openFilm minus its cross-world half: this build has
     * UIDashboard.documentTabsBar and addOrActivate(ContentType, id) (verified), but no
     * closeCrossWorldFilmTab / clearCrossWorldPendingJoin / closeCrossWorldFilmTabs — cross-world
     * films are a newer feature, so there are no stale cross-world tabs to clean up here.
     *
     * getDashboard() (not peekDashboard) is correct here: opening a film SHOULD build the dashboard
     * if it does not exist yet — that is the point of the call.
     */
    public static void openFilm(String filmId)
    {
        if (filmId == null || filmId.trim().isEmpty())
        {
            return;
        }

        UIDashboard dashboard = BBSModClient.getDashboard();
        mchorse.bbs_mod.ui.film.UIFilmPanel panel = dashboard.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class);

        mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
        dashboard.setPanel(panel);

        /* pickData ist derselbe einstieg, den der doppelklick im films-browser nimmt
         * (UIDataOverlayPanel bindet this::pickData): speichert den verlassenen film,
         * setzt die tab-id und laedt ueber requestData -> repository.load -> fill. */
        panel.pickData(filmId);
    }

    /** getDeclaredField walking the hierarchy; null when the field does not exist anywhere. */
    private static Field lookupDeclared(Class<?> owner, String fieldName)
    {
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField(fieldName);

                field.setAccessible(true);

                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                /* keep walking up */
            }
            catch (Exception e)
            {
                return null;
            }
        }

        return null;
    }
}
