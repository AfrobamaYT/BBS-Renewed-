// Coded by Afrobama/Claude — Phase 15 v6: runs BBS stencil pick at alt-cam POV per-frame
package com.afrobama.bbstools.client.campreview;

import net.minecraft.client.renderer.state.CameraRenderState;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Pair;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import io.netty.util.collection.IntObjectMap;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Runs BBS's stencil-pick pipeline for a single alt-cam clip's pending
 * hover/click request and writes the picked {@code (Form, bone-name)}
 * pair back to {@link BoneHoverPickService} so the tile can display it.
 *
 * <p>Called from the pre-pass mixin while the camera is already mutated
 * to the alt-cam POV. We bind our per-clip {@link StencilFormFramebuffer},
 * setup a {@link StencilMap}, render each entity in every film via
 * {@link BaseFilmController#renderEntity(FilmControllerContext)} with
 * the stencil map attached, then sample the stencil at the translated
 * mouse position.</p>
 *
 * <p>The chat broadcast on click is a Phase 15 v6 placeholder — Phase
 * v7 will hook into {@code UIPoseEditor}/{@code UIFilmController} to
 * actually drive the editor's selection state.</p>
 */
public final class BoneHoverPickRunner
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/bone-hover-runner");

    private BoneHoverPickRunner() {}

    /** Run the stencil pick if there's a pending hover request for the
     *  given clip. Writes back to {@link BoneHoverPickService#RESULTS}.
     *  Caller must have already bound a 3D render context (camera
     *  mutated + projection set). */
    public static void runHover(UUID clipId, WorldRenderContext ctx, int fbW, int fbH)
    {
        BoneHoverPickService.Request req = BoneHoverPickService.REQUESTS.get(clipId);
        if (req == null) return;

        Pair<Form, String> picked = doPick(clipId, ctx, req, fbW, fbH);
        StencilFormFramebuffer stencil = BoneHoverPickService.getStencil(clipId);
        int stencilTex = 0;
        int sw = fbW, sh = fbH, idx = 0;
        if (stencil != null && stencil.getFramebuffer() != null)
        {
            Texture mt = stencil.getFramebuffer().getMainTexture();
            stencilTex = mt.id;
            sw = mt.width;
            sh = mt.height;
            idx = stencil.getIndex();
        }
        BoneHoverPickService.RESULTS.put(clipId,
            new BoneHoverPickService.Result(picked != null ? picked.a : null,
                                            picked != null ? picked.b : null,
                                            stencilTex, sw, sh, idx));
    }

    /** Run the same stencil pick for a one-shot CLICK. Selects the
     *  picked Form+bone in BBS's replay editor (UIPoseEditor via
     *  pickForm), opens the editor panel if hidden, and broadcasts
     *  the pick to chat.
     *
     *  <p>Modifiers:</p>
     *  <ul>
     *    <li>Shift: bypass {@code replayMarkedBonesOnly} so any bone
     *        is pickable (matches BBS's main-preview behavior).</li>
     *    <li>Ctrl: after picking, walk one level up the bone hierarchy
     *        — useful for grabbing the limb when fingertip was under
     *        the cursor.</li>
     *  </ul>
     */
    public static void runClick(UUID clipId, WorldRenderContext ctx, int fbW, int fbH)
    {
        BoneHoverPickService.Request req = BoneHoverPickService.CLICKS.remove(clipId);
        if (req == null) return;

        Pair<Form, String> picked = doPick(clipId, ctx, req, fbW, fbH);

        if (req.ctrl && picked != null && picked.a != null && picked.b != null && !picked.b.isEmpty())
        {
            String parent = findParentBone(picked.a, picked.b);
            if (parent != null && !parent.isEmpty())
            {
                picked = new Pair<>(picked.a, parent);
            }
        }

        announce(picked);
        applyPickToBBSEditor(picked);
    }

    /** Walks one level up the rig's bone hierarchy. Returns the parent
     *  bone name or {@code null} if the picked bone is the root or the
     *  form isn't a model. */
    private static String findParentBone(Form form, String boneName)
    {
        try
        {
            if (!(form instanceof mchorse.bbs_mod.forms.forms.ModelForm modelForm)) return null;
            mchorse.bbs_mod.cubic.ModelInstance model =
                mchorse.bbs_mod.forms.renderers.ModelFormRenderer.getModel(modelForm);
            if (model == null || model.model == null) return null;

            /* Walk the ModelInstance's armature bone map. The exact
             *  accessor differs between cubic-model types, so we look
             *  up via reflection-free public field paths that exist on
             *  the bobj path. If model.model has a getArmature() it
             *  returns a BOBJArmature with a public bones Map. */
            Object cubicModel = model.model;
            mchorse.bbs_mod.bobj.BOBJArmature armature = null;
            try
            {
                java.lang.reflect.Method m = cubicModel.getClass().getMethod("getArmature");
                Object result = m.invoke(cubicModel);
                if (result instanceof mchorse.bbs_mod.bobj.BOBJArmature ba)
                {
                    armature = ba;
                }
            }
            catch (Throwable ignored) {}

            if (armature == null || armature.bones == null) return null;
            mchorse.bbs_mod.bobj.BOBJBone bone = armature.bones.get(boneName);
            if (bone == null) return null;
            /* BOBJBone.parent holds the parent's NAME string directly
             *  (BOBJBone.parentBone is the resolved Bone reference). */
            return bone.parent == null || bone.parent.isEmpty() ? null : bone.parent;
        }
        catch (Throwable ignored) {}
        return null;
    }

    /** When the user clicks a bone in the alt-cam tile, drive BBS's
     *  replay editor exactly the same way as a click in the main
     *  preview would: select the replay, open the editor panel if
     *  hidden, then call {@code UIReplaysEditor.pickForm(form, bone)}
     *  so the pose editor focuses on the picked bone. */
    private static void applyPickToBBSEditor(Pair<Form, String> picked)
    {
        if (picked == null || picked.a == null) return;
        try
        {
            mchorse.bbs_mod.ui.film.UIFilmPanel panel = findEditedFilmPanel();
            if (panel == null) return;
            mchorse.bbs_mod.ui.film.controller.UIFilmController editorCtl = panel.getController();
            if (editorCtl == null) return;

            IEntity selectedEntity = editorCtl.getCurrentEntity();
            if (selectedEntity == null) return;

            IntObjectMap<IEntity> entities = editorCtl.getEntities();
            if (entities == null) return;

            int selectedIdx = -1;
            for (io.netty.util.collection.IntObjectMap.PrimitiveEntry<IEntity> entry : entities.entries())
            {
                if (entry.value() == selectedEntity)
                {
                    selectedIdx = entry.key();
                    break;
                }
            }
            if (selectedIdx < 0) return;

            mchorse.bbs_mod.film.Film film = panel.getData();
            if (film == null || film.replays == null) return;
            Replay replay = CollectionUtils.getSafe(film.replays.getList(), selectedIdx);
            if (replay == null) return;

            panel.replayEditor.setReplay(replay);
            if (!panel.replayEditor.isVisible())
            {
            }

            /* The key call — wires the bone into BBS's pose editor. */
            panel.replayEditor.pickForm(picked.a, picked.b == null ? "" : picked.b);

            LOG.info("Applied pick to BBS editor: form={} bone={}",
                picked.a.getFormIdOrName(), picked.b);
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to apply pick to BBS editor: " + t, t);
        }
    }

    private static Pair<Form, String> doPick(UUID clipId, WorldRenderContext ctx,
                                              BoneHoverPickService.Request req,
                                              int fbW, int fbH)
    {
        StencilFormFramebuffer stencil = BoneHoverPickService.getStencil(clipId);
        if (stencil == null) return null;

        CameraRenderState altCam = ctx.gameRenderer().getLevelRenderState().cameraRenderState;
        if (altCam == null) return null;

        /* Use whatever GL modelview the visible alt-cam render used —
         *  identity at pre-pass entry, which evidently works for the
         *  visible render (user sees the model). Pushing our own MV
         *  caused stencil-render positions to mismatch the visible
         *  positions, so hasPicked=false at every cursor location.
         *
         *  We still set BBSRendering.camera = rotation-only-view so
         *  BBS form renderers that read it (relative-mode shadow etc.)
         *  see alt-cam orientation. */
        Matrix4f savedBBSCamera = new Matrix4f(BBSRendering.camera);

        float pitchRad = (float) Math.toRadians(altCam.bbsXRot());
        float yawRad = (float) Math.toRadians(altCam.bbsYRot() + 180F);

        Matrix4f altCamView = new Matrix4f()
            .identity()
            .rotateX(pitchRad)
            .rotateY(yawRad);

        BBSRendering.camera.set(altCamView);

        try
        {
            stencil.setup(Link.bbs("bbstools_alt_cam_pick_" + clipId.toString().substring(0, 8)));

            /* resizeGUI(w,h) multiplies by BBSModClient.getGUIScale() — that
             * gives us a 1280×720 (or worse) framebuffer when we want
             * 640×360. Use the explicit-scale resize with scale=1 so
             * stencil pixel space matches our pick-coord computation. */
            Texture mainTexture = stencil.getFramebuffer().getMainTexture();
            if (mainTexture.width != fbW || mainTexture.height != fbH)
            {
                stencil.resize(fbW, fbH); /* AUTONOMIE 2026-07-27: fs' resize nimmt nur w,h (kein sample-count) */
                LOG.info("Resized stencil framebuffer for clip {} to {}x{} (was {}x{})",
                    clipId, fbW, fbH, mainTexture.width, mainTexture.height);
            }

            StencilMap map = new StencilMap();
            map.setup();
            map.setIncrement(true);
            stencil.apply();

            /* Render ONLY the currently-selected entity in the
             *  UIFilmPanel — matches BBS's own non-alt stencil pass.
             *  Picking all entities causes their bone-index ranges to
             *  overlap (Target + texCoord2.x), so every pick collides
             *  on the anchor/root bone. */
            mchorse.bbs_mod.ui.film.UIFilmPanel panel = findEditedFilmPanel();
            if (panel == null)
            {
                LOG.warn("Pick for clip {}: no UIFilmPanel open", clipId);
                return null;
            }
            mchorse.bbs_mod.ui.film.controller.UIFilmController editorCtl = panel.getController();
            if (editorCtl == null)
            {
                LOG.warn("Pick for clip {}: UIFilmPanel has no controller", clipId);
                return null;
            }

            IEntity selectedEntity = editorCtl.getCurrentEntity();
            if (selectedEntity == null)
            {
                LOG.warn("Pick for clip {}: no currently-selected entity", clipId);
                return null;
            }

            IntObjectMap<IEntity> editorEntities = editorCtl.getEntities();
            if (editorEntities == null)
            {
                LOG.warn("Pick for clip {}: no entities map", clipId);
                return null;
            }

            int selectedIdx = -1;
            for (io.netty.util.collection.IntObjectMap.PrimitiveEntry<IEntity> entry : editorEntities.entries())
            {
                if (entry.value() == selectedEntity)
                {
                    selectedIdx = entry.key();
                    break;
                }
            }
            if (selectedIdx < 0)
            {
                LOG.warn("Pick for clip {}: selected entity not in entities map", clipId);
                return null;
            }

            Film selectedFilm = panel.getData();
            if (selectedFilm == null || selectedFilm.replays == null)
            {
                LOG.warn("Pick for clip {}: no film/replays on panel", clipId);
                return null;
            }
            Replay selectedReplay = CollectionUtils.getSafe(selectedFilm.replays.getList(), selectedIdx);
            if (selectedReplay == null)
            {
                LOG.warn("Pick for clip {}: no replay at index {}", clipId, selectedIdx);
                return null;
            }

            map.objectIndex = 7;

            /* Apply BBS's "marked bones only" filter unless the user
             *  held Shift (modifier override). Pre-Phase-15-v7 we
             *  always picked from all bones; that's actually less
             *  ergonomic for rigs with marked-bone setups. */
            map.allowedBones = null;
            if (!req.shift)
            {
                try
                {
                    if (mchorse.bbs_mod.BBSSettings.replayMarkedBonesOnly.get()
                        && selectedReplay.form.get() instanceof mchorse.bbs_mod.forms.forms.ModelForm modelForm)
                    {
                        mchorse.bbs_mod.cubic.ModelInstance modelInst =
                            mchorse.bbs_mod.forms.renderers.ModelFormRenderer.getModel(modelForm);
                        String poseGroup = modelInst == null ? modelForm.model.get() : modelInst.config.poseGroup.get();
                        if (poseGroup == null || poseGroup.isEmpty())
                        {
                            poseGroup = modelInst == null ? modelForm.model.get() : modelInst.id;
                        }
                        if (mchorse.bbs_mod.ui.utils.pose.UIPoseEditor.hasMarkedBones(poseGroup))
                        {
                            map.allowedBones = mchorse.bbs_mod.ui.utils.pose.UIPoseEditor.getMarkedBones(poseGroup);
                        }
                    }
                }
                catch (Throwable ignored) {}
            }

            PoseStack tempStack = new PoseStack();
            float tickDelta = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

            BaseFilmController.renderEntity(FilmControllerContext.instance
                .setup(editorEntities, selectedEntity, selectedReplay, altCam, tempStack,
                    /* 26.2: bufferSource gibt es nicht mehr -- der Kontext traegt den Sammler.
                     * Er kommt von der Stelle, die diesen Durchgang aufgemacht hat. */
                    new mchorse.bbs_mod.compat.gpu.SofortSammler(ctx.consumers()),
                    tickDelta)
                .transition(tickDelta)
                .stencil(map)
                .relative(selectedReplay.relative.get()));

            int totalEntitiesRendered = 1;

            /* ⚠️ 26.2: HIER FEHLT DIE HALBE MECHANIK, und das ist der Grund, warum der Picker
             * derzeit nicht aufgerufen wird (siehe GameRendererPreviewPrePassLogik).
             *
             * Frueher stand hier ein endBatch: die Stencil-Zuege in den Rahmenpuffer druecken,
             * damit das glReadPixels darunter etwas VORFINDET. In 26.2 wird Geometrie
             * eingereicht und vom Bildgraphen spaeter gezeichnet -- es gibt kein "jetzt
             * fertigzeichnen" mehr. Ohne Ersatz liest der Picker einen Puffer, in dem noch
             * nichts steht, und trifft immer daneben.
             *
             * Der ehrliche Weg zurueck: eigener Durchgang fuer den Stencil, danach ein Zaun
             * (RenderSystem.queueFencedTask), erst dann lesen. Das ist ein Umbau, kein
             * Nachschlag -- deshalb steht hier nichts Erfundenes. */

            int px = (int) ((req.mouseX / (float) req.tileW) * fbW);
            int py = (int) ((1F - req.mouseY / (float) req.tileH) * fbH);

            stencil.pick(px, py);
            stencil.unbind(map);

            /* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
             * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
             * zeichnen will, gibt das Ziel dort an. */

            boolean picked = stencil.hasPicked();
            LOG.info("Pick clip={} entities={} mouseTile=({},{}) stencilPx=({},{}) hasPicked={} picked={}",
                clipId, totalEntitiesRendered, req.mouseX, req.mouseY, px, py, picked,
                picked ? stencil.getPicked() : null);
            return picked ? stencil.getPicked() : null;
        }
        catch (Throwable t)
        {
            LOG.warn("Stencil pick FAILED for clip " + clipId, t);
            return null;
        }
        finally
        {
            BBSRendering.camera.set(savedBBSCamera);
        }
    }

    /** Public alias for the editor panel lookup so tiles can reach it
     *  without exposing the internal name. */
    public static mchorse.bbs_mod.ui.film.UIFilmPanel findPanel()
    {
        return findEditedFilmPanel();
    }

    /** Walks the open BBS UIScreen for a UIFilmPanel — same lookup
     *  pattern as {@code ActiveCameraClipRegistry.findEditedFilmPanel}.
     *  Returns null when the dashboard isn't open or no film panel
     *  is active. */
    private static mchorse.bbs_mod.ui.film.UIFilmPanel findEditedFilmPanel()
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !(mc.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen)) return null;
            mchorse.bbs_mod.ui.framework.UIScreen screen = (mchorse.bbs_mod.ui.framework.UIScreen) mc.gui.screen();
            mchorse.bbs_mod.ui.framework.UIBaseMenu menu = screen.getMenu();
            if (menu == null) return null;
            mchorse.bbs_mod.ui.framework.UIBaseMenu.UIRootElement root = menu.getRoot();
            if (root == null) return null;
            java.util.List<mchorse.bbs_mod.ui.film.UIFilmPanel> panels =
                root.getChildren(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
            if (panels != null && !panels.isEmpty()) return panels.get(0);
        }
        catch (Throwable ignored) {}
        return null;
    }

    /** Bundle of (Film, entity-map) so we can iterate either playback
     *  or edit-mode sources uniformly. */
    private static final class EntitiesSource
    {
        final Film film;
        final IntObjectMap<IEntity> entities;

        EntitiesSource(Film film, IntObjectMap<IEntity> entities)
        {
            this.film = film;
            this.entities = entities;
        }
    }

    private static void announce(Pair<Form, String> picked)
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            LocalPlayer player = mc.player;
            if (player == null) return;
            if (picked == null || picked.a == null)
            {
                player.sendOverlayMessage(Component.literal("§7[BBSTools] §oNo bone under cursor"));
                return;
            }
            String msg = "§a[BBSTools] §fPicked: §b" + picked.a.getFormIdOrName()
                + (picked.b != null && !picked.b.isEmpty() ? " §7— §e" + picked.b : "");
            player.sendOverlayMessage(Component.literal(msg));
        }
        catch (Throwable ignored) {}
    }

}
