package com.afrobama.bbstools.gametest;

import com.mcstudio.nano.NanoFlaeche;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.UISkala;
import mchorse.bbs_mod.ui.hilfe.HilfeErststart;
import mchorse.bbs_mod.ui.hilfe.UIHilfeHinweis;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Test-mod-only readback of actual rendered hit zones; input goes through Fabric. */
final class DashboardFirstRun
{
    static void dismiss(ClientGameTestContext context)
    {
        context.runOnClient(client ->
        {
            UIDashboard dashboard = dashboard();
            if (dashboard.overlay.getChildren(UIHilfeHinweis.class).isEmpty()) HilfeErststart.zeigen(dashboard);
        });
        context.waitFor(client -> target(client) != null);
        context.takeScreenshot("bbs-first-run-hint");
        double[] point = context.computeOnClient(DashboardFirstRun::target);
        if (point == null) throw new AssertionError("Rendered welcome button disappeared before input");
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTick();
        context.getInput().pressMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
        context.waitFor(client -> dashboard().overlay.getChildren(UIHilfeHinweis.class).isEmpty()
            && EditorZustaende.hilfeErststart.get().equals("known"));
        context.waitTicks(5);
        System.out.println("BBS_FIRST_RUN_HINT_DISMISSED known; Fabric mouse input; dashboard unobstructed");
    }

    private static UIDashboard dashboard()
    {
        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard) return dashboard;
        throw new AssertionError("Require the actual active BBS dashboard");
    }

    private static double[] target(Minecraft client)
    {
        List<UIHilfeHinweis> hints = dashboard().overlay.getChildren(UIHilfeHinweis.class);
        if (hints.isEmpty()) return null;
        if (hints.size() != 1) throw new AssertionError("Ambiguous welcome hint");
        List<NanoFlaeche> surfaces = hints.get(0).getChildren(NanoFlaeche.class);
        if (surfaces.size() != 1) throw new AssertionError("Unexpected welcome drawing surface");
        NanoFlaeche surface = surfaces.get(0);

        try
        {
            Field field = NanoFlaeche.class.getDeclaredField("zonen");
            field.setAccessible(true);
            Object zones = field.get(surface);
            Method all = zones.getClass().getDeclaredMethod("alle");
            all.setAccessible(true);

            for (Object zone : (List<?>) all.invoke(zones))
            {
                if (!value(zone, "id").equals("known")) continue;
                double x = surface.area.x + (number(zone, "x") + number(zone, "w") / 2D) / surface.skala();
                double y = surface.area.y + (number(zone, "y") + number(zone, "h") / 2D) / surface.skala();
                com.mojang.blaze3d.platform.Window window = client.getWindow();

                return new double[] {x * UISkala.rest() * window.getScreenWidth() / window.getGuiScaledWidth(),
                    y * UISkala.rest() * window.getScreenHeight() / window.getGuiScaledHeight()};
            }
            return null;
        }
        catch (ReflectiveOperationException failure)
        {
            throw new AssertionError("Cannot read the rendered welcome button", failure);
        }
    }

    private static Object value(Object zone, String name) throws ReflectiveOperationException
    {
        Method method = zone.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(zone);
    }

    private static double number(Object zone, String name) throws ReflectiveOperationException
    {
        return ((Number) value(zone, name)).doubleValue();
    }

    private DashboardFirstRun()
    {}
}
