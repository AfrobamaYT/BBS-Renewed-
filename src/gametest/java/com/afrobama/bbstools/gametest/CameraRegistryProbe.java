package com.afrobama.bbstools.gametest;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIZeitleisteNano;
import mchorse.bbs_mod.ui.film.ZeitleisteZweig;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.utils.clips.Clip;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Camera/media selection and native data contracts; evaluation and playback are separate tests. */
public final class CameraRegistryProbe
{
    private CameraRegistryProbe() {}

    public static void verify(ClientGameTestContext context)
    {
        context.runOnClient(client ->
        {
            if (!(panel().zeitleisteNano instanceof UIZeitleisteNano timeline)) throw new AssertionError("Require the real Nano timeline");
            if (timeline.modus != 2) timeline.modusUmschalten(2);
            ZeitleisteZweig.anwenden(panel(), 2);
        });
        List<Link> ids = context.computeOnClient(client -> new ArrayList<>(BBSMod.getFactoryCameraClips().getKeys()));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Link id : ids)
        {
            Map<String, Object> row = context.computeOnClient(client -> select(id));
            rows.add(row);
            if (!Boolean.TRUE.equals(row.get("selected")) || !Boolean.TRUE.equals(row.get("payloadPreserved"))
                || !Boolean.TRUE.equals(row.get("typeIdPreserved")) || !Boolean.TRUE.equals(row.get("copyClass")))
                failures.add(id.toString());
            context.waitTicks(2);
            if (id.toString().startsWith("bbstools:") || !Boolean.TRUE.equals(row.get("selected")))
                context.takeScreenshot("camera-registry-" + id.toString().replace(':', '-'));
        }
        System.out.println("BBS_CAMERA_REGISTRY " + new com.google.gson.Gson().toJson(Map.of(
            "rows", rows, "failures", failures, "scope", "selection/native data/copy; clips disabled, media and camera evaluation untested")));
        if (!failures.isEmpty()) throw new AssertionError("Camera/media editor or native data contract failed: " + failures);
        context.runOnClient(client ->
        {
            Film film = panel().getData();
            if (!BBSMod.getFilms().save(film.getId(), film.toData().asMap())) throw new AssertionError("Cannot save camera-registry film");
            Film restored = BBSMod.getFilms().load(film.getId());
            if (restored == null || !Arrays.equals(DataStorageUtils.writeToBytes(film.camera.toData()),
                DataStorageUtils.writeToBytes(restored.camera.toData())))
                throw new AssertionError("Complete camera/media timeline changed during native film save/load");
        });
        System.out.println("BBS_CAMERA_REGISTRY_FILM_SAVE_RELOAD true");
    }

    private static Map<String, Object> select(Link id)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inputId", id.toString());
        try
        {
            UIClipsPanel editor = panel().cameraEditor;
            Clip original = BBSMod.getFactoryCameraClips().create(id);
            if (original instanceof CameraClip camera) camera.fromCamera(editor.getCamera());
            original.enabled.set(false);
            original.title.set("Camera audit " + id);
            original.duration.set(37);
            MapType data = BBSMod.getFactoryCameraClips().toData(original);
            data.putString("type", id.toString());
            Clip loaded = BBSMod.getFactoryCameraClips().fromData(
                DataStorageUtils.readFromBytes(DataStorageUtils.writeToBytes(data)).asMap());
            row.put("class", loaded.getClass().getName());
            row.put("savedId", BBSMod.getFactoryCameraClips().getType(loaded).toString());
            row.put("typeIdPreserved", id.equals(BBSMod.getFactoryCameraClips().getType(loaded)));
            row.put("payloadPreserved", Arrays.equals(DataStorageUtils.writeToBytes(original.toData()),
                DataStorageUtils.writeToBytes(loaded.toData())));
            row.put("copyClass", loaded.copy().getClass() == loaded.getClass());
            loaded.layer.set(panel().getData().camera.get().size());
            panel().getData().camera.addClip(loaded);
            editor.clips.setSelected(loaded);
            editor.pickClip(loaded);
            List<UIClip> controls = panel().getChildren(UIClip.class).stream().filter(ui -> ui.clip == loaded).toList();
            row.put("selected", editor.getClip() == loaded && controls.size() == 1);
            row.put("panel", controls.isEmpty() ? "missing" : controls.getFirst().getClass().getName());
        }
        catch (RuntimeException failure)
        {
            row.put("error", failure.toString());
        }
        return row;
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }
}
