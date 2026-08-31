package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;

public class Replay extends ValueGroup
{
    public final ValueForm form = new ValueForm("form");
    public final ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public final FormProperties properties = new FormProperties("properties");
    public final Clips actions = new Clips("actions", BBSMod.getFactoryActionClips());

    public final ValueBoolean enabled = new ValueBoolean("enabled", true);

    /**
     * Nur im Viewport zeigen, im fertigen Video NICHT.
     *
     * <p>Fuer tutorial- und making-of-aufnahmen: hilfs-akteure, platzhalter oder
     * referenz-posen bleiben beim arbeiten sichtbar und verschwinden im export,
     * ohne dass man sie vorher ausschalten und hinterher wieder einschalten muss.
     *
     * <p>Unterschied zu {@link #enabled}: das schaltet den akteur UEBERALL ab.
     */
    public final ValueBoolean nurVorschau = new ValueBoolean("nur_vorschau", false);
    /**
     * Single-level folder name for the replay list (empty = root). No {@code /} — see {@link #normalizeCategory(String)}.
     */
    public final ValueString category = new ValueString("category", "");
    public final ValueString label = new ValueString("label", "");
    /** Farblabel (After-Effects-Muster): 0 = keins, sonst RGB. Toent Outliner-Zeile und Dope-Kopf. */
    public final ValueInt labelColor = new ValueInt("label_color", 0);
    public final ValueString nameTag = new ValueString("name_tag", "");
    /* MERGE 2026-07-27: mob-capture aus dem CML-upstream (RecorderMobCapture). */
    public final ValueBoolean vanillaMobPlayback = new ValueBoolean("vanilla_mob_playback", false);
    public transient boolean vanillaMobPlaybackSerialized = false;

    public final ValueString group = new ValueString("group", "");
    public final ValueBoolean isGroup = new ValueBoolean("is_group", false);
    public final ValueString uuid = new ValueString("uuid", "");

    public final ValueBoolean shadow = new ValueBoolean("shadow", true);
    public final ValueFloat shadowSize = new ValueFloat("shadow_size", 0.5F);
    /** When enabled, the shadow follows the form's perceived position (anchor/transform motion). */
    public final ValueBoolean shadowFollow = new ValueBoolean("shadow_follow", false);
    /** Extra world-space offset added to the followed shadow position (corrects a model's shifted floor level). */
    public final ValuePoint shadowOffset = new ValuePoint("shadow_offset", new Point(0, 0, 0));
    public final ValueInt looping = new ValueInt("looping", 0);

    public final ValueBoolean actor = new ValueBoolean("actor", false);
    public final ValueBoolean fp = new ValueBoolean("fp", false);
    public final ValueBoolean relative = new ValueBoolean("relative", false);
    public final ValuePoint relativeOffset = new ValuePoint("relativeOffset", new Point(0, 0, 0));

    public final ValueBoolean axesPreview = new ValueBoolean("axes_preview", false);
    public final ValueString axesPreviewBone = new ValueString("axes_preview_bone", "");

    public Replay(String id)
    {
        super(id);

        this.add(this.form);
        this.add(this.keyframes);
        this.add(this.properties);
        this.add(this.actions);

        this.add(this.enabled);

        this.add(this.nurVorschau);
        this.add(this.category);
        this.add(this.label);
        this.add(this.labelColor);
        this.add(this.nameTag);
        this.add(this.vanillaMobPlayback);
        this.add(this.group);
        this.add(this.isGroup);
        this.add(this.uuid);
        this.uuid.set(java.util.UUID.randomUUID().toString());
        this.add(this.shadow);
        this.add(this.shadowSize);
        this.add(this.shadowFollow);
        this.add(this.shadowOffset);
        this.add(this.looping);

        this.add(this.actor);
        this.add(this.fp);
        this.add(this.relative);
        this.add(this.relativeOffset);

        this.add(this.axesPreview);
        this.add(this.axesPreviewBone);
    }

    /**
     * Normalizes a user-supplied category: trim, single segment (no {@code /}), empty = root.
     */
    public static String normalizeCategory(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        String s = raw.trim();

        if (s.isEmpty())
        {
            return "";
        }

        int slash = s.indexOf('/');

        if (slash >= 0)
        {
            s = s.substring(0, slash).trim();
        }

        return s.isEmpty() ? "" : s;
    }

    public String getName()
    {
        String label = this.label.get();

        if (!label.isEmpty())
        {
            return label;
        }

        Form form = this.form.get();

        if (form == null)
        {
            return "-";
        }

        return form.getDisplayName();
    }

    public void shift(float tick)
    {
        this.keyframes.shift(tick);
        this.properties.shift(tick);
        this.actions.shift(tick);
    }

    public void applyActions(LivingEntity actor, SuperFakePlayer fakePlayer, Film film, int tick)
    {
        List<Clip> clips = this.actions.getClips(tick);

        for (Clip clip : clips)
        {
            ((ActionClip) clip).apply(actor, fakePlayer, film, this, tick);
        }
    }

    public void applyClientActions(int tick, IEntity entity, Film film)
    {
        tick = this.getTick(tick);

        List<Clip> clips = this.actions.getClips(tick);

        for (Clip clip : clips)
        {
            if (clip instanceof ActionClip actionClip && actionClip.isClient())
            {
                actionClip.applyClient(entity, film, this, tick);
            }
        }
    }

    public int getTick(int tick)
    {
        return this.looping.get() > 0 ? tick % this.looping.get() : tick;
    }
}