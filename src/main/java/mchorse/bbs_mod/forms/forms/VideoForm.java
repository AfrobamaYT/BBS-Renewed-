package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * Ein Video als Billboard: Rechteck, Farbe, Billboard-Modus und Gizmo kommen von {@link BillboardForm}.
 * Der Texturlink wird vom Renderer auf das gerade dekodierte Bild gesetzt (video:&lt;pfad&gt;), nicht
 * vom Nutzer -- darum unsichtbar.
 */
public class VideoForm extends BillboardForm
{
    /** Dateipfad (absolut oder relativ zum BBS-Assets-Ordner). */
    public final ValueString video = new ValueString("video", "");
    /** Versatz in Ticks: Film-Tick 0 zeigt das Video ab diesem Tick. */
    public final ValueInt offset = new ValueInt("video_offset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    public final ValueFloat speed = new ValueFloat("video_speed", 1F, 0.01F, 8F);
    public final ValueBoolean loop = new ValueBoolean("video_loop", false);
    /** Film-Tick, ab dem das Video laeuft und zu sehen ist (wie ein Clip in der Zeitleiste). */
    public final ValueInt start = new ValueInt("video_start", 0, 0, Integer.MAX_VALUE);
    /** Sichtbare Laenge in Ticks; 0 = ohne Ende. Clips aus Resolve tragen hier ihre Laenge. */
    public final ValueInt length = new ValueInt("video_length", 0, 0, Integer.MAX_VALUE);
    /** Videospur in Resolve (0 = V1), nur fuer den Resolve-Link. */
    public final ValueInt track = new ValueInt("video_track", 0, 0, 99);

    public VideoForm()
    {
        super();

        this.texture.invisible();
        this.linear.set(true);

        this.add(this.video);
        this.add(this.offset);
        /* One-off switches, not tracks: a keyframe on the speed multiplies the carrier's whole age and
         * jumps the playhead instead of ramping; animate the offset for that (BBS FS 085cc3d87) */
        this.loop.invisible();
        this.speed.invisible();
        this.add(this.speed);
        this.add(this.loop);
        this.add(this.start);
        this.add(this.length);
        this.add(this.track);
    }

    /** Ob das Video zu diesem Film-Tick zu sehen ist. */
    public boolean shownAt(double filmTicks)
    {
        int length = this.length.get();

        return filmTicks >= this.start.get() && (length <= 0 || filmTicks < this.start.get() + length);
    }

    @Override
    public String getDefaultDisplayName()
    {
        String path = this.video.get();

        if (path == null || path.isEmpty())
        {
            return "video";
        }

        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
