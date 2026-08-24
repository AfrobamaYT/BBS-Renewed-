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

            this.editor.setCursor(Math.max(0, (int) (Math.round(this.fromGraphX(context.mouseX)) + offset)));
        }
    }

    @Override
    protected void renderOverlay(UIContext context)
    {
        if (this.editor != null)
        {
            int cx = this.toGraphX(this.getOffset());
            /* Nur die Zahl — die Gesamtdauer steht im Kopf der Zeitleiste, wie in UIClips. */
            String label = TimeUtils.formatTime(this.getOffset());

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
        }

        com.mcstudio.nano.ZonenMelder melder = this.zonen();

        melder.beginnen();
        KeyframeZonen.melden(melder, this);
        melder.uebernehmen();
    }
}
