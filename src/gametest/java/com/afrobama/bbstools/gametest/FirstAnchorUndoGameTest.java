package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.dashboard.AnnouncementGate;
import com.afrobama.bbstools.client.keybinds.KeybindOverrides;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoGimbal;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** The first saved-anchor ring drag, not a direct value assignment or native undo substitute. */
public final class FirstAnchorUndoGameTest implements FabricClientGameTest
{
    private static final String FILM = "ci_first_anchor_undo";

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
            context.runOnClient(FirstAnchorUndoGameTest::prepare);
            context.waitTicks(20);
            context.runOnClient(client -> selectAnchor());
            context.waitTicks(5);
            context.takeScreenshot("h1-anchor-ready");
            double[][] points = context.computeOnClient(client -> ringPoints());
            int selected = -1;

            for (int i = 0; i < points.length; i++)
            {
                double[] end = points[(i + 12) % points.length];
                if (!inside(points[i]) || !inside(end)) continue;
                context.getInput().setCursorPos(points[i][0], points[i][1]);
                context.waitTicks(2);
                if (context.computeOnClient(client -> panel().getController().getGizmoStencil().getIndex()) == 13)
                {
                    selected = i;
                    break;
                }
            }
            if (selected < 0) throw new AssertionError("H1 fixture has no actual X-ring stencil hit");
            float[] before = context.computeOnClient(client -> authored());
            Map<String, Object> beforeState = context.computeOnClient(client -> state());
            double[] start = points[selected], end = points[(selected + 12) % points.length];
            context.getInput().holdMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));

            try
            {
                for (int step = 1; step <= 24; step++)
                {
                    double fraction = step / 24D;
                    context.getInput().setCursorPos(start[0] + (end[0] - start[0]) * fraction,
                        start[1] + (end[1] - start[1]) * fraction);
                    context.waitTick();
                }
            }
            finally
            {
                context.getInput().releaseMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
            }
            context.waitTicks(11);
            float[] after = context.computeOnClient(client -> authored());
            Map<String, Object> afterState = context.computeOnClient(client -> state());
            context.takeScreenshot("h1-anchor-after-drag");
            if (after[0] == before[0] || after[1] != before[1] || after[2] != before[2])
                throw new AssertionError("Actual X-ring drag did not isolate the authored X channel");
            context.getInput().holdControl();
            try { context.getInput().pressKey(InputConstants.KEY_Z); }
            finally { context.getInput().releaseControl(); }
            context.waitTicks(5);
            float[] undo = context.computeOnClient(client -> authored());
            Map<String, Object> undoState = context.computeOnClient(client -> state());
            context.takeScreenshot("h1-anchor-after-undo");
            System.out.println("BBS_H1_FIRST_ANCHOR " + new com.google.gson.Gson().toJson(
                Map.of("before", beforeState, "afterDrag", afterState, "afterUndo", undoState)));
            if (!Arrays.equals(before, undo))
                throw new AssertionError("H1 first anchor Undo failed: before=" + Arrays.toString(before)
                    + " after=" + Arrays.toString(after) + " undo=" + Arrays.toString(undo));
            if (!Arrays.equals(before, (float[]) undoState.get("widget")))
                throw new AssertionError("Authored anchor restored but the displayed transform is stale");
        }
    }

    @SuppressWarnings("unchecked")
    private static void prepare(Minecraft client)
    {
        KeybindOverrides.setActivePreset("BBS Default");
        EditorZustaende.autoKey.set(false);
        BBSSettings.transformSpace.set(5);
        BBSSettings.gizmoHoverTolerance.set(0);
        BBSSettings.axesScale.set(2.25F);
        Gizmo.INSTANCE.setMode(Gizmo.Mode.ROTATE);
        client.player.setYRot(0F);
        client.player.setXRot(0F);
        Film film = new Film();
        film.setId(FILM);
        IdleClip clip = new IdleClip();
        Camera camera = new Camera();
        camera.set(client.player, MathUtils.toRad(client.options.fov().get()));
        clip.fromCamera(camera);
        clip.duration.set(80);
        film.camera.addClip(clip);
        Replay replay = film.replays.addReplay();
        ModelForm form = new ModelForm();
        form.model.set("player/steve_bends_3d");
        replay.form.set(form);
        replay.keyframes.x.insert(0F, client.player.getX());
        replay.keyframes.y.insert(0F, client.player.getY());
        replay.keyframes.z.insert(0F, client.player.getZ() + 5D);
        KeyframeChannel<Anchor> channel = (KeyframeChannel<Anchor>) replay.properties.getOrCreate(form, "anchor");
        Anchor anchor = new Anchor();
        anchor.transform.rotate.set((float) Math.toRadians(20D), (float) Math.toRadians(30D), (float) Math.toRadians(45D));
        channel.insert(20F, anchor);
        if (!BBSMod.getFilms().save(FILM, film.toData().asMap())) throw new AssertionError("Cannot save the real H1 fixture");
        Film saved = BBSMod.getFilms().load(FILM);
        if (saved == null) throw new AssertionError("Cannot reload the H1 fixture before opening");
        UIDashboard dashboard = BBSModClient.getDashboard();
        UIFilmPanel panel = dashboard.getPanels().getPanel(UIFilmPanel.class);
        dashboard.setPanel(panel);
        panel.fill(saved);
        panel.replayEditor.setReplay(saved.replays.getList().get(0));
        dashboard.copyCurrentEntityCamera();
        mchorse.bbs_mod.ui.hilfe.HilfeErststart.hilfeGeoeffnet();
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }

    private static void selectAnchor()
    {
        UIFilmPanel panel = panel();
        panel.setCursor(20);
        UIKeyframes view = panel.replayEditor.keyframeEditor.view;
        UIKeyframeSheet sheet = view.getGraph().getSheets().stream().filter(value -> value.id.equals("anchor")).findFirst().orElseThrow();
        view.getGraph().clearSelection();
        int index = -1;
        for (int i = 0; i < sheet.channel.getKeyframes().size(); i++)
            if (((Keyframe<?>) sheet.channel.getKeyframes().get(i)).getTick() == 20F) index = i;
        if (index < 0) throw new AssertionError("Saved anchor key missing at20");
        sheet.selection.add(index);
        view.getGraph().pickSelected();
    }

    private static float[] authored()
    {
        KeyframeChannel<?> channel = (KeyframeChannel<?>) panel().getData().replays.getList().get(0).properties.get("anchor");
        Anchor anchor = (Anchor) channel.get(0).getValue();
        Vector3f value = anchor.transform.rotate;
        return new float[] {value.x, value.y, value.z};
    }

    private static Map<String, Object> state()
    {
        UIFilmPanel panel = panel();
        mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform transform =
            UIReplaysEditorUtils.getEditableTransform(panel.replayEditor.keyframeEditor);
        Vector3f widget = transform.getTransform().rotate;
        return Map.of("authored", authored(), "widget", new float[] {widget.x, widget.y, widget.z},
            "editing", transform.isEditing(), "historySize", panel.getUndoHandler().getUndoManager().getTotalUndos(),
            "historyIndex", panel.getUndoHandler().getUndoManager().getCurrentUndoIndex(),
            "preset", KeybindOverrides.getActivePreset());
    }

    private static double[][] ringPoints()
    {
        try
        {
            Gizmo gizmo = Gizmo.INSTANCE;
            Field matrix = Gizmo.class.getDeclaredField("lastRenderMatrix");
            Field radius = Gizmo.class.getDeclaredField("lastSphereLocalRadius");
            matrix.setAccessible(true);
            radius.setAccessible(true);
            PoseStack stack = new PoseStack();
            Method align = GizmoGimbal.class.getDeclaredMethod("ausrichten", PoseStack.class, Axis.class);
            align.setAccessible(true);
            align.invoke(null, stack, Axis.X);
            Matrix4f projection = new Matrix4f(panel().getController().getGizmoProjection())
                .mul((Matrix4f) matrix.get(gizmo)).mul(stack.last().pose());
            float r = radius.getFloat(gizmo);
            Area area = panel().getController().getGizmoArea();
            float scale = (float) Minecraft.getInstance().getWindow().getScreenWidth() / panel().getContext().menu.width;
            double[][] points = new double[72][2];

            for (int i = 0; i < points.length; i++)
            {
                double angle = i * Math.PI / 36D;
                Vector4f point = projection.transform(new Vector4f(0F, (float) Math.cos(angle) * r, (float) Math.sin(angle) * r, 1F));
                points[i][0] = Math.round((area.x + (point.x / point.w * .5F + .5F) * area.w) * scale);
                points[i][1] = Math.round((area.y + (.5F - point.y / point.w * .5F) * area.h) * scale);
            }
            return points;
        }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect the actual rendered ring", failure); }
    }

    private static boolean inside(double[] point)
    {
        return point[0] >= 0D && point[1] >= 0D && point[0] < Minecraft.getInstance().getWindow().getScreenWidth()
            && point[1] < Minecraft.getInstance().getWindow().getScreenHeight();
    }
}
