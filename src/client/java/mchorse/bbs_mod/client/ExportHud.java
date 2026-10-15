package mchorse.bbs_mod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;

import java.util.List;

/**
 * The tracker clip's HUD in the exported video. 26.3 draws the GUI into the window after the
 * frame, while the export reads the film buffer, so a "Show HUD" tracker exported without hotbar
 * and hearts (BBS FS 1.21.1 had them; reported 2026-10-14). As Flashback's HUD export does, the
 * HUD gets its own GUI pass into the frame the recorder reads: a second GuiRenderer with its own
 * state, the window state set to the film buffer's size for the pass, the GUI scale picked the
 * way the tracker's HUD scale restore picks it for the video's size.
 */
public final class ExportHud
{
    private static GuiRenderState zustand;
    private static GuiRenderer zeichner;
    private static boolean aktiv;

    private ExportHud()
    {}

    /** While true, the main target is the film buffer (SpielRendererLogik.filmpuffer). */
    public static boolean aktiv()
    {
        return aktiv;
    }

    public static void zeichnen()
    {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget film = BBSRendering.getFramebuffer();

        /* Exactly the frames BBSModClient.captureFinishedFrame records. */
        if (mc.level == null || !BBSModClient.getVideoRecorder().isRecording() || !BBSRendering.canRender
            || film == null || BBSModClient.getCameraController().getCurrent() == null || !TrackerClientClip.shouldRenderHud())
        {
            return;
        }

        if (zeichner == null)
        {
            zustand = new GuiRenderState();
            zeichner = new GuiRenderer(zustand, mc.gameRenderer.featureRenderDispatcher(), List.of());
        }

        WindowRenderState fenster = mc.gameRenderer.gameRenderState().windowRenderState;
        int breite = fenster.width, hoehe = fenster.height, skala = fenster.guiScale;
        int neueSkala = skala(film.width, film.height);
        boolean versteckt = HudSichtbarkeit.istVersteckt();

        zustand.reset();

        /* The two ints are the mouse position (none here); the HUD lays itself out by the
         * WINDOW's GUI size (GuiGraphics.guiWidth reads Window.getGuiScaledWidth), so
         * that is set to the video's for the extraction. Left at the window's, the hotbar sat
         * below the video's bottom edge: 46 elements extracted, none in the frame. */
        GuiGraphics hud = new GuiGraphics(mc, zustand, -1, -1);

        HudSichtbarkeit.verstecken(false);
        TrackerClientClip.beginHudRendering(hud);

        int[] gespeichert = guiGroesse(new int[] {(int) Math.ceil(film.width / (double) neueSkala),
            (int) Math.ceil(film.height / (double) neueSkala), neueSkala});

        /* No crosshair, as in BBS FS's export: the HUD draws it only in first person. */
        net.minecraft.client.CameraType perspektive = mc.options.getCameraType();

        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);

        try
        {
            mc.gui.render(hud, mc.getTimer());
        }
        finally
        {
            mc.options.setCameraType(perspektive);
            guiGroesse(gespeichert);
            TrackerClientClip.endHudRendering(hud);
            HudSichtbarkeit.verstecken(versteckt);
        }

        fenster.width = film.width;
        fenster.height = film.height;
        fenster.guiScale = neueSkala;
        aktiv = true;

        try
        {
            zeichner.render();
        }
        finally
        {
            aktiv = false;
            fenster.width = breite;
            fenster.height = hoehe;
            fenster.guiScale = skala;
            zeichner.endFrame();
        }
    }

    /** Sets the window's GUI width, height and scale; returns the previous ones. */
    private static int[] guiGroesse(int[] werte)
    {
        try
        {
            var fenster = Minecraft.getInstance().getWindow();
            String[] namen = {"guiScaledWidth", "guiScaledHeight", "guiScale"};
            int[] vorher = new int[3];

            for (int i = 0; i < 3; i++)
            {
                var feld = fenster.getClass().getDeclaredField(namen[i]);

                feld.setAccessible(true);
                vorher[i] = feld.getInt(fenster);
                feld.setInt(fenster, werte[i]);
            }

            return vorher;
        }
        catch (ReflectiveOperationException fehler)
        {
            throw new IllegalStateException("Minecraft changed: cannot size the export HUD", fehler);
        }
    }

    /** The scale vanilla would pick for a window of this size, capped by the player's GUI scale option. */
    private static int skala(int w, int h)
    {
        int gewaehlt = Minecraft.getInstance().options.guiScale().get();
        int s = 1;

        while (s != gewaehlt && s < w && s < h && w / (s + 1) >= 320 && h / (s + 1) >= 240)
        {
            s++;
        }

        return s;
    }
}
