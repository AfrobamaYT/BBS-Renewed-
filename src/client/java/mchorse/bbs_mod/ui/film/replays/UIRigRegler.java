package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.cubic.ik.RigMechanik;
import mchorse.bbs_mod.cubic.ik.RigSnap;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Map;

/**
 * Das Bedienfeld eines Control-Rigs — Rigifys N-Panel.
 *
 * <p>Zeigt die Regler des ausgewaehlten Rigs: IK_FK, IK_Stretch, IK_parent,
 * pole_vector, pole_parent, FK_limb_follow. Jeder Regler steht an einem
 * Eigenschaften-Knochen je Glied ({@code upper_arm_parent.L}); in Blender sitzt
 * dort das Zahnrad-Widget, das der Animator anklickt.
 *
 * <p>⛔ Es erscheinen NUR Regler, an denen wirklich ein Treiber haengt — die
 * Vorauswahl trifft schon {@code rig-extrakt.py}. Ein Schieber, der nichts bewegt,
 * ist eine Luege im Bedienfeld, und die faellt erst auf, wenn jemand eine Stunde
 * lang sucht, warum sein Arm nicht umschaltet.
 */
public class UIRigRegler extends UIElement
{
    private String gebautFuer = "";
    private IEntity entity;
    /** ⛔ Die Pose der REPLAY, nicht die der Entity. `createEntities` legt je Bild eine
     * Form-KOPIE an; ein Snap in deren Pose ist im naechsten Bild weg (gemessen: Sprung
     * blieb bei 0.56, waehrend derselbe Snap in die Replay-Pose 1e-6 lieferte). */
    private mchorse.bbs_mod.utils.pose.Pose pose;

    public UIRigRegler()
    {
        this.column().vertical().stretch();
    }

    /** Die Mechanik des Modells, an dem diese Entity haengt — oder null. */
    public static RigMechanik mechanikVon(IEntity entity)
    {
        if (entity == null
            || !(entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            || !(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form)
                instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer))
        {
            return null;
        }

        mchorse.bbs_mod.cubic.ModelInstance instance = renderer.getModel();

        if (instance == null
            || !(instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj))
        {
            return null;
        }

        RigMechanik.installieren(bobj, form.model.get());

        return bobj.getArmature().zwang instanceof RigMechanik m ? m : null;
    }

    /**
     * Baut das Feld neu, wenn ein anderes Rig ausgewaehlt ist. Je Frame billig:
     * bei gleicher Kennung passiert nichts.
     */
    public void aktualisiere(IEntity entity, mchorse.bbs_mod.utils.pose.Pose pose)
    {
        RigMechanik mechanik = mechanikVon(entity);
        String kennung = mechanik == null ? "" : String.valueOf(mechanik.hashCode());

        if (kennung.equals(this.gebautFuer))
        {
            return;
        }

        this.gebautFuer = kennung;
        this.entity = entity;
        this.pose = pose;
        this.removeAll();

        if (mechanik == null || mechanik.treiber.regler.leer())
        {
            this.resize();

            return;
        }

        for (String bone : mechanik.treiber.regler.knochen())
        {
            this.add(UI.label(IKey.raw(bone))
                .background(Colors.A50 | Colors.GRAY));

            for (Map.Entry<String, MapType> e : mechanik.treiber.regler.reglerVon(bone).entrySet())
            {
                this.add(this.schieber(mechanik, bone, e.getKey(), e.getValue()));

                float fk = RigSnap.fkWert(mechanik, bone, e.getKey(),
                    e.getValue().getFloat("min", 0F), e.getValue().getFloat("max", 1F));

                /* ⛔ Tor ist die PAAR-Zahl, nicht fkWert. Gemessen: `eyes.eyes_follow` treibt
                 * zwar ein COPY_TRANSFORMS, hat aber 0 Paare (der getriebene Constraint steht
                 * auf nr0, es gibt keine zweite Quelle) — ein Knopf dort haette nichts
                 * geschnappt. Ein Knopf, der nichts tut, ist derselbe Betrug wie ein
                 * Schieber, der nichts bewegt. */
                if (!Float.isNaN(fk) && !RigSnap.paare(mechanik, bone, e.getKey()).isEmpty())
                {
                    this.add(this.snapZeile(mechanik, bone, e.getKey(), fk));
                }
            }
        }

        this.resize();
    }

    /**
     * Die zwei Snap-Knoepfe eines Glieds. Blenders Reihenfolge: <b>erst schnappen, dann
     * schalten</b> — beides in einem Klick, weil nur Schalten den Ein-Bild-Sprung erzeugt,
     * vor dem jede Rigify-Anleitung warnt (gemessen 0.564 gegen 1e-6 mit Snap).
     */
    private UIElement snapZeile(RigMechanik mechanik, String bone, String prop, float fkWert)
    {
        float ikWert = fkWert == 0F ? 1F : 0F;

        /* Blender baut den Kiefer-Lock (`mouth_lock`) aus GENAU derselben Konstruktion wie
         * FK/IK: zwei COPY_TRANSFORMS, der zweite getrieben. Strukturell sind sie nicht zu
         * unterscheiden — also wird nur dort "FK/IK" draufgeschrieben, wo der Regler auch so
         * heisst (Rigifys Konvention, rein fuer die BESCHRIFTUNG; die Mechanik bleibt aus
         * dem Graphen abgeleitet). Sonst steht dran, was wirklich passiert. */
        boolean ikfk = prop.toUpperCase().contains("IK");
        String linksText = ikfk ? "-> FK" : prop + " = " + trim(fkWert);
        String rechtsText = ikfk ? "-> IK" : prop + " = " + trim(ikWert);

        UIButton nachFk = new UIButton(IKey.raw(linksText), (b) -> this.snap(mechanik, bone, prop, true, fkWert));
        UIButton nachIk = new UIButton(IKey.raw(rechtsText), (b) -> this.snap(mechanik, bone, prop, false, ikWert));

        nachFk.tooltip(IKey.raw("Erst schnappen, dann schalten — ohne den Snap springt das Glied um ein Bild"));
        nachIk.tooltip(IKey.raw("Erst schnappen, dann schalten — ohne den Snap springt das Glied um ein Bild"));

        return UI.row(nachFk, nachIk).marginTop(4);
    }

    private static String trim(float v)
    {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private void snap(RigMechanik mechanik, String bone, String prop, boolean nachFk, float ziel)
    {
        if (this.pose == null)
        {
            return;
        }

        mchorse.bbs_mod.cubic.ik.RigMechanik m = mechanikVon(this.entity);
        mchorse.bbs_mod.bobj.BOBJArmature arm = armaturVon(this.entity);

        if (m == null || arm == null)
        {
            return;
        }

        if (nachFk)
        {
            RigSnap.fkAnIk(arm, m, this.pose, bone, prop);
        }
        else
        {
            RigSnap.ikAnFk(arm, m, this.pose, bone, prop);
        }

        m.treiber.regler.setze(bone, prop, ziel);
        this.gebautFuer = "";
        this.aktualisiere(this.entity, this.pose);
    }

    /** Die Armature hinter dieser Entity — dieselbe Kette wie {@link #mechanikVon}. */
    public static mchorse.bbs_mod.bobj.BOBJArmature armaturVon(IEntity entity)
    {
        if (entity == null
            || !(entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            || !(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form)
                instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer))
        {
            return null;
        }

        mchorse.bbs_mod.cubic.ModelInstance instance = renderer.getModel();

        return instance != null && instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj
            ? bobj.getArmature() : null;
    }

    private UIElement schieber(RigMechanik mechanik, String bone, String prop, MapType d)
    {
        UITrackpad pad = new UITrackpad((v) ->
            mechanik.treiber.regler.setze(bone, prop, v.floatValue()));

        pad.setValue(mechanik.treiber.regler.wertVon(bone, prop, d.getFloat("wert", 0F)));

        /* Blenders Grenzen mitnehmen: ohne sie liesse sich IK_FK auf 7 stellen und
         * die Kette verschwaende in einer Lage, die es in Blender nicht gibt.
         * Die int-Regler (IK_parent, pole_parent) tragen Blenders Default-Grenzen
         * ±2^31 — die sind keine echte Grenze, also nur die Rasterung uebernehmen. */
        boolean ganz = "int".equals(d.getString("art")) || "bool".equals(d.getString("art"));

        if (ganz)
        {
            pad.integer();
        }

        if (d.has("min") && d.has("max") && Math.abs(d.getFloat("max")) < 1.0E6F)
        {
            pad.limit(d.getFloat("min"), d.getFloat("max"));
        }
        else if ("bool".equals(d.getString("art")))
        {
            pad.limit(0D, 1D);
        }

        String text = d.getString("text");

        pad.tooltip(IKey.raw(text == null || text.isEmpty() ? prop : text));

        UIElement zeile = UI.label(IKey.raw(prop)).marginTop(4).h(12);

        zeile.add(pad.w(1F));

        return zeile;
    }
}
