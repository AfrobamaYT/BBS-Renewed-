package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;

import io.netty.util.collection.IntObjectMap;

/**
 * Blenders {@code Action}-Knopf: den Snap ueber ALLE Pose-Keyframes rechnen statt nur
 * im aktuellen Bild.
 *
 * <p>Ohne ihn ist FK/IK Handarbeit: an jeder Umschaltstelle schnappen, umschalten,
 * beides keyen. Rigify liefert dafuer seit Jahren die Bake-Fassung jedes Snaps —
 * woertlich aus dem Rig gelesen: {@code rigify_limb_ik2fk} "Snap the IK chain to FK
 * result" gegen {@code rigify_limb_ik2fk_bake} "Snap the IK chain KEYFRAMES to FK
 * result". Damit animiert man den ganzen Schuss in einer Betriebsart und rechnet ihn
 * hinterher in einem Griff um.
 *
 * <p>⛔ JE KEYFRAME NEU AUSWERTEN. Der Snap liest die Armature, wie sie GERADE steht.
 * Wer die Pose des Ticks nicht vorher hineinlegt und die Auswertung nicht erzwingt,
 * schnappt N-mal gegen dieselben Matrizen und schreibt in jeden Keyframe dasselbe —
 * ein Ergebnis, das plausibel aussieht und die Animation platt macht.
 *
 * <p>⛔ UND DEN BILD-CACHE JE TICK WEGWERFEN. {@code BildFrameCache} haelt die
 * Matrizen fuer die Dauer EINES Bildes fest; in einer Schleife ueber Ticks liefert er
 * sonst dem zweiten bis letzten Keyframe die Matrizen des ersten. Genau dieselbe Falle
 * wie im Control-Zeichner, dort schon einmal bezahlt.
 */
public final class RigBacken
{
    private RigBacken()
    {}

    /**
     * Schnappt an jedem Pose-Keyframe und schreibt das Ergebnis in genau diesen
     * Keyframe.
     *
     * @param nachFk true = FK-Controls auf die IK-Lage (Blenders "FK->IK"-Richtung
     *               heisst dort, dass FK das Ergebnis uebernimmt)
     * @return wie viele Keyframes geschrieben wurden, oder -1 wenn es keine Pose-Spur gibt
     */
    public static int backe(IntObjectMap<IEntity> entities, IEntity entity, Replay replay,
        BOBJArmature armature, RigMechanik mechanik, String schalter, String prop, boolean nachFk)
    {
        if (entity == null || replay == null || armature == null || mechanik == null)
        {
            return -1;
        }

        Form form = entity.getForm();
        mchorse.bbs_mod.forms.forms.ModelForm mf =
            form instanceof mchorse.bbs_mod.forms.forms.ModelForm m ? m : null;
        mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer =
            mf != null && mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(mf)
                instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer r ? r : null;
        KeyframeChannel kanal = replay.properties.properties.get("pose");

        if (form == null || mf == null || renderer == null
            || kanal == null || kanal.getKeyframes().isEmpty())
        {
            return -1;
        }

        float merker = 0F;
        int getan = 0;

        for (Object roh : kanal.getKeyframes())
        {
            Keyframe kf = (Keyframe) roh;

            if (!(kf.getValue() instanceof Pose pose))
            {
                continue;
            }

            float tick = kf.getTick();

            /* Die Pose DIESES Ticks in die Form legen und auswerten lassen — der Snap
             * liest danach die Armature, wie sie an diesem Tick steht. */
            replay.properties.applyProperties(form, tick);

            /* ⛔ Und die ARMATURE neu rechnen, nicht den Matrix-Cache. RigSnap liest
             * `armature.bones[...].mat` — dieselben Matrizen, die der Renderer beim
             * Zeichnen fuellt. `getBoneCompositeMatrix` rechnet in einen EIGENEN Cache
             * und laesst `bone.mat` stehen: der Snap haette dann N-mal gegen den Stand
             * des letzten gezeichneten Bildes geschnappt. Genau das ist beim ersten
             * Testlauf passiert — 2 Keyframes gemeldet, 0 Werte geschrieben.
             * Der Weg des Renderers ist: Ruhepose, Aktionen, Form-Pose, dann die
             * Constraints ueber setupMatrices. */
            mchorse.bbs_mod.forms.renderers.utils.BildFrameCache.anlegen();

            try
            {
                if (renderer != null)
                {
                    mchorse.bbs_mod.cubic.ModelInstance mi = renderer.getModel();

                    if (mi != null)
                    {
                        mi.model.resetPose();
                        mi.model.applyPose(renderer.getPose());
                        armature.setupMatrices();
                    }
                }

                /* ⛔ In die LEBENDE Form-Pose schnappen, nicht direkt in den Keyframe.
                 * RigSnap wertet nach JEDEM Paar neu aus, damit das naechste Kettenglied
                 * die eben gesetzte Elternlage sieht — und diese Auswertung liest die
                 * Form-Pose. Wer in die Keyframe-Pose schreibt, laesst Paar 2 und 3 gegen
                 * einen veralteten Stand rechnen. Gemessen: das Umschalten bewegte die
                 * Kette danach um 1.237 statt um 0 — schlechter als ohne Bake. */
                mchorse.bbs_mod.utils.pose.Pose lebend = mf.pose.get();

                if (nachFk)
                {
                    RigSnap.fkAnIk(armature, mechanik, lebend, schalter, prop);
                }
                else
                {
                    RigSnap.ikAnFk(armature, mechanik, lebend, schalter, prop);
                }

                /* ... und das Ergebnis in den Keyframe uebernehmen, sonst ist es beim
                 * naechsten applyProperties weg. Nur die geschnappten Knochen: alles
                 * andere gehoert dem Animator. */
                for (String[] paar : RigSnap.paare(mechanik, schalter, prop))
                {
                    String name = paar[nachFk ? 0 : 1];

                    pose.get(name).copy(lebend.get(name));
                }
            }
            finally
            {
                mchorse.bbs_mod.forms.renderers.utils.BildFrameCache.frei();
            }

            kf.setValue(kf.getValue(), true);
            merker = tick;
            getan++;
        }

        /* Die Form stand zuletzt auf dem letzten Keyframe — der Aufrufer zeichnet im
         * naechsten Bild ohnehin neu, aber ein zurueckgelassener Fremdstand hier waere
         * genau die Art stiller Nebenwirkung, die man spaeter woanders sucht. */
        replay.properties.applyProperties(form, merker);

        return getan;
    }
}
