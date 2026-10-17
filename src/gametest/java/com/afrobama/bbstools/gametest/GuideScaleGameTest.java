package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.dashboard.AnnouncementGate;
import com.afrobama.bbstools.client.keybinds.KeybindOverrides;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.regie.RegieMarker;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISeitenleiste;
import mchorse.bbs_mod.ui.film.regie.UIRegieReiter;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Supplier;

/** Actual Guide scroll/input geometry at scale2, including dynamically added marker fields. */
public final class GuideScaleGameTest implements FabricClientGameTest
{
    private static final String FILM = "ci_guide_scale2";

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            world.getConnection().waitForChunksRender();
            context.setScreen(() ->
            {
                AnnouncementGate.markShown();
                return new UIScreen(Component.empty(), BBSModClient.getDashboard());
            });
            context.runOnClient(GuideScaleGameTest::prepare);
            context.waitTicks(20);
            Target initial = context.computeOnClient(client -> geometry(guide().felder.richtung, false));
            context.takeScreenshot("guide-scale2-initial");
            Target plainBottom = reveal(context, () -> guide().felder.richtung, false);
            context.takeScreenshot("guide-scale2-bottom");

            Target marker = reveal(context, () -> guide().liste, true);
            context.getInput().setCursorPos(marker.point()[0], marker.point()[1]);
            context.waitTick();
            context.getInput().pressMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
            context.waitTicks(6);
            if (!context.computeOnClient(client -> guide().getChildren(UITextbox.class).contains(guide().felder.name)))
                throw new AssertionError("Actual marker-list click did not attach the marker-edit fields");
            Target selectedBottom = reveal(context, () -> guide().felder.richtung, false);
            context.takeScreenshot("guide-scale2-selected-bottom");
            System.out.println("BBS_GUIDE_SCALE2 " + new com.google.gson.Gson().toJson(
                Map.of("initial", initial, "plainBottom", plainBottom, "selectedBottom", selectedBottom,
                    "configuredScale", context.computeOnClient(client -> BBSSettings.userIntefaceScale.get()))));
        }
    }

    private static void prepare(Minecraft client)
    {
        KeybindOverrides.setActivePreset("BBS Default");
        BBSSettings.userIntefaceScale.set(2F);
        Film film = new Film();
        film.setId(FILM);
        Camera camera = new Camera();
        camera.set(client.player, MathUtils.toRad(client.options.fov().get()));
        IdleClip clip = new IdleClip();
        clip.fromCamera(camera);
        clip.duration.set(80);
        film.camera.addClip(clip);
        film.regie.marker.neu(RegieMarker.ORT, "Guide scroll marker", 0, 80,
            client.player.getX(), client.player.getY(), client.player.getZ());
        if (!BBSMod.getFilms().save(FILM, film.toData().asMap())) throw new AssertionError("Cannot save Guide fixture");
        Film saved = BBSMod.getFilms().load(FILM);
        if (saved == null) throw new AssertionError("Cannot reload Guide fixture");
        UIDashboard dashboard = BBSModClient.getDashboard();
        UIFilmPanel panel = panel();
        dashboard.setPanel(panel);
        panel.fill(saved);
        dashboard.copyCurrentEntityCamera();
        mchorse.bbs_mod.ui.hilfe.HilfeErststart.hilfeGeoeffnet();
        java.util.List<UISeitenleiste> bars = panel.getChildren(UISeitenleiste.class);
        if (bars.size() != 1) throw new AssertionError("Ambiguous actual viewport sidebar: " + bars.size());
        bars.getFirst().zeige("Guide");
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }

    private static UIRegieReiter guide()
    {
        java.util.List<UIRegieReiter> guides = panel().getChildren(UIRegieReiter.class);
        if (guides.size() != 1) throw new AssertionError("Require one actual open Guide panel: " + guides.size());
        return guides.getFirst();
    }

    private static Target reveal(ClientGameTestContext context, Supplier<UIElement> target, boolean firstRow)
    {
        Target measured = null;
        for (int attempt = 0; attempt < 40; attempt++)
        {
            measured = context.computeOnClient(client -> geometry(target.get(), firstRow));
            if (measured.inside()) return measured;
            context.getInput().setCursorPos(measured.gutter()[0], measured.gutter()[1]);
            context.getInput().scroll(measured.control()[1] < measured.viewport()[1] ? 2D : -2D);
            context.waitTicks(3);
        }
        throw new AssertionError("Guide control remains clipped after actual wheel input: "
            + new com.google.gson.Gson().toJson(measured));
    }

    private static Target geometry(UIElement element, boolean firstRow)
    {
        UIScrollView roll = guide().getParent(UIScrollView.class);
        if (roll == null) throw new AssertionError("Actual Guide is outside a scroll viewport");
        Area a = element.area, r = roll.area;
        int height = firstRow ? Math.min(20, a.h) : a.h;
        double scroll = roll.scroll.getScroll(), y = a.y - scroll;
        float scale = (float) Minecraft.getInstance().getWindow().getScreenWidth() / panel().getContext().menu.width;
        boolean inside = a.w > 0 && height > 0 && a.x >= r.x && a.ex() <= r.ex()
            && y >= r.y && y + height <= r.ey();
        return new Target(new double[] {a.x, y, a.w, height}, new int[] {r.x, r.y, r.w, r.h},
            scroll, roll.scroll.scrollSize, scale,
            new double[] {(a.x + a.w / 2D) * scale, (y + height / 2D) * scale},
            new double[] {(r.x + 1D) * scale, (r.y + Math.min(8D, r.h / 2D)) * scale}, inside);
    }

    private record Target(double[] control, int[] viewport, double scroll, int scrollSize,
        float nativeScale, double[] point, double[] gutter, boolean inside) {}
}
