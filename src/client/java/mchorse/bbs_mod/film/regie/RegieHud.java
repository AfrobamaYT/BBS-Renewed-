package mchorse.bbs_mod.film.regie;

import com.mcstudio.nano.NanoEbene;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Bindet die Bildschirmschicht der Regiehilfe an: im Spiel beim Aufnehmen (HUD) und im
 * Film-Editor ueber dem Vorschaufeld. Gemalt wird je Bild mit der Kamera DIESES Bildes.
 *
 * <p>⛔ DIE KAMERA KOMMT AUS DEM GESAMMELTEN ZUSTAND, NICHT AUS EINEM WELT-HAKEN (gemessen
 * 2026-10-11). 26.3 sammelt erst alles ({@code GameRenderer.extract}: Kamera, Welt, HUD) und
 * zeichnet danach ({@code render} -> {@code renderLevel}). Ein {@code END_MAIN}-Haken liefert
 * dem HUD deshalb die Kamera des VORBILDS: im Video lagen die Karten beim Drehen ein Bild hinter
 * der Welt. Beim HUD-Aufbau steht die Kamera dieses Bildes schon im Zustand — Vanilla liest sie
 * fuer {@code projectPointToScreen} genauso.</p>
 */
public final class RegieHud
{
    private static final RegieKamera KAMERA = new RegieKamera();
    private static final RegieAnsicht SPIEL = new RegieAnsicht();
    private static final RegieAnsicht EDITOR = new RegieAnsicht();
    private static final RegieZeichner ZEICHNER = new RegieZeichner();
    private static final NanoEbene SPIELEBENE = new NanoEbene();
    private static final NanoEbene EDITOREBENE = new NanoEbene();
    private static Recorder letzteAufnahme;

    private RegieHud()
    {
    }

    /** Kamera dieses Bildes aus dem gesammelten Zustand (Spiel wie Editor). */
    private static boolean kameraDiesesBildes()
    {
        CameraRenderState kamera = Minecraft.getInstance().gameRenderer.getLevelRenderState().cameraRenderState;

        if (kamera == null || !kamera.initialized)
        {
            return false;
        }

        KAMERA.setzen(kamera.bbsProjectionMatrix(), kamera.bbsViewRotationMatrix(), kamera.pos.x, kamera.pos.y, kamera.pos.z);

        return true;
    }

    /** Im Spiel waehrend einer Aufnahme (aus {@code Films#renderHud}). */
    public static void aufnahme(Batcher2D batcher, Recorder aufnahme, float tickDelta)
    {
        Regie regie = aufnahme.film.regie;

        if (leer(regie) || !kameraDiesesBildes())
        {
            return;
        }

        if (aufnahme != letzteAufnahme)
        {
            letzteAufnahme = aufnahme;
            SPIEL.treffer.leeren();
        }

        Minecraft mc = Minecraft.getInstance();
        int breite = mc.gameRenderer.mainRenderTarget().width;
        int hoehe = mc.gameRenderer.mainRenderTarget().height;
        boolean laeuft = !aufnahme.hasNotStarted();
        float tick = aufnahme.getTick() + (laeuft ? tickDelta : 0F);

        SPIEL.treffer.spielerDa = mc.player != null;

        if (mc.player != null)
        {
            SPIEL.treffer.spielerX = mc.player.getX();
            SPIEL.treffer.spielerZ = mc.player.getZ();
        }

        Texture textur = SPIELEBENE.malen(breite, hoehe, (vg, b, h) ->
        {
            float k = h / 1080F;

            RegieZeichner.schriftenAnmelden(vg);
            SPIEL.berechnen(regie, tick, true, laeuft, KAMERA, b, h, k, ZEICHNER.messer(vg), RegieHud::verdeckt);
            ZEICHNER.zeichnen(vg, SPIEL, zeit(), k);
        });

        aufblenden(batcher, SPIELEBENE, textur, 0F, 0F,
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /** Im Film-Editor ueber dem Vorschaufeld (Planen: Karten, Startpunkt, Wege). */
    public static void editor(UIContext context, UIFilmPanel panel, float x, float y, float w, float h)
    {
        if (panel.getData() == null || leer(panel.getData().regie) || !kameraDiesesBildes())
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        float skala = mchorse.bbs_mod.BBSModClient.getGUIScale();
        int breite = Math.max(1, Math.round(w * skala));
        int hoehe = Math.max(1, Math.round(h * skala));
        float k = mc.getWindow().getHeight() / 1080F;
        float tick = panel.getCursor() + (panel.isRunning() ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0F);
        Regie regie = panel.getData().regie;

        RegieWelt.editorMelden(panel);
        EDITOR.wege = RegieWeltWege::weg;

        Texture textur = EDITOREBENE.malen(breite, hoehe, (vg, b, hh) ->
        {
            RegieZeichner.schriftenAnmelden(vg);
            EDITOR.berechnen(regie, tick, false, false, KAMERA, b, hh, k, ZEICHNER.messer(vg), RegieHud::verdeckt);
            ZEICHNER.zeichnen(vg, EDITOR, zeit(), k);
        });

        context.batcher.flush();
        aufblenden(context.batcher, EDITOREBENE, textur, x, y, w, h);
    }

    private static boolean leer(Regie regie)
    {
        return regie.marker.getList().isEmpty() && !regie.startGesetzt.get();
    }

    private static void aufblenden(Batcher2D batcher, NanoEbene ebene, Texture textur, float x, float y, float w, float h)
    {
        if (textur == null)
        {
            return;
        }

        boolean kopfueber = ebene.kopfueber();

        /* NanoVG schreibt vormultipliziert — normales Alpha-Mischen dunkelte Leuchten und Grund doppelt ab. */
        batcher.texturedBox(() -> RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, textur, Colors.WHITE, x, y, w, h,
            0, kopfueber ? textur.height : 0, textur.width, kopfueber ? 0 : textur.height, textur.width, textur.height);
    }

    /** Feste Bloecke zwischen Auge und Punkt? (Karte dann gedaempft, wie im Design.) */
    private static boolean verdeckt(double x, double y, double z)
    {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null)
        {
            return false;
        }

        Vec3 von = new Vec3(KAMERA.x, KAMERA.y, KAMERA.z);
        Vec3 nach = new Vec3(x, y, z);
        HitResult treffer = mc.level.clip(new ClipContext(von, nach, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));

        return treffer.getType() == HitResult.Type.BLOCK && treffer.getLocation().distanceToSqr(nach) > 0.25D;
    }

    /** Sekunden fuer die Animationen; nach einer Stunde springt die Phase einmal. */
    private static float zeit()
    {
        return (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000F;
    }
}
