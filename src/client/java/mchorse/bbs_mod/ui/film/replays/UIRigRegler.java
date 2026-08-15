package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.cubic.ik.RigMechanik;
import mchorse.bbs_mod.film.replays.Replay;
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
        /* ⛔ Der gewaehlte Knochen gehoert in die Kennung: das Panel zeigt seit heute nur
         * noch die Regler SEINER Gliedmasse, und ohne ihn bliebe es bei der Auswahl
         * stehen, die beim Aufklappen gerade galt. */
        String kennung = mechanik == null ? ""
            : mechanik.hashCode() + "#" + gewaehlterKnochen();

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

        String gewaehlt = gewaehlterKnochen();
        int gezeigt = 0;

        for (String bone : mechanik.treiber.regler.knochen())
        {
            for (Map.Entry<String, MapType> e : mechanik.treiber.regler.reglerVon(bone).entrySet())
            {
                /* ⛔ Blender zeigt NICHT alle Gliedmassen untereinander: jede Zeile steht
                 * in seinem `rig_ui.py` in `if is_selected({...})`, und die Bloecke
                 * schachteln. Der IK/FK-Schieber des linken Arms erscheint nur, wenn ein
                 * Control DIESES Arms gewaehlt ist, `FK_limb_follow` zusaetzlich nur bei
                 * einem FK-Knochen. Vorher stand hier die ganze Liste mit Knochennamen als
                 * Zwischenueberschrift — lesbar, aber nicht Blender. */
                if (!sichtbar(e.getValue(), gewaehlt))
                {
                    continue;
                }

                this.add(this.schieber(mechanik, bone, e.getKey(), e.getValue()));
                gezeigt++;

                float fk = RigSnap.fkWert(mechanik, bone, e.getKey(),
                    e.getValue().getFloat("min", 0F), e.getValue().getFloat("max", 1F));

                /* ⛔ Tor ist die PAAR-Zahl, nicht fkWert. Gemessen: `eyes.eyes_follow` treibt
                 * zwar ein COPY_TRANSFORMS, hat aber 0 Paare (der getriebene Constraint steht
                 * auf nr0, es gibt keine zweite Quelle) — ein Knopf dort haette nichts
                 * geschnappt. Ein Knopf, der nichts tut, ist derselbe Betrug wie ein
                 * Schieber, der nichts bewegt. */
                /* ⛔ Und nur dort, wo BLENDER auch Knoepfe hat: die Beschriftungen kommen
                 * aus seinem `rig_ui.py`. Der Kiefer-Lock ist strukturell derselbe Bau wie
                 * FK/IK (zwei COPY_TRANSFORMS, der zweite getrieben) — wir haben ihm daraus
                 * zwei Knoepfe gebaut, die es in Blender nicht gibt ("mouth_lock = 0/1").
                 * Erfundene Knoepfe sind genau das, was der Nutzer als falsch gemeldet hat. */
                boolean blenderHatKnoepfe = !e.getValue().getString("snap_zu_fk").isEmpty()
                    && !e.getValue().getString("snap_zu_ik").isEmpty();

                if (blenderHatKnoepfe && !Float.isNaN(fk)
                    && !RigSnap.paare(mechanik, bone, e.getKey()).isEmpty())
                {
                    this.add(this.snapZeile(mechanik, bone, e.getKey(), fk));
                }
            }
        }

        if (gezeigt == 0)
        {
            this.add(this.hinweis(gewaehlt));
        }

        this.neuEinmessen();
    }

    /**
     * Was dasteht, wenn nichts zu zeigen ist. Blender laesst das Panel in diesem Fall
     * LEER (sein {@code draw} steigt ohne {@code selected_pose_bones} sofort aus) — hier
     * steht stattdessen, warum es leer ist. Ein leeres Feld sieht sonst aus wie ein
     * kaputtes Feld, und genau danach sucht man dann an der falschen Stelle.
     *
     * <p>⛔ ENGLISCH, wie jeder sichtbare Text dieses Panels: der Nutzer arbeitet mit
     * jemandem zusammen, der kein Deutsch kann, und ein Hinweis, den der Leser nicht
     * versteht, ist so nuetzlich wie ein leeres Feld. Die Kommentare bleiben deutsch —
     * sie richten sich an den, der den Code aendert, nicht an den, der ihn bedient.
     *
     * <p>(Nebenbefund vom 2026-08-15, weiter gueltig: die Schrift TRAEGT Umlaute. Im
     * ganzen Baum hat kein UI-Text einen, es war also unbelegt; eine Probe in genau
     * diesem Feld kam vollstaendig heraus, Klein, Gross und Eszett.)
     *
     * <p>⛔ KURZ bleibt es trotzdem: ob {@link UIText} bei 194 px Panelbreite sauber umbricht, konnte
     * ich nicht messen — der Render-Thread-Waechter blockt {@code resize()} aus dem
     * Messkanal, und der Zustand "gar keine Auswahl" ist im Film-Editor kaum erreichbar.
     * Beide Fassungen bleiben deshalb in der Laenge, die im Bild belegt ist (22 Zeichen,
     * eine Zeile).
     */
    private UIElement hinweis(String gewaehlt)
    {
        String text = gewaehlt.isEmpty()
            ? "Select a rig control."
            : gewaehlt + " has no rig properties.";

        return new mchorse.bbs_mod.ui.framework.elements.utils.UIText(IKey.raw(text))
            .color(Colors.A75 | Colors.LIGHTER_GRAY, false)
            .marginTop(6);
    }

    /**
     * ⛔ Nach einem Neubau muss der ROLLBEREICH mit, nicht nur dieses Element.
     *
     * <p>Seit das Panel der Auswahl folgt, aendert sich seine Hoehe stark (41 Zeilen bei
     * allen Gliedmassen, 6 bei einem Bein). Ein blosses {@code this.resize()} laesst den
     * Abschnitt darueber auf der alten Hoehe stehen und den Rollstand im Leeren: gemessen
     * standen die neuen Zeilen bei y=2298, waehrend der sichtbare Bereich bei ~300 endete —
     * der Kopf "Rig Main Properties" war da, darunter nichts.
     */
    private void neuEinmessen()
    {
        UIElement oben = this;

        for (UIElement p = this.getParent(); p != null; p = p.getParent())
        {
            oben = p;

            if (p instanceof mchorse.bbs_mod.ui.framework.elements.UIScrollView roll)
            {
                roll.resize();
                roll.scroll.clamp();

                return;
            }
        }

        oben.resize();
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
        /* ⛔ Beschriftung aus Blender, wenn sie da ist: Rigify schreibt auf diese Knoepfe
         * "IK->FK (foot.L)" und "FK->IK (foot.L)" — mit dem Glied, an dem sie haengen.
         * "-> FK" / "-> IK" war Eigenerfindung und liess nicht erkennen, ob der Knopf
         * zum Arm oder zum Bein gehoert. */
        MapType schema = mechanik.treiber.regler.reglerVon(bone).get(prop);
        String vonBlenderFk = schema == null ? null : schema.getString("snap_zu_fk");
        String vonBlenderIk = schema == null ? null : schema.getString("snap_zu_ik");

        boolean ikfk = prop.toUpperCase().contains("IK");
        String linksText = vonBlenderFk != null && !vonBlenderFk.isEmpty() ? vonBlenderFk
            : ikfk ? "-> FK" : prop + " = " + trim(fkWert);
        String rechtsText = vonBlenderIk != null && !vonBlenderIk.isEmpty() ? vonBlenderIk
            : ikfk ? "-> IK" : prop + " = " + trim(ikWert);

        UIButton nachFk = new UIButton(IKey.raw(linksText), (b) -> this.snap(mechanik, bone, prop, true, fkWert));
        UIButton nachIk = new UIButton(IKey.raw(rechtsText), (b) -> this.snap(mechanik, bone, prop, false, ikWert));

        IKey snapHilfe = IKey.raw("Snap first, then switch \u2014 without the snap the limb "
            + "jumps for one frame");

        nachFk.tooltip(snapHilfe);
        nachIk.tooltip(snapHilfe);

        /* Blenders zweite Reihe: derselbe Snap ueber ALLE Keyframes statt nur im
         * aktuellen Bild. Dort heissen beide Knoepfe schlicht "Action"; die Richtung
         * steht im Tooltip, weil sie schon ueber dem Knopf steht. */
        UIButton backeFk = new UIButton(IKey.raw("Action"),
            (b) -> this.backe(mechanik, bone, prop, true, fkWert));
        UIButton backeIk = new UIButton(IKey.raw("Action"),
            (b) -> this.backe(mechanik, bone, prop, false, ikWert));

        backeFk.tooltip(IKey.raw(linksText + " on EVERY pose keyframe \u2014 convert the whole "
            + "shot at once instead of snapping by hand at each switch"));
        backeIk.tooltip(IKey.raw(rechtsText + " on EVERY pose keyframe \u2014 convert the whole "
            + "shot at once instead of snapping by hand at each switch"));

        UIElement spalte = new UIElement();

        spalte.column(2).vertical().stretch();
        spalte.add(UI.row(nachFk, nachIk), UI.row(backeFk, backeIk));

        return spalte.marginTop(4);
    }

    private static String trim(float v)
    {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    /**
     * Blenders {@code Action}: der Snap ueber ALLE Pose-Keyframes, danach der Schalter.
     *
     * <p>Der Schalter wird EINMAL gesetzt, nicht je Keyframe — er ist ein Regler des
     * Rigs, keine Spur. Ist die ganze Animation umgerechnet, gilt die neue Betriebsart
     * fuer den ganzen Schuss, und genau das ist der Sinn.
     */
    private void backe(RigMechanik mechanik, String bone, String prop, boolean nachFk, float ziel)
    {
        if (!(mchorse.bbs_mod.BBSModClient.getDashboard().panels.panel
            instanceof mchorse.bbs_mod.ui.film.UIFilmPanel panel))
        {
            return;
        }

        RigMechanik m = mechanikVon(this.entity);
        mchorse.bbs_mod.bobj.BOBJArmature arm = armaturVon(this.entity);
        Replay replay = panel.replayEditor.getReplay();

        if (m == null || arm == null || replay == null)
        {
            return;
        }

        int getan = mchorse.bbs_mod.cubic.ik.RigBacken.backe(panel.getController().getEntities(),
            this.entity, replay, arm, m, bone, prop, nachFk);

        if (getan <= 0)
        {
            return;
        }

        m.treiber.regler.setze(bone, prop, ziel);
        this.gebautFuer = "";
        this.aktualisiere(this.entity, this.pose);
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

        /* ⛔ Mit Neuauswertung — siehe RigSnap#schnappe. Ohne sie schrieb der Knopf
         * Werte, die das naechste Control gar nicht sah. */
        mchorse.bbs_mod.forms.renderers.ModelFormRenderer ren =
            this.entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm mf2
                && mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(mf2)
                    instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer r ? r : null;
        Runnable neu = ren == null ? null : () ->
        {
            mchorse.bbs_mod.cubic.ModelInstance mi = ren.getModel();

            if (mi != null)
            {
                mi.model.resetPose();
                mi.model.applyPose(ren.getPose());
                arm.setupMatrices();
            }
        };

        if (nachFk)
        {
            RigSnap.fkAnIk(arm, m, this.pose, bone, prop, neu);
        }
        else
        {
            RigSnap.ikAnFk(arm, m, this.pose, bone, prop, neu);
        }

        m.treiber.regler.setze(bone, prop, ziel);
        this.gebautFuer = "";
        this.aktualisiere(this.entity, this.pose);
    }

    /** Die Armature hinter dieser Entity — dieselbe Kette wie {@link #mechanikVon}. */
    /** Der im Pose-Editor gewaehlte Knochen — Blenders {@code selected_pose_bones}. */
    private static String gewaehlterKnochen()
    {
        if (!(mchorse.bbs_mod.BBSModClient.getDashboard().panels.panel
            instanceof mchorse.bbs_mod.ui.film.UIFilmPanel panel))
        {
            return "";
        }

        var editor = panel.replayEditor.keyframeEditor;
        var bone = editor == null ? null : editor.getBone();

        return bone == null || bone.a == null ? "" : mchorse.bbs_mod.utils.StringUtils.fileName(bone.a);
    }

    /**
     * Blenders {@code is_selected}, geschachtelt: die Zeile erscheint, wenn in JEDEM Tor
     * mindestens einer der genannten Knochen gewaehlt ist. Ohne Tor-Angabe (aelterer
     * Export) bleibt die Zeile sichtbar — lieber zu viel zeigen als still verschwinden.
     */
    private static boolean sichtbar(MapType d, String gewaehlt)
    {
        mchorse.bbs_mod.data.types.ListType tore = d.getList("sichtbar_wenn");

        if (tore == null || tore.size() == 0)
        {
            return true;
        }

        if (gewaehlt.isEmpty())
        {
            return false;
        }

        for (int i = 0; i < tore.size(); i++)
        {
            mchorse.bbs_mod.data.types.ListType namen = tore.getList(i);
            boolean drin = false;

            for (int j = 0; namen != null && j < namen.size(); j++)
            {
                if (gewaehlt.equals(namen.getString(j)))
                {
                    drin = true;

                    break;
                }
            }

            if (!drin)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Blenders Vorgabe-Beschriftung fuer eine Property ohne eigenen {@code text}:
     * Unterstriche zu Leerzeichen, Wortanfaenge gross. {@code pole_vector} wird zu
     * "Pole Vector". Betrifft die sechs Regler, die Rigify ohne Beschriftung zeichnet
     * (pole_vector je Glied, eyes_follow, mouth_lock) — dort steht in Blender genau das.
     */
    private static String huebsch(String prop)
    {
        StringBuilder sb = new StringBuilder();

        for (String wort : prop.split("_"))
        {
            if (wort.isEmpty())
            {
                continue;
            }

            if (sb.length() > 0)
            {
                sb.append(' ');
            }

            sb.append(Character.toUpperCase(wort.charAt(0))).append(wort.substring(1));
        }

        return sb.length() == 0 ? prop : sb.toString();
    }

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

        /* ⛔ Beschriftung ist Blenders `titel`, nicht der Rohname der Property. Rigify
         * legt sie in seinem generierten `rig_ui.py` je (Knochen, Property) fest:
         * `IK_FK` heisst am Arm "IK-FK (hand.L)" und am Bein "IK-FK (foot.L)". Vorher
         * stand hier `prop` — im Panel las man "IK_FK", "FK_limb_follow", "head_follow",
         * und Arm und Bein waren nicht auseinanderzuhalten. */
        String titel = d.getString("titel");

        /* ⛔ Eine echte REIHE, kein Kind im Label. Vorher stand hier
         * `UI.label(...).add(pad.w(1F))` — ein UILabel hat keinen Layout-Manager, das
         * relative w(1F) wurde nie eingemessen, und der Schieber lag mit w=0 unter der
         * Schrift: man las "IK-FK (foot.L)" und konnte nichts einstellen (gemessen am
         * laufenden Panel: UITrackpad x=0 y=0 w=0 in JEDER Zeile). Blender setzt die
         * Beschriftung links und den Schieber rechts daneben. */
        UIElement zeile = UI.row(4);

        zeile.add(UI.label(IKey.raw(titel == null || titel.isEmpty() ? huebsch(prop) : titel)).h(16));
        zeile.add(pad);

        return zeile.marginTop(4).h(16);
    }
}
