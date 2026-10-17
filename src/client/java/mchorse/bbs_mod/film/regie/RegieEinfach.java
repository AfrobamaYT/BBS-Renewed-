package mchorse.bbs_mod.film.regie;

import com.mcstudio.nano.NanoDesign;
import static org.lwjgl.nanovg.NanoVG.*;

/** One quiet target indicator. Reuses the existing frame camera and behind-camera projection. */
public final class RegieEinfach
{
    private RegieEinfach()
    {}

    public record Bild(RegieMarker marker, boolean rand, float x, float y, float winkel,
        float rest, float fortschritt, boolean bevor)
    {}

    /** Most recently started active window; otherwise the earliest upcoming window. Ties stay stable. */
    public static RegieMarker ziel(Regie regie, float tick)
    {
        RegieMarker ziel = null;

        for (RegieMarker marker : regie.marker.getList())
        {
            if (marker.ende.get() <= tick) continue;
            boolean aktiv = marker.start.get() <= tick;
            boolean vorherAktiv = ziel != null && ziel.start.get() <= tick;

            if (ziel == null || aktiv && !vorherAktiv
                || aktiv && vorherAktiv && marker.start.get() > ziel.start.get()
                || !aktiv && !vorherAktiv && marker.start.get() < ziel.start.get())
            {
                ziel = marker;
            }
        }

        return ziel;
    }

    public static Bild bild(Regie regie, float tick, RegieKamera kamera, float breite, float hoehe, float k)
    {
        RegieMarker marker = ziel(regie, tick);
        if (marker == null) return null;
        float[] punkt = new float[3];
        double y = marker.y.get() + (marker.istOrt() ? 1D : 0D);
        float randSkala = Math.min(k, Math.min(breite, hoehe) / 160F);
        RegiePfeil rand = RegiePfeil.fuer(marker.farbe.get(), "", marker.start.get() <= tick,
            marker.x.get(), y, marker.z.get(), kamera, breite, hoehe, randSkala, punkt);
        boolean bevor = tick < marker.start.get();
        float rest = (bevor ? marker.start.get() - tick : marker.ende.get() - tick) / 20F;
        float fortschritt = bevor ? 1F - RegieAnsicht.klemmen(rest / 6F, 0F, 1F)
            : RegieAnsicht.klemmen((marker.ende.get() - tick) / Math.max(1F, marker.ende.get() - marker.start.get()), 0F, 1F);

        return new Bild(marker, rand != null, rand == null ? punkt[0] : rand.x,
            rand == null ? punkt[1] - 18F * k : rand.y, rand == null ? (float) Math.PI : rand.winkel,
            rest, fortschritt, bevor);
    }

    /** Only the visible world arrow is draggable; hidden markers and edge hints never steal clicks. */
    public static String fassen(Bild bild, float x, float y, float k)
    {
        return bild != null && !bild.rand && Math.abs(x - bild.x) <= 14F * k
            && Math.abs(y - bild.y) <= 16F * k ? bild.marker.uuid.get() : null;
    }

    public static String fassen(mchorse.bbs_mod.ui.film.UIFilmPanel panel, mchorse.bbs_mod.ui.framework.UIContext context)
    {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.renderer.state.level.CameraRenderState zustand = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (zustand == null || !zustand.initialized) return null;
        RegieKamera kamera = new RegieKamera();
        kamera.setzen(zustand.projectionMatrix, zustand.viewRotationMatrix, zustand.pos.x, zustand.pos.y, zustand.pos.z);
        mchorse.bbs_mod.ui.utils.Area area = panel.preview.getViewport();
        float skala = mchorse.bbs_mod.BBSModClient.getGUIScale();
        float k = mc.getWindow().getHeight() / 1080F;
        float tick = panel.getCursor() + (panel.isRunning() ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0F);
        Bild bild = bild(panel.getData().regie, tick, kamera, Math.max(1, Math.round(area.w * skala)),
            Math.max(1, Math.round(area.h * skala)), k);
        return fassen(bild, (context.mouseX - area.x) * skala, (context.mouseY - area.y) * skala, k);
    }

    public static void zeichnen(long vg, Regie regie, float tick, RegieKamera kamera, float breite, float hoehe, float k)
    {
        Bild bild = bild(regie, tick, kamera, breite, hoehe, k);
        if (bild == null) return;
        RegieStift st = Stifte.STIFT;

        nvgSave(vg);
        nvgTranslate(vg, bild.x, bild.y);
        nvgRotate(vg, bild.winkel);
        nvgBeginPath(vg);
        nvgMoveTo(vg, 0F, -12F * k);
        nvgLineTo(vg, 9F * k, 7F * k);
        nvgLineTo(vg, 0F, 3F * k);
        nvgLineTo(vg, -9F * k, 7F * k);
        nvgClosePath(vg);
        nvgStrokeColor(vg, st.rgba(0x101114, 0.9F, st.a));
        nvgStrokeWidth(vg, 3F * k);
        nvgStroke(vg);
        nvgFillColor(vg, st.rgba(0xE5E7EB, 1F, st.a));
        nvgFill(vg);
        nvgRestore(vg);

        nvgSave(vg);
        float ty = bild.y + 23F * k;
        float rand = Math.min(100F * k, breite / 2F);
        float tx = RegieAnsicht.klemmen(bild.x, rand, breite - rand);
        st.schrift(vg, NanoDesign.SANS, 12F * k, 0F);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        if (RegieDarstellung.text(regie) && !bild.marker.name.get().isBlank())
        {
            String text = st.kuerzen(vg, bild.marker.name.get(), 180F * k);
            text(vg, st, text, tx, ty, k);
            ty += 17F * k;
        }

        if (RegieDarstellung.zeit(regie))
        {
            st.rundeck(vg, tx - 30F * k, ty, 60F * k, 4F * k, 2F * k, 0x15171B, 0.9F);
            if (bild.fortschritt > 0F)
                st.rundeck(vg, tx - 30F * k, ty, 60F * k * bild.fortschritt, 4F * k, 2F * k,
                    bild.marker.farbe.get(), 0.9F);
            text(vg, st, (bild.bevor ? "in " : "") + RegieAnsicht.zahl(bild.rest) + " s", tx, ty + 15F * k, k);
        }

        nvgRestore(vg);
    }

    private static void text(long vg, RegieStift st, String text, float x, float y, float k)
    {
        nvgFillColor(vg, st.rgba(0x101114, 1F, st.a));
        nvgText(vg, x + k, y + k, text);
        nvgFillColor(vg, st.rgba(0xE5E7EB, 1F, st.a));
        nvgText(vg, x, y, text);
    }

    private static final class Stifte
    {
        private static final RegieStift STIFT = new RegieStift();
    }
}
