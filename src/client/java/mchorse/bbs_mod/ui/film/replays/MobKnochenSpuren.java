package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.List;
import java.util.Map;

/**
 * A mob form's bones as tracks of their own under its pose track, as a model form's are: one colour
 * for a mob's parts, depth 0, since vanilla's parts are a flat list to the timeline. Driven at run
 * time by {@code FormProperties}, which now poses any {@link IPosedForm}. From BBS FS e341fa733
 * (Wemppy4/bbs-fs, MIT).
 */
public final class MobKnochenSpuren
{
    private MobKnochenSpuren()
    {}

    public static void hinzufuegen(Form form, FormProperties properties, List<UIKeyframeSheet> out, Map<String, Integer> tiefe)
    {
        if (!(form instanceof IPosedForm posed) || !posed.getBoneTracks().get())
        {
            return;
        }

        String path = FormUtils.getPath(form);
        int farbe = Colors.HSVtoRGB(0.08F, 0.7F, 0.7F).getRGBColor();

        for (Object knochen : FormUtilsClient.getRenderer(form).getBones())
        {
            String bone = String.valueOf(knochen), key = PerLimbService.toPoseBoneKey(path, bone);
            KeyframeChannel channel = properties.registerChannel(key, KeyframeFactories.POSE_TRANSFORM);

            out.add(new UIKeyframeSheet(key, IKey.constant(path.isEmpty() ? bone : path + "/" + bone), farbe, false, channel, new ValueTransform(key, new PoseTransform()), true).form(form));

            if (tiefe != null) tiefe.put(key, 0);
        }
    }
}
