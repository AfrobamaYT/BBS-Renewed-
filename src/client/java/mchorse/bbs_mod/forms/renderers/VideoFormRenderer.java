package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.video.VideoClock;
import mchorse.bbs_mod.video.VideoTextures;

import java.util.Objects;

/**
 * Zeichnet ein Video ueber den Billboard-Weg: vor jedem Zug wird der Texturlink der Form auf das
 * Bild zur aktuellen Film-Zeit gesetzt. Alles Weitere (Rechteck, Vitrail, Picking) bleibt Billboard.
 */
public class VideoFormRenderer extends BillboardFormRenderer
{
    private static final int PREPARE_TICKS = 40;

    public VideoFormRenderer(VideoForm form)
    {
        super(form);
    }

    /** Ohne Video nur das Symbol, und das flach: eingereiht landet ein Batcher-Zug nicht in der Kachel. */
    @Override
    protected boolean flacheVorschau()
    {
        String path = ((VideoForm) this.form).video.get();

        return path == null || path.isEmpty();
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* Ohne Video gibt es kein Bild: das Symbol, damit die Form in der Liste nicht unsichtbar ist. */
        if (this.update(null, 0F) == null)
        {
            context.batcher.icon(Icons.VIDEO_CAMERA, (x1 + x2) / 2, (y1 + y2) / 2, 0.5F, 0.5F);

            return;
        }

        super.renderInUI(context, x1, y1, x2, y2);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        VideoForm form = (VideoForm) this.form;
        double ticks = context.entity == null ? 0D : VideoClock.ticks(context.entity, context.getTransition());

        /* Ausserhalb seines Zeitfensters (Start/Laenge, z. B. ein Clip aus Resolve) ist das Video
         * nicht da -- auch nicht als Standbild. Zwei Sekunden vorher wird der Decoder schon
         * geoeffnet, damit das erste sichtbare Bild (auch im Export) schon da ist. */
        if (context.entity != null && !form.shownAt(ticks) && !form.shownAt(ticks + PREPARE_TICKS))
        {
            return;
        }

        Link link = this.update(context.entity, context.getTransition());

        /* Ohne dekodiertes Bild kein leeres Rechteck. */
        if (link == null || (context.entity != null && !form.shownAt(ticks)))
        {
            return;
        }

        super.render3D(context);
    }

    private Link update(IEntity entity, float transition)
    {
        VideoForm form = (VideoForm) this.form;
        Link link = VideoTextures.frame(form, entity, entity == null ? this : entity);

        if (!Objects.equals(form.texture.get(), link))
        {
            form.texture.set(link);
        }

        return link;
    }
}
