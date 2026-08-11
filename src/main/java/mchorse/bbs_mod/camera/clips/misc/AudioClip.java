package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.function.Predicate;

public class AudioClip extends CameraClip
{
    public static final Predicate<Clip> NO_AUDIO = (clip) -> !(clip instanceof AudioClip);

    /* Modi fuer {@link #spatial}. Die ZAHLEN landen im film-json — reihenfolge nie
     * umstellen, neue modi nur hinten anhaengen, sonst zeigen alte filme auf den
     * falschen modus. 0 bleibt der alt-zustand (nicht raeumlich), damit filme von
     * vor diesem feature unveraendert klingen. */
    public static final int SPATIAL_OFF = 0;
    public static final int SPATIAL_WORLD = 1;
    public static final int SPATIAL_ACTOR = 2;

    public ValueLink audio = new ValueLink("audio", null);
    public ValueInt offset = new ValueInt("offset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    public ValueFloat volume = new ValueFloat("volume", 1F, 0F,1F);

    /** Aus / feste weltkoordinate / an einen actor gebunden. */
    public ValueInt spatial = new ValueInt("spatial", SPATIAL_OFF, SPATIAL_OFF, SPATIAL_ACTOR);

    /** Nur im modus {@link #SPATIAL_WORLD} gelesen — im actor-modus liefert der actor die position. */
    public ValuePoint spatialPoint = new ValuePoint("spatial_point", new Point(0, 0, 0));

    /** Index in die actor-liste des films (CameraClipContext.entities); -1 = keiner gewaehlt. */
    public ValueInt spatialSelector = new ValueInt("spatial_selector", -1);

    /**
     * Herkunft, wenn dieser clip von Auto Audio angelegt wurde: {@code replay-uuid|form-pfad}.
     * Leer = von hand gesetzt.
     *
     * <p>⛔ Das ist die BESITZ-marke, nicht nur eine notiz: ein erneuter Auto-Audio-lauf
     * loescht genau die clips mit SEINER markierung und legt sie neu an. Ein clip ohne
     * markierung wird nie angefasst. Wer das feld von hand leert, macht den clip damit
     * dauerhaft — der naechste lauf laesst ihn stehen (und legt daneben einen neuen an).</p>
     */
    public ValueString autoAudio = new ValueString("auto_audio", "");

    public AudioClip()
    {
        super();

        this.add(this.audio);
        this.add(this.offset);
        this.add(this.volume);
        this.add(this.spatial);
        this.add(this.spatialPoint);
        this.add(this.spatialSelector);
        this.add(this.autoAudio);
    }

    @Override
    public void shiftLeft(int tick)
    {
        super.shiftLeft(tick);

        this.offset.set(this.offset.get() - (this.tick.get() - tick));
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new AudioClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        this.offset.set(this.offset.get() + offset);
    }
}