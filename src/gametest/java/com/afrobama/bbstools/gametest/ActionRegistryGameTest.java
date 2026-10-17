package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.dashboard.AnnouncementGate;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIZeitleisteNano;
import mchorse.bbs_mod.ui.film.ZeitleisteZweig;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All offered actions must select an editor and retain data through their registered IDs. */
public final class ActionRegistryGameTest implements FabricClientGameTest
{
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
            context.runOnClient(ActionRegistryGameTest::prepare);
            context.waitTicks(5);
            List<Link> types = context.computeOnClient(client -> new ArrayList<>(BBSMod.getFactoryActionClips().getKeys()));
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (Link type : types)
            {
                Map<String, Object> row = context.computeOnClient(client -> select(type));
                rows.add(row);
                if (!Boolean.TRUE.equals(row.get("selected")) || !Boolean.TRUE.equals(row.get("dataRoundTrip"))
                    || !Boolean.TRUE.equals(row.get("copyClass")) || !Boolean.TRUE.equals(row.get("hasActionHandler"))
                    || !Boolean.TRUE.equals(row.get("typeIdPreserved")))
                    failures.add(type.toString());
                context.waitTicks(2);
                if (type.toString().startsWith("bbstools:") || type.toString().equals("bbs:block_animation"))
                    context.takeScreenshot("action-registry-" + type.toString().replace(':', '-'));
            }
            System.out.println("BBS_ACTION_REGISTRY " + new com.google.gson.Gson().toJson(
                Map.of("rows", rows, "failures", failures, "scope", "editor selection, native data and method resolution; effects disabled")));
            if (!failures.isEmpty()) throw new AssertionError("Offered actions lack an editor/handler or lose data: " + failures);
            BlockAnimationEditorProbe.verify(context);
        }
    }

    private static Map<String, Object> select(Link type)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inputId", type.toString());
        try
        {
            ActionClip original = (ActionClip) BBSMod.getFactoryActionClips().create(type);
            original.enabled.set(false);
            original.title.set("Audit " + type);
            original.frequency.set(7);
            original.duration.set(31);
            MapType data = BBSMod.getFactoryActionClips().toData(original);
            data.putString("type", type.toString());
            ActionClip loaded = (ActionClip) BBSMod.getFactoryActionClips().fromData(
                DataStorageUtils.readFromBytes(DataStorageUtils.writeToBytes(data)).asMap());
            boolean retained = Arrays.equals(DataStorageUtils.writeToBytes(original.toData()),
                DataStorageUtils.writeToBytes(loaded.toData()));
            Class<?> owner = actionOwner(loaded);
            UIClipsPanel editor = panel().actionEditor;
            Replay replay = panel().replayEditor.getReplay();
            loaded.layer.set(replay.actions.get().size());
            replay.actions.addClip(loaded);
            editor.clips.setSelected(loaded);
            editor.pickClip(loaded);
            List<UIClip> controls = panel().getChildren(UIClip.class).stream().filter(ui -> ui.clip == loaded).toList();
            row.put("class", loaded.getClass().getName());
            row.put("savedId", BBSMod.getFactoryActionClips().getType(loaded).toString());
            row.put("typeIdPreserved", type.equals(BBSMod.getFactoryActionClips().getType(loaded)));
            row.put("dataRoundTrip", retained);
            row.put("copyClass", loaded.copy().getClass() == loaded.getClass());
            row.put("handlerOwner", owner.getName());
            row.put("hasActionHandler", owner != ActionClip.class);
            row.put("selected", editor.getClip() == loaded && controls.size() == 1);
            row.put("panel", controls.isEmpty() ? "missing" : controls.getFirst().getClass().getName());
        }
        catch (ReflectiveOperationException | RuntimeException failure)
        {
            row.put("error", failure.toString());
        }
        return row;
    }

    private static Class<?> actionOwner(ActionClip clip) throws NoSuchMethodException
    {
        if (!clip.isClient()) return clip.getClass().getMethod("applyAction", LivingEntity.class,
            SuperFakePlayer.class, Film.class, Replay.class, int.class).getDeclaringClass();
        for (Class<?> type = clip.getClass(); type != null; type = type.getSuperclass())
        {
            try { return type.getDeclaredMethod("applyClientAction", IEntity.class, Film.class, Replay.class, int.class).getDeclaringClass(); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("Action has no client handler: " + clip.getClass());
    }

    private static void prepare(Minecraft client)
    {
        BBSSettings.userIntefaceScale.set(2F);
        Film film = new Film();
        film.setId("ci_action_registry");
        Camera camera = new Camera();
        camera.set(client.player, MathUtils.toRad(client.options.fov().get()));
        IdleClip idle = new IdleClip();
        idle.fromCamera(camera);
        idle.duration.set(80);
        film.camera.addClip(idle);
        Replay replay = film.replays.addReplay();
        BBSModClient.getDashboard().setPanel(panel());
        panel().fill(film);
        BBSModClient.getDashboard().copyCurrentEntityCamera();
        mchorse.bbs_mod.ui.hilfe.HilfeErststart.hilfeGeoeffnet();
        panel().replayEditor.setReplay(replay);
        if (!(panel().zeitleisteNano instanceof UIZeitleisteNano timeline)) throw new AssertionError("Require the actual Nano timeline");
        if (timeline.modus != 3) ZeitleisteZweig.aktionenModus(panel());
        ZeitleisteZweig.anwenden(panel(), 3);
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }
}
