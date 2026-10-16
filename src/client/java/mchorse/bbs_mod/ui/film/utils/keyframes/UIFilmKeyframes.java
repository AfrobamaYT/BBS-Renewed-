package mchorse.bbs_mod.ui.film.utils.keyframes;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

public class UIFilmKeyframes extends UIKeyframes implements com.mcstudio.nano.ZonenQuelle
{
    /** Klickflaechen fuer die Abnahme — der alte Batcher-Zweig meldet sonst keine.
     *
     * ⛔ NICHT im Feldinitialisierer anlegen. Ein per Hotswap NEU hinzugekommenes Feld ist
     * auf allen SCHON BESTEHENDEN Instanzen null — der Initialisierer laeuft dort nie mehr.
     * Genau daran ist die Testinstanz am 2026-08-23 gestorben: NullPointerException in
     * ZonenMelder.beginnen() mitten im Zeichnen, Absturzbericht „Rendering screen". */
    private com.mcstudio.nano.ZonenMelder zonen;

    private com.mcstudio.nano.ZonenMelder zonen()
    {
        if (this.zonen == null)
        {
            this.zonen = new com.mcstudio.nano.ZonenMelder();
        }

        return this.zonen;
    }

    @Override
    public java.util.List<com.mcstudio.nano.ZonenMelder.Zone> gemeldeteZonen()
    {
        return this.zonen().alle();
    }

    public IUIClipsDelegate editor;
    public boolean absolute;

    public UIFilmKeyframes(IUIClipsDelegate delegate, Consumer<Keyframe> callback)
    {
        super(callback);

        this.editor = delegate;
        this.keys().register(mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.TASTE, () -> mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.starten(this, false))
            .inside().category(mchorse.bbs_mod.ui.UIKeys.KEYFRAMES_KEYS_CATEGORY);
        this.keys().register(mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.STRECKEN, () -> mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.starten(this, true))
            .inside().category(mchorse.bbs_mod.ui.UIKeys.KEYFRAMES_KEYS_CATEGORY);
        this.keys().register(mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.DUPLIZIEREN, () -> mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.starten(this, false, true))
            .inside().category(mchorse.bbs_mod.ui.UIKeys.KEYFRAMES_KEYS_CATEGORY);
        this.keys().register(mchorse.bbs_mod.ui.film.replays.KeyframeMischen.TASTE, () -> mchorse.bbs_mod.ui.film.replays.KeyframeMischen.starten(this))
            .inside().category(mchorse.bbs_mod.ui.UIKeys.KEYFRAMES_KEYS_CATEGORY);
    }

    /** Edited position or rotation keys move the actor now, not at the next 20 Hz tick (BBS FS cea01fe06). */
    @Override
    public void triggerChange()
    {
        super.triggerChange();

        mchorse.bbs_mod.ui.film.UIFilmPanel panel = this.getParent(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
        if (panel == null || panel.getController().isControlling()) return;
        mchorse.bbs_mod.film.replays.Replay replay = panel.replayEditor.getReplay();
        mchorse.bbs_mod.forms.entities.IEntity entity = panel.getController().getCurrentEntity();
        if (replay == null || entity == null) return;

        var channels = replay.keyframes;
        boolean position = false;
        boolean rotation = false;
        for (var sheet : this.getGraph().getSheets())
        {
            if (!sheet.selection.hasAny()) continue;
            position |= sheet.channel == channels.x || sheet.channel == channels.y || sheet.channel == channels.z;
            rotation |= sheet.channel == channels.yaw || sheet.channel == channels.pitch
                || sheet.channel == channels.headYaw || sheet.channel == channels.bodyYaw;
        }
        if (!position && !rotation) return;

        float cursor = this.getTick();
        float tick = replay.getTick((int) cursor) + (cursor - (int) cursor);
        if (position)
        {
            double x = channels.x.interpolate(tick), y = channels.y.interpolate(tick), z = channels.z.interpolate(tick);
            entity.setPosition(x, y, z);
            entity.setPrevX(x);
            entity.setPrevY(y);
            entity.setPrevZ(z);
        }
        if (rotation)
        {
            float yaw = channels.yaw.interpolate(tick).floatValue(), pitch = channels.pitch.interpolate(tick).floatValue();
            float headYaw = channels.headYaw.interpolate(tick).floatValue(), bodyYaw = channels.bodyYaw.interpolate(tick).floatValue();
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.setHeadYaw(headYaw);
            entity.setBodyYaw(bodyYaw);
            entity.setPrevYaw(yaw);
            entity.setPrevPitch(pitch);
            entity.setPrevHeadYaw(headYaw);
            entity.setPrevBodyYaw(bodyYaw);
        }
    }

    public UIFilmKeyframes absolute()
    {
        this.absolute = true;

        return this;
    }

    public long getClipOffset()
    {
        if (this.absolute)
        {
            return 0;
        }

        if (this.editor == null || this.editor.getClip() == null)
        {
            return 0;
        }

        return this.editor.getClip().tick.get();
    }

    public int getOffset()
    {
        if (this.editor == null)
        {
            return 0;
        }

        return (int) (this.editor.getCursor() - this.getClipOffset());
    }

    @Override
    public float getTick()
    {
        return this.getOffset();
    }

    @Override
    protected void selectNextKeyframe(int direction)
    {
        super.selectNextKeyframe(direction);

        Keyframe keyframe = this.getGraph().getSelected();

        if (keyframe != null)
        {
            this.editor.setCursor((int) keyframe.getTick());
        }
    }

    @Override
    protected void moveNoKeyframes(UIContext context)
    {
        if (this.editor != null)
        {
            long offset = this.getClipOffset();
            float tick = (float) this.fromGraphX(context.mouseX);

            /* Playhead-Magnet (Resolve): der Scrub rastet an Markern, Clip-Kanten und
             * Keys — der Cursor selbst ist ausgenommen (er ist das Gezogene). Gemessen
             * Akte 38: der Nutzer-Zug lief IMMER hier entlang, nie im Key-Drag. */
            if (!mchorse.bbs_mod.graphics.window.Window.isShiftPressed())
            {
                tick = this.getDopeSheet().magnetTick(tick, context, true);
            }

            this.editor.setCursor(Math.max(0F, mchorse.bbs_mod.ui.film.ICursor.einrasten(tick) + offset));
        }
    }

    @Override
    protected void renderOverlay(UIContext context)
    {
        /* Film-Merkstellen NACH dem Lineal (Overlay), nicht im rulerDecorator: der laeuft
         * VOR renderTicks und die Skala uebermalte die Koepfe (Nutzerbefund Akte 38).
         * Nur im absolute-Modus — die Clip-Unteransichten rechnen mit Clip-Versatz. */
        if (this.absolute && this.editor != null && this.editor.getFilm() != null)
        {
            context.batcher.clip(this.graphArea, context);
            mchorse.bbs_mod.ui.film.markers.UIMarkerLane.zeichneImLineal(context,
                this.graphArea, this.editor.getFilm().markers, this::toGraphX);
            context.batcher.unclip(context);
        }

        if (this.editor != null)
        {
            float cursor = this.editor.getCursor(0F) - this.getClipOffset();
            int cx = this.toGraphX(cursor);
            /* Nur die Zahl — die Gesamtdauer steht im Kopf der Zeitleiste, wie in UIClips. */
            String label = TimeUtils.formatCursorTime(cursor);

            context.batcher.clip(this.graphArea, context);
            UIClips.renderCursor(context, label, this.graphArea, cx - 1);
            context.batcher.unclip(context);
        }

        super.renderOverlay(context);

        /* Am ENDE des Zeichenlaufs: erst jetzt steht das Layout, das gemeldet wird. */
        /* ⛔ „Animated only" gilt AUCH hier. Der Modus lag nur in der Zeitleiste und wurde
         * ausschliesslich an ZeitleisteSpuren gereicht; der Animier-Bereich baut seine
         * Zeilen ueber updateChannelsList und bekam ihn nie zu sehen (Nutzerbefund
         * 2026-08-24). Der Setter ist idempotent, das kostet je Bild nichts. */
        mchorse.bbs_mod.ui.film.UIFilmPanel filmPanel =
            mchorse.bbs_mod.BBSModClient.getDashboard() == null ? null
                : mchorse.bbs_mod.BBSModClient.getDashboard().getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class);

        if (filmPanel != null && filmPanel.zeitleisteNano instanceof mchorse.bbs_mod.ui.film.UIZeitleisteNano zl
            && this.getDopeSheet() != null)
        {
            this.getDopeSheet().setNurBewegte(zl.modus == 1);
            /* Idempotent (Guard im Zweig) — der Editor-Neubau verliert das Predicate sonst. */
            mchorse.bbs_mod.ui.film.ZeitleisteZweig.boneFilterAnwenden(filmPanel);
        }

        com.mcstudio.nano.ZonenMelder melder = this.zonen();

        melder.beginnen();
        KeyframeZonen.melden(melder, this);
        melder.uebernehmen();
    }
}
