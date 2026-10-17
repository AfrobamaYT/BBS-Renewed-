package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.clips.UIBlockAnimationClip;
import com.afrobama.bbstools.clip.BlockAnimationActionClip;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Map;

/** Real offset drag and saved-film readback; the effect itself stays disabled. */
public final class BlockAnimationEditorProbe
{
    private BlockAnimationEditorProbe() {}

    public static void verify(ClientGameTestContext context)
    {
        context.runOnClient(client ->
        {
            BlockAnimationActionClip clip = block();
            panel().actionEditor.clips.setSelected(clip);
            panel().actionEditor.pickClip(clip);
        });
        context.waitTicks(3);
        double[] point = reveal(context);
        float before = context.computeOnClient(client -> block().offsetX.get());
        float siblingY = context.computeOnClient(client -> block().offsetY.get());
        float siblingZ = context.computeOnClient(client -> block().offsetZ.get());
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTick();
        context.getInput().holdMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
        try
        {
            for (int step = 1; step <= 12; step++)
            {
                context.getInput().setCursorPos(point[0] + step * 2D, point[1]);
                context.waitTick();
            }
        }
        finally { context.getInput().releaseMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0)); }
        context.waitTicks(3);
        float after = context.computeOnClient(client -> block().offsetX.get());
        if (before == after) throw new AssertionError("Actual Block Animation offset drag did not author a value");
        context.runOnClient(client ->
        {
            BlockAnimationActionClip clip = block();
            if (clip.offsetY.get() != siblingY || clip.offsetZ.get() != siblingZ || clip.enabled.get())
                throw new AssertionError("Offset drag changed a sibling or enabled effect execution");
            Film film = panel().getData();
            if (!BBSMod.getFilms().save(film.getId(), film.toData().asMap())) throw new AssertionError("Cannot save edited action film");
            Film saved = BBSMod.getFilms().load(film.getId());
            if (saved == null) throw new AssertionError("Cannot reload edited action film");
            BlockAnimationActionClip loaded = saved.replays.getAllTyped().getFirst().actions.get().stream()
                .filter(value -> BBSMod.getFactoryActionClips().getType(value).equals(Link.create("bbstools:block_animation")))
                .map(BlockAnimationActionClip.class::cast).findFirst().orElseThrow();
            if (loaded.offsetX.get() != after || loaded.offsetY.get() != siblingY || loaded.offsetZ.get() != siblingZ)
                throw new AssertionError("Edited Block Animation offsets did not survive native save/reload");
        });
        context.takeScreenshot("block-animation-offset-edited");
        System.out.println("BBS_BLOCK_ANIMATION_EDITOR " + new com.google.gson.Gson().toJson(
            Map.of("beforeX", before, "afterX", after, "siblingY", siblingY, "siblingZ", siblingZ,
                "nativeFilmSaveReload", true, "effectExecuted", false)));
    }

    private static BlockAnimationActionClip block()
    {
        return panel().replayEditor.getReplay().actions.get().stream()
            .filter(clip -> BBSMod.getFactoryActionClips().getType(clip).equals(Link.create("bbstools:block_animation")))
            .map(BlockAnimationActionClip.class::cast).findFirst().orElseThrow();
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }

    private static double[] reveal(ClientGameTestContext context)
    {
        for (int attempt = 0; attempt < 35; attempt++)
        {
            double[] point = context.computeOnClient(client ->
            {
                UIBlockAnimationClip editor = panel().getChildren(UIBlockAnimationClip.class).stream()
                    .filter(ui -> ui.clip == block()).findFirst().orElseThrow();
                UIElement target = editor.offsetX;
                Area field = target.area, viewport = editor.panels.area;
                double y = field.y - editor.panels.scroll.getScroll();
                float scale = (float) client.getWindow().getScreenWidth() / panel().getContext().menu.width;
                return new double[] {(field.x + field.w / 2D) * scale, (y + field.h / 2D) * scale,
                    y >= viewport.y && y + field.h <= viewport.ey() ? 1 : 0,
                    (viewport.x + 1D) * scale, (viewport.y + 8D) * scale, y < viewport.y ? 2 : -2};
            });
            if (point[2] == 1) return point;
            context.getInput().setCursorPos(point[3], point[4]);
            context.getInput().scroll(point[5]);
            context.waitTicks(3);
        }
        throw new AssertionError("Block Animation offset field is clipped after actual wheel input");
    }
}
