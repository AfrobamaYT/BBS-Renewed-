package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * G6: the key preview zooms onto what the key CHANGES, not the whole model. A small change (an
 * eyebrow, a finger) was a few pixels in the 80 px full-body tile -- the user's report. The
 * changed bones are this key against the one before it on the same track (the rest pose for
 * the first key); the tile frames their pivots, and a line names the change ("head R X +15°").
 * Blender's "frame selected" for a hover tile; neither Blender nor Maya preview keys at all.
 *
 * <p>The focus hangs off the preview's own form copy (weak map): the tile renders later, from
 * the thumbnail queue, through ModelFormRenderer.renderInUI, which asks {@link #matrix}.</p>
 */
public final class KeyVorschauFokus
{
    private static final float EPS = 1e-3F;
    /** Smallest framed size in blocks: one bone's pivot is a point, a head is about this tall */
    private static final float MIN_GROESSE = 0.6F;

    private record Fokus(Vector3f mitte, float groesse)
    {}

    private static final Map<Form, Fokus> FOKUS = new WeakHashMap<>();

    private KeyVorschauFokus()
    {}

    /** Bone -> what changed, for a pose value against the one before (null: rest pose) */
    public static Map<String, String> geaendert(Pose jetzt, Pose davor)
    {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> knochen = new ArrayList<>(jetzt.transforms.keySet());

        if (davor != null) davor.transforms.keySet().forEach(k -> { if (!knochen.contains(k)) knochen.add(k); });

        for (String k : knochen)
        {
            String text = unterschied(jetzt.transforms.get(k), davor == null ? null : davor.transforms.get(k));

            if (text != null) out.put(k, text);
        }

        return out;
    }

    /** "R X +15°, T Y +0.25" or null when equal (a missing transform counts as rest) */
    public static String unterschied(Transform a, Transform b)
    {
        Transform rest = new Transform();
        Transform x = a == null ? rest : a;
        Transform y = b == null ? rest : b;
        StringBuilder sb = new StringBuilder();

        achsen(sb, "R", MathUtils.toDeg(x.rotate.x - y.rotate.x), MathUtils.toDeg(x.rotate.y - y.rotate.y), MathUtils.toDeg(x.rotate.z - y.rotate.z), "°", 0.05F);
        achsen(sb, "T", x.translate.x - y.translate.x, x.translate.y - y.translate.y, x.translate.z - y.translate.z, "", EPS);
        achsen(sb, "S", x.scale.x - y.scale.x, x.scale.y - y.scale.y, x.scale.z - y.scale.z, "", EPS);

        return sb.isEmpty() ? null : sb.toString();
    }

    /** "head R X +15°" for up to two bones, then "+N" */
    public static String zeile(Map<String, String> geaendert)
    {
        StringBuilder sb = new StringBuilder();
        int i = 0;

        for (Map.Entry<String, String> e : geaendert.entrySet())
        {
            if (i++ == 2)
            {
                return sb.append("  +").append(geaendert.size() - 2).toString();
            }

            sb.append(sb.isEmpty() ? "" : "  ").append(e.getKey()).append(' ').append(e.getValue());
        }

        return sb.toString();
    }

    /** Frame these bones of the preview form; nothing (or no bone found) keeps the full view */
    public static void setzen(Form form, Iterable<String> knochen)
    {
        FOKUS.remove(form);

        FormRenderer renderer = form instanceof ModelForm ? FormUtilsClient.getRenderer(form) : null;
        ModelInstance model = form instanceof ModelForm m ? ModelFormRenderer.getModel(m) : null;

        if (renderer == null || model == null || Minecraft.getInstance().level == null)
        {
            return;
        }

        StubEntity entity = new StubEntity(Minecraft.getInstance().level);

        /* A fresh preview copy has a fresh renderer without an animator until its first draw,
         * and collectMatrices only collects bones with one: the first hover framed nothing */
        ((ModelFormRenderer) renderer).ensureAnimator(0F);
        entity.setForm(form);

        MatrixCache cache = renderer.collectMatrices(entity, 0F);
        Matrix4f wurzel = cache.get("").matrix();
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
        int punkte = 0;

        for (String k : knochen)
        {
            MatrixCacheEntry e = cache.get(k);

            if (e.matrix() == null) continue;

            /* The pivot, and a point up the bone: a pivot alone is the neck of a head */
            Vector3f p = e.matrix().getTranslation(new Vector3f());
            Vector3f oben = e.matrix().transformPosition(new Vector3f(0, 0.5F, 0));

            min.min(p).min(oben);
            max.max(p).max(oben);
            punkte++;
        }

        if (punkte == 0 || wurzel == null)
        {
            return;
        }

        /* collectMatrices: root (form transforms) then a half turn about Y; the UI tile applies the
         * form transforms and the UI scale without that turn (ModelFormRenderer.renderInUI) */
        float uiScale = ((ModelForm) form).uiScale.get() * model.getUiScale();
        Vector3f mitte = new Vector3f(min).add(max).mul(0.5F);
        Vector3f modell = new Matrix4f(wurzel).invert().transformPosition(mitte, new Vector3f()).rotateY(-MathUtils.PI);
        Vector3f imKachelraum = new Matrix4f(wurzel).transformPosition(modell.mul(uiScale), new Vector3f());
        float groesse = Math.max(MIN_GROESSE, Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z))) * uiScale;

        FOKUS.put(form, new Fokus(imKachelraum, groesse));
    }

    /** For ModelFormRenderer.renderInUI: the usual full-body matrix, or one framed on the focus */
    public static Matrix4f matrix(Form form, Matrix4f basis, UIContext context, int x1, int y1, int x2, int y2)
    {
        Fokus f = FOKUS.get(form);

        if (f == null)
        {
            return basis;
        }

        float w = x2 - x1;
        float h = y2 - y1;
        float s = Math.min(w, h) / (f.groesse() * 1.7F);
        /* Same turn as getUIMatrix, so the zoomed tile still follows the mouse (or stays frozen) */
        float winkel = BBSSettings.freezeModels.get() ? -MathUtils.PI + MathUtils.PI / 8 : MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

        return new Matrix4f().translate(x1 + w / 2, y1 + h / 2, 40).scale(s, -s, s).rotateX(MathUtils.PI / 8).rotateY(winkel)
            .translate(-f.mitte().x, -f.mitte().y, -f.mitte().z);
    }

    private static void achsen(StringBuilder sb, String art, float x, float y, float z, String einheit, float schwelle)
    {
        float[] w = {x, y, z};

        for (int i = 0; i < 3; i++)
        {
            if (Math.abs(w[i]) < schwelle) continue;

            sb.append(sb.isEmpty() ? "" : ", ").append(art).append(' ').append("XYZ".charAt(i)).append(' ')
                .append(String.format(java.util.Locale.ROOT, einheit.isEmpty() ? "%+.2f" : "%+.0f", w[i])).append(einheit);
        }
    }
}
