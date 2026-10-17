package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.clips.UIWorldSnapshotClip;
import com.afrobama.bbstools.client.dashboard.AnnouncementGate;
import com.afrobama.bbstools.clips.WorldSnapshotClip;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.ZeitleisteZweig;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Supplier;

/** The real action editor, mouse/text input and saved film round trip. */
public final class WorldSnapshotEditorGameTest implements FabricClientGameTest
{
    private static final String FILM = "ci_snapshot_editor";
    private static final String SNAPSHOT = "bbstools:ci_snapshot";

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
            context.runOnClient(WorldSnapshotEditorGameTest::prepare);
            context.waitTicks(10);
            click(context, () -> editor().snapshot);
            context.getInput().typeChars(SNAPSHOT);
            context.waitTicks(3);
            if (!context.computeOnClient(client -> editor().clip.snapshotFile.get().equals(SNAPSHOT)))
                throw new AssertionError("Typed snapshot ID did not reach the actual clip");
            context.takeScreenshot("snapshot-editor-structure-id");
            click(context, () -> editor().restoreBlockEntities);
            click(context, () -> editor().diffOnly);
            context.waitTicks(3);
            context.takeScreenshot("snapshot-editor-options");
            context.runOnClient(client ->
            {
                WorldSnapshotClip clip = editor().clip;
                if (!clip.restoreBlockEntities.get() || clip.diffOnly.get())
                    throw new AssertionError("Actual toggle clicks did not change the restore options");
                Film film = panel().getData();
                if (!BBSMod.getFilms().save(FILM, film.toData().asMap())) throw new AssertionError("Cannot save edited snapshot film");
                open(BBSMod.getFilms().load(FILM));
                WorldSnapshotClip restored = editor().clip;
                if (!restored.snapshotFile.get().equals(SNAPSHOT) || !restored.restoreBlockEntities.get()
                    || restored.diffOnly.get() || restored.minX.get() != 12 || restored.maxX.get() != 13
                    || restored.minY.get() != -59 || restored.maxY.get() != -59
                    || restored.minZ.get() != 12 || restored.maxZ.get() != 12)
                    throw new AssertionError("Saved action editor changed snapshot values on reopen");
                if (!editor().snapshot.getText().equals(SNAPSHOT) || !editor().restoreBlockEntities.getValue()
                    || editor().diffOnly.getValue()) throw new AssertionError("Reopened editor widgets are stale");
            });
            context.waitTicks(5);
            context.takeScreenshot("snapshot-editor-reopened");
            System.out.println("BBS_WORLD_SNAPSHOT_EDITOR " + new com.google.gson.Gson().toJson(
                Map.of("registeredPanel", true, "typedStructureId", SNAPSHOT, "actualMouseToggles", true,
                    "filmSaveReload", true, "cornersPreserved", true)));
        }
    }

    private static void prepare(Minecraft client)
    {
        BBSSettings.userIntefaceScale.set(2F);
        Film film = new Film();
        film.setId(FILM);
        Camera camera = new Camera();
        camera.set(client.player, MathUtils.toRad(client.options.fov().get()));
        IdleClip idle = new IdleClip();
        idle.fromCamera(camera);
        idle.duration.set(80);
        film.camera.addClip(idle);
        Replay replay = film.replays.addReplay();
        WorldSnapshotClip clip = (WorldSnapshotClip) BBSMod.getFactoryActionClips().create(Link.create("bbstools:world_snapshot"));
        clip.minX.set(12); clip.maxX.set(13);
        clip.minY.set(-59); clip.maxY.set(-59);
        clip.minZ.set(12); clip.maxZ.set(12);
        replay.actions.addClip(clip);
        open(film);
    }

    private static void open(Film film)
    {
        if (film == null) throw new AssertionError("Cannot reopen snapshot film");
        BBSModClient.getDashboard().setPanel(panel());
        panel().fill(film);
        BBSModClient.getDashboard().copyCurrentEntityCamera();
        mchorse.bbs_mod.ui.hilfe.HilfeErststart.hilfeGeoeffnet();
        Replay replay = film.replays.getAllTyped().getFirst();
        panel().replayEditor.setReplay(replay);
        ZeitleisteZweig.aktionenModus(panel());
        panel().actionEditor.pickClip(replay.actions.get(0));
        editor();
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }

    private static UIWorldSnapshotClip editor()
    {
        java.util.List<UIWorldSnapshotClip> editors = panel().getChildren(UIWorldSnapshotClip.class);
        if (editors.size() != 1)
            throw new AssertionError("Expected one actual snapshot editor panel, got " + editors.size());
        return editors.getFirst();
    }

    private static void click(ClientGameTestContext context, Supplier<UIElement> target)
    {
        for (int attempt = 0; attempt < 35; attempt++)
        {
            double[] point = context.computeOnClient(client ->
            {
                Area field = target.get().area, viewport = editor().panels.area;
                double y = field.y - editor().panels.scroll.getScroll();
                float scale = (float) client.getWindow().getScreenWidth() / panel().getContext().menu.width;
                boolean inside = field.w > 0 && field.h > 0 && y >= viewport.y && y + field.h <= viewport.ey();
                return new double[] {(field.x + field.w / 2D) * scale, (y + field.h / 2D) * scale,
                    inside ? 1 : 0, (viewport.x + 1D) * scale, (viewport.y + 8D) * scale,
                    y < viewport.y ? 2 : -2};
            });
            context.getInput().setCursorPos(point[2] == 1 ? point[0] : point[3], point[2] == 1 ? point[1] : point[4]);
            if (point[2] == 1)
            {
                context.waitTick();
                context.getInput().pressMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
                context.waitTicks(3);
                return;
            }
            context.getInput().scroll(point[5]);
            context.waitTicks(3);
        }
        throw new AssertionError("Snapshot editor control remains clipped after real wheel input");
    }
}
